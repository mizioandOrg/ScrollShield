"""Train MobileNetV3-Small dual-head visual classifier for ScrollShield Tier 1.

This script trains the WI-17 visual classifier (Classification head with 7 logits +
TopicCategory head with 20 sigmoid outputs) on screenshot data annotated under
``ml/dataset/``.

Modes
-----
* ``--smoke-test``: synthesise a tiny 14-image fixture, run a single CPU epoch with
  an unpretrained backbone, and emit a checkpoint at
  ``ml/dataset/_artifacts/visual_classifier.pth``. Used by CI to validate the
  end-to-end script wiring.

  Both ``--enforce-minimums`` and ``--enforce-platform-mix`` are unconditionally
  skipped when ``--smoke-test`` is set — the seed/smoke fixtures are too small to
  satisfy the 15 k full-dataset minimums.

* Default (real training): expects an annotated dataset under ``--data-root``;
  uses ImageNet-pretrained weights, AdamW + CosineAnnealingLR, early-stopping on
  val macro-F1, and writes the same checkpoint path.

Platform-mix arithmetic
-----------------------
The seed annotations.json has 7 entries split 3/2/2 across tiktok/instagram/youtube,
which is 0.4286 / 0.2857 / 0.2857 — a max drift of 0.029 vs the 40/30/30 target,
inside the ±0.05 tolerance. The smoke fixture is 14 entries split 6/4/4 — the same
0.4286 / 0.2857 / 0.2857 ratio, also within tolerance. ``validate_platform_mix``
enforces this when ``--enforce-platform-mix`` is set.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import re
import sys
import time
from collections import Counter
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from PIL import Image
from sklearn.metrics import f1_score
from sklearn.model_selection import StratifiedShuffleSplit
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

IMG_SIZE = 224
NUM_CLASSES = 7
TOPIC_DIM = 20
BATCH_SIZE = 32
LR = 1e-4
EPOCHS = 50
EARLY_STOPPING_PATIENCE = 10
LOSS_WEIGHTS = (0.7, 0.3)
FROZEN_STAGES = 10  # NOTE: top-level child modules of features, NOT individual Conv2d count

# Class minimum sizes for the full 15k dataset
CLASS_MIN = {
    "ORGANIC": 5000,
    "OFFICIAL_AD": 3000,
    "INFLUENCER_PROMO": 2500,
    "ENGAGEMENT_BAIT": 1500,
    "OUTRAGE_TRIGGER": 1500,
    "EDUCATIONAL": 1000,
    "UNKNOWN": 500,
}
PLATFORM_MIX = {"tiktok": 0.40, "instagram": 0.30, "youtube": 0.30}
PLATFORM_MIX_TOLERANCE = 0.05  # +/- 5 percentage points

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)

# Smoke fixture deterministic platform plan: 14 items -> 6/4/4 = 0.4286/0.2857/0.2857
SMOKE_PLATFORM_PLAN = ["tiktok"] * 6 + ["instagram"] * 4 + ["youtube"] * 4


# ---------------------------------------------------------------------------
# Enum mirror & drift check
# ---------------------------------------------------------------------------

EXPECTED_CLASSIFICATIONS = [
    "ORGANIC",
    "OFFICIAL_AD",
    "INFLUENCER_PROMO",
    "ENGAGEMENT_BAIT",
    "OUTRAGE_TRIGGER",
    "EDUCATIONAL",
    "UNKNOWN",
]
EXPECTED_TOPICS = [
    ("COMEDY", 0), ("MUSIC", 1), ("FOOD", 2), ("SPORTS", 3),
    ("FASHION", 4), ("TECH", 5), ("EDUCATION", 6), ("GAMING", 7),
    ("FINANCE", 8), ("POLITICS", 9), ("ANIMALS", 10), ("TRAVEL", 11),
    ("ART", 12), ("NEWS", 13), ("RELATIONSHIPS", 14), ("CARS", 15),
    ("HOME", 16), ("PARENTING", 17), ("HEALTH", 18), ("NATURE", 19),
]
KOTLIN_ENUM_PATH = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt"
)


def assert_no_enum_drift() -> None:
    """Re-parse the Kotlin enum source and verify it matches our mirror.

    Raises ``RuntimeError`` on drift. The Kotlin file may not yet exist
    (WI-02 may be pending); in that case we print a warning and continue
    rather than fail — drift is only enforceable once WI-02 has shipped.
    """
    if not KOTLIN_ENUM_PATH.exists():
        print(f"[warn] {KOTLIN_ENUM_PATH} not found; skipping enum drift check")
        return
    text = KOTLIN_ENUM_PATH.read_text()

    m = re.search(r"enum class Classification\s*\{([^}]+)\}", text, re.DOTALL)
    if not m:
        raise RuntimeError("Could not find Classification enum in Kotlin source")
    body = m.group(1)
    # Strip comments & whitespace, split tokens
    body_clean = re.sub(r"//[^\n]*", "", body)
    members = [
        tok.strip().split("(")[0].strip()
        for tok in body_clean.split(",")
        if tok.strip()
    ]
    if members != EXPECTED_CLASSIFICATIONS:
        raise RuntimeError(
            f"Enum drift: Kotlin Classification = {members} "
            f"but expected {EXPECTED_CLASSIFICATIONS}"
        )

    m = re.search(r"enum class TopicCategory[^{]*\{(.+?)\}", text, re.DOTALL)
    if not m:
        raise RuntimeError("Could not find TopicCategory enum in Kotlin source")
    pairs = re.findall(r"(\w+)\s*\(\s*(\d+)", m.group(1))
    pairs = [(name, int(idx)) for name, idx in pairs]
    if pairs != EXPECTED_TOPICS:
        raise RuntimeError(
            f"Enum drift: Kotlin TopicCategory = {pairs} "
            f"but expected {EXPECTED_TOPICS}"
        )


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------


class MobileNetV3SmallDualHead(nn.Module):
    """Dual-head MobileNetV3-Small for ScrollShield Tier 1 visual classification.

    WI-17 spec: "freeze first 10 convolutional layers". torchvision's
    ``mobilenet_v3_small.features`` is structured as 13 top-level child modules
    (indices 0..12): index 0 is Conv2dNormActivation, indices 1..11 are
    InvertedResidual blocks, index 12 is the final Conv2dNormActivation.
    We interpret "first 10 layers" as the first 10 top-level child modules
    (features[0..9]) — the canonical MobileNetV3 stage boundary. This avoids
    partially freezing internal Conv2d sublayers within an InvertedResidual.
    """

    def __init__(self, pretrained: bool = True):
        super().__init__()
        weights = MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
        backbone = mobilenet_v3_small(weights=weights)
        self.features = backbone.features
        self.avgpool = backbone.avgpool

        for idx, child in enumerate(self.features.children()):
            if idx < FROZEN_STAGES:
                for p in child.parameters():
                    p.requires_grad = False

        assert sum(1 for c in self.features.children()) == 13, (
            "MobileNetV3-Small features must have 13 stages"
        )
        unfrozen = [
            i
            for i, c in enumerate(self.features.children())
            if any(p.requires_grad for p in c.parameters())
        ]
        assert unfrozen == [10, 11, 12], (
            f"Expected unfrozen stages [10,11,12], got {unfrozen}"
        )

        self.cls_head = nn.Linear(576, NUM_CLASSES)
        self.topic_head = nn.Sequential(nn.Linear(576, TOPIC_DIM), nn.Sigmoid())

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        x = torch.flatten(x, 1)
        return self.cls_head(x), self.topic_head(x)


# ---------------------------------------------------------------------------
# Transforms
# ---------------------------------------------------------------------------


def build_transforms(train: bool):
    if train:
        return transforms.Compose(
            [
                transforms.RandomResizedCrop(IMG_SIZE, scale=(0.8, 1.0)),
                transforms.RandomHorizontalFlip(),
                transforms.RandomRotation(5),
                transforms.ColorJitter(brightness=0.2, contrast=0.2),
                transforms.ToTensor(),
                transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
            ]
        )
    return transforms.Compose(
        [
            transforms.Resize(256),
            transforms.CenterCrop(IMG_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )


# ---------------------------------------------------------------------------
# Dataset
# ---------------------------------------------------------------------------


class ScreenshotDataset(Dataset):
    def __init__(self, records, transform):
        # records: list of (path: Path, class_idx: int, topic_vec: torch.Tensor)
        self.records = records
        self.transform = transform

    def __len__(self):
        return len(self.records)

    def __getitem__(self, idx):
        path, cls_idx, topic_vec = self.records[idx]
        img = Image.open(path).convert("RGB")
        img = self.transform(img)
        return img, cls_idx, topic_vec


# ---------------------------------------------------------------------------
# Loss
# ---------------------------------------------------------------------------


def compute_loss(cls_logits, topic_pred, cls_target, topic_target):
    return (
        LOSS_WEIGHTS[0] * F.cross_entropy(cls_logits, cls_target)
        + LOSS_WEIGHTS[1] * F.mse_loss(topic_pred, topic_target)
    )


# ---------------------------------------------------------------------------
# Validators
# ---------------------------------------------------------------------------


def validate_class_minimums(annotations):
    counts = Counter(a["classification"] for a in annotations)
    violations = []
    for cls, minimum in CLASS_MIN.items():
        if counts.get(cls, 0) < minimum:
            violations.append(f"{cls}: {counts.get(cls, 0)} < {minimum}")
    if violations:
        raise RuntimeError(
            "Class minimums not satisfied:\n  " + "\n  ".join(violations)
        )
    return counts


def validate_platform_mix(annotations, tolerance: float = PLATFORM_MIX_TOLERANCE):
    counts = Counter(a["source_app"] for a in annotations)
    total = sum(counts.values())
    if total == 0:
        raise RuntimeError("No annotations to validate platform mix against")
    drift = {}
    violations = []
    for plat, target in PLATFORM_MIX.items():
        actual = counts.get(plat, 0) / total
        drift[plat] = actual - target
        if abs(actual - target) > tolerance:
            violations.append(
                f"{plat}: actual={actual:.3f} target={target:.2f} drift={actual - target:+.3f}"
            )
    if violations:
        raise RuntimeError(
            f"Platform mix outside ±{tolerance:.2f} tolerance:\n  "
            + "\n  ".join(violations)
        )
    return drift


# ---------------------------------------------------------------------------
# Splits
# ---------------------------------------------------------------------------


def stratified_split(records, seed: int = 42):
    """Split records 70/15/15 stratified on (classification, platform).

    ``records`` is a list of dicts containing at least ``classification`` and
    ``source_app``. Returns (train_idx, val_idx, test_idx).
    """
    if len(records) < 3:
        # Degenerate case (smoke / seed), fall back to round-robin so each split is non-empty.
        idx = list(range(len(records)))
        random.Random(seed).shuffle(idx)
        n = len(idx)
        n_test = max(1, n // 7)
        n_val = max(1, n // 7)
        n_train = n - n_test - n_val
        return idx[:n_train], idx[n_train : n_train + n_val], idx[n_train + n_val :]

    keys = [f"{r['classification']}|{r['source_app']}" for r in records]
    key_counts = Counter(keys)
    # If any stratum has only 1 sample, StratifiedShuffleSplit fails — fall back.
    if min(key_counts.values()) < 2:
        idx = list(range(len(records)))
        random.Random(seed).shuffle(idx)
        n = len(idx)
        n_test = max(1, n // 7)
        n_val = max(1, n // 7)
        n_train = n - n_test - n_val
        train_idx = idx[:n_train]
        val_idx = idx[n_train : n_train + n_val]
        test_idx = idx[n_train + n_val :]
    else:
        sss1 = StratifiedShuffleSplit(n_splits=1, test_size=0.30, random_state=seed)
        all_idx = np.arange(len(records))
        train_idx, holdout_idx = next(sss1.split(all_idx, keys))
        holdout_keys = [keys[i] for i in holdout_idx]
        sss2 = StratifiedShuffleSplit(n_splits=1, test_size=0.50, random_state=seed)
        rel_val, rel_test = next(sss2.split(holdout_idx, holdout_keys))
        val_idx = holdout_idx[rel_val]
        test_idx = holdout_idx[rel_test]
        train_idx = list(train_idx)
        val_idx = list(val_idx)
        test_idx = list(test_idx)

    def _summary(label, indices):
        cls_c = Counter(records[i]["classification"] for i in indices)
        plat_c = Counter(records[i]["source_app"] for i in indices)
        print(f"[split:{label}] n={len(indices)} cls={dict(cls_c)} plat={dict(plat_c)}")

    _summary("train", train_idx)
    _summary("val", val_idx)
    _summary("test", test_idx)
    return train_idx, val_idx, test_idx


# ---------------------------------------------------------------------------
# Smoke fixture generator
# ---------------------------------------------------------------------------


def _synth_image(seed_int: int, path: Path) -> None:
    """Synthesise a deterministic 224x224 RGB PNG for a given seed."""
    rng = np.random.RandomState(seed_int)
    arr = rng.randint(0, 256, size=(IMG_SIZE, IMG_SIZE, 3), dtype=np.uint8)
    Image.fromarray(arr, mode="RGB").save(path)


def ensure_smoke_dataset(root: Path):
    """Synthesise 14 deterministic 224x224 PNGs (2 per class) across 6/4/4
    tiktok/instagram/youtube. Writes annotations.json + splits.json with the full
    WI-17 schema. Idempotent: re-runs overwrite annotations + splits and only
    re-synthesise images that are missing.
    """
    screenshots_root = root / "screenshots"
    screenshots_root.mkdir(parents=True, exist_ok=True)
    for cls in EXPECTED_CLASSIFICATIONS:
        (screenshots_root / cls).mkdir(parents=True, exist_ok=True)

    smoke_pairs = [(c, s) for c in EXPECTED_CLASSIFICATIONS for s in (0, 1)]  # 14 pairs
    annotations = []
    for (cls, sample_idx), plat in zip(smoke_pairs, SMOKE_PLATFORM_PLAN):
        image_id = f"smoke_{cls.lower()}_{plat}_{sample_idx:04d}"
        rel_path = screenshots_root / cls / f"{image_id}.png"
        if not rel_path.exists():
            seed_int = abs(hash(image_id)) % (2**31)
            _synth_image(seed_int, rel_path)
        # Topic vector: deterministic dominant entry per class
        topic_vec = [0.0] * TOPIC_DIM
        cls_to_topic = {
            "ORGANIC": 0,            # COMEDY
            "OFFICIAL_AD": 4,        # FASHION
            "INFLUENCER_PROMO": 4,   # FASHION
            "ENGAGEMENT_BAIT": 14,   # RELATIONSHIPS
            "OUTRAGE_TRIGGER": 9,    # POLITICS
            "EDUCATIONAL": 6,        # EDUCATION
            "UNKNOWN": 19,           # NATURE (low-confidence catch-all)
        }
        topic_vec[cls_to_topic[cls]] = 0.9
        ad_indicators_map = {
            "ORGANIC": [],
            "OFFICIAL_AD": ["sponsored_label", "cta_button"],
            "INFLUENCER_PROMO": ["product_card", "discount_code"],
            "ENGAGEMENT_BAIT": ["brand_logo"],
            "OUTRAGE_TRIGGER": [],
            "EDUCATIONAL": [],
            "UNKNOWN": [],
        }
        platform_versions = {
            "tiktok": "tiktok-30.4.0",
            "instagram": "instagram-310.0.0",
            "youtube": "youtube-19.16.36",
        }
        annotations.append(
            {
                "image_id": image_id,
                "source_app": plat,
                "classification": cls,
                "confidence": 1.0,
                "ad_indicators": ad_indicators_map[cls],
                "topic_categories": topic_vec,
                "platform_version": platform_versions[plat],
                "device_resolution": "1080x2340",
                "capture_timestamp": "2026-05-08T12:00:00Z",
                "annotator_id": "smoke-fixture",
            }
        )

    (root / "annotations.json").write_text(json.dumps(annotations, indent=2))

    # 14 -> 8 train / 3 val / 3 test, deterministic round-robin per class.
    train, val, test = [], [], []
    for i, ann in enumerate(annotations):
        bucket = i % 7
        # 4 train, 1 val, 1 test per class -> but we have 2 per class. Use 8/3/3 overall.
        # Simple deterministic assignment: first sample (idx 0) -> train; second (idx 1) -> val/test based on class index.
        pass
    # Deterministic assignment: per-class, sample 0 -> train; sample 1 -> val (first 3 classes)/test (last 4 classes).
    # That gives 7 train + 3 val + 4 test (10/2/2 ratio close enough for smoke).
    for i, ann in enumerate(annotations):
        cls_idx = i // 2
        sample_idx = i % 2
        if sample_idx == 0:
            train.append(ann["image_id"])
        else:
            if cls_idx < 4:
                val.append(ann["image_id"])
            else:
                test.append(ann["image_id"])
    splits = {"train": train, "val": val, "test": test}
    (root / "splits.json").write_text(json.dumps(splits, indent=2))
    print(
        f"[smoke] wrote {len(annotations)} annotations, "
        f"split train={len(train)} val={len(val)} test={len(test)}"
    )
    return annotations, splits


# ---------------------------------------------------------------------------
# Data loading helpers
# ---------------------------------------------------------------------------


def load_records(data_root: Path):
    annotations = json.loads((data_root / "annotations.json").read_text())
    splits = json.loads((data_root / "splits.json").read_text())
    return annotations, splits


def annotations_to_records(annotations, data_root: Path):
    cls_to_idx = {c: i for i, c in enumerate(EXPECTED_CLASSIFICATIONS)}
    records = []
    for ann in annotations:
        cls = ann["classification"]
        cls_idx = cls_to_idx[cls]
        topic_vec = torch.tensor(ann["topic_categories"], dtype=torch.float32)
        path = data_root / "screenshots" / cls / f"{ann['image_id']}.png"
        records.append((path, cls_idx, topic_vec, ann))
    return records


# ---------------------------------------------------------------------------
# Training
# ---------------------------------------------------------------------------


def set_seed(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def train(args):
    assert_no_enum_drift()
    set_seed(args.seed)

    data_root = Path(args.data_root).resolve()
    if args.smoke_test:
        device = torch.device("cpu")
        epochs = 1
        batch_size = 2
        pretrained = False
        ensure_smoke_dataset(data_root)
        print("[smoke] forced device=cpu epochs=1 batch_size=2 pretrained=False")
    else:
        device = (
            torch.device(args.device)
            if args.device != "auto"
            else torch.device("cuda" if torch.cuda.is_available() else "cpu")
        )
        epochs = args.epochs
        batch_size = args.batch_size
        pretrained = True

    annotations, splits = load_records(data_root)

    if args.smoke_test:
        print("[smoke] skipping --enforce-minimums and --enforce-platform-mix")
    else:
        if args.enforce_minimums:
            counts = validate_class_minimums(annotations)
            print(f"[validate] class counts OK: {dict(counts)}")
        if args.enforce_platform_mix:
            drift = validate_platform_mix(annotations)
            print(f"[validate] platform mix drift: {drift}")

    records = annotations_to_records(annotations, data_root)
    by_id = {ann["image_id"]: rec for ann, rec in zip(annotations, records)}

    def _records_for(split_name):
        return [by_id[i] for i in splits[split_name] if i in by_id]

    train_records = _records_for("train")
    val_records = _records_for("val")
    test_records = _records_for("test")
    print(
        f"[data] train={len(train_records)} val={len(val_records)} test={len(test_records)}"
    )

    train_ds = ScreenshotDataset(
        [(p, c, t) for (p, c, t, _ann) in train_records],
        transform=build_transforms(train=True),
    )
    val_ds = ScreenshotDataset(
        [(p, c, t) for (p, c, t, _ann) in val_records],
        transform=build_transforms(train=False),
    )

    train_loader = DataLoader(
        train_ds,
        batch_size=batch_size,
        shuffle=True,
        num_workers=0,
        drop_last=False,
    )
    val_loader = DataLoader(
        val_ds, batch_size=batch_size, shuffle=False, num_workers=0
    )

    model = MobileNetV3SmallDualHead(pretrained=pretrained).to(device)
    optimizer = optim.AdamW(
        [p for p in model.parameters() if p.requires_grad],
        lr=args.lr,
        weight_decay=1e-4,
    )
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=max(1, epochs))

    best_val_f1 = -math.inf
    epochs_no_improve = 0
    checkpoint_path = Path(args.checkpoint).resolve()
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)

    for epoch in range(epochs):
        model.train()
        train_loss = 0.0
        n_batches = 0
        for imgs, cls_t, topic_t in train_loader:
            imgs = imgs.to(device)
            cls_t = cls_t.to(device)
            topic_t = topic_t.to(device)
            optimizer.zero_grad()
            cls_logits, topic_pred = model(imgs)
            loss = compute_loss(cls_logits, topic_pred, cls_t, topic_t)
            loss.backward()
            optimizer.step()
            train_loss += float(loss.item())
            n_batches += 1
        scheduler.step()
        train_loss = train_loss / max(1, n_batches)

        # Validation
        val_f1 = _eval_macro_f1(model, val_loader, device) if len(val_ds) > 0 else 0.0
        print(
            f"[epoch {epoch + 1}/{epochs}] train_loss={train_loss:.4f} val_macro_f1={val_f1:.4f}"
        )

        if val_f1 > best_val_f1:
            best_val_f1 = val_f1
            epochs_no_improve = 0
            torch.save(model.state_dict(), checkpoint_path)
            print(f"[ckpt] saved best to {checkpoint_path}")
        else:
            epochs_no_improve += 1
            if epochs_no_improve >= EARLY_STOPPING_PATIENCE and not args.smoke_test:
                print(f"[early-stop] no improvement for {EARLY_STOPPING_PATIENCE} epochs")
                break

    # Always ensure a checkpoint exists at the end (smoke may have val_f1 == 0)
    if not checkpoint_path.exists():
        torch.save(model.state_dict(), checkpoint_path)
        print(f"[ckpt] forced save to {checkpoint_path}")

    print(f"[done] best_val_macro_f1={best_val_f1:.4f}")
    return 0


def _eval_macro_f1(model, loader, device):
    model.eval()
    y_true, y_pred = [], []
    with torch.no_grad():
        for imgs, cls_t, _topic_t in loader:
            imgs = imgs.to(device)
            cls_logits, _ = model(imgs)
            preds = cls_logits.argmax(dim=1).cpu().numpy().tolist()
            y_pred.extend(preds)
            y_true.extend(cls_t.numpy().tolist())
    if not y_true:
        return 0.0
    return float(
        f1_score(
            y_true,
            y_pred,
            labels=list(range(NUM_CLASSES)),
            average="macro",
            zero_division=0.0,
        )
    )


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    p.add_argument("--smoke-test", action="store_true")
    p.add_argument("--epochs", type=int, default=EPOCHS)
    p.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    p.add_argument("--lr", type=float, default=LR)
    p.add_argument(
        "--data-root",
        type=str,
        default=str(Path(__file__).resolve().parent / "dataset"),
    )
    p.add_argument(
        "--checkpoint",
        type=str,
        default=str(
            Path(__file__).resolve().parent / "dataset/_artifacts/visual_classifier.pth"
        ),
    )
    p.add_argument("--device", type=str, default="auto")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--enforce-minimums", action="store_true")
    p.add_argument("--enforce-platform-mix", action="store_true")
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    return train(args)


if __name__ == "__main__":
    sys.exit(main())
