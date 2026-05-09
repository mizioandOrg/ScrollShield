"""Evaluate the WI-17 visual classifier on the held-out test split.

This script loads a trained dual-head MobileNetV3-Small checkpoint and reports
classification metrics (per-class precision/recall/F1, confusion matrix, overall
accuracy) and topic-vector MSE on the ``test`` split defined in
``ml/dataset/splits.json``. It then prints a PASS/FAIL gate against the WI-17
acceptance thresholds.

Modes
-----
* ``--smoke-test``: forces CPU; if a checkpoint exists at
  ``ml/dataset/_artifacts/visual_classifier.pth`` it is loaded, otherwise a
  fresh untrained model is built and a warning is printed (metrics are then
  meaningless but the end-to-end wiring is exercised). ``--strict`` is forced
  off under smoke regardless of the CLI flag.
* Default: loads the supplied checkpoint and runs the full evaluation. With
  ``--strict``, the script exits non-zero if any threshold fails.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import warnings
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from PIL import Image
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    mean_squared_error,
    precision_recall_fscore_support,
)
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

IMG_SIZE = 224
NUM_CLASSES = 7
TOPIC_DIM = 20
FROZEN_STAGES = 10

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)

TARGET_THRESHOLDS = {
    "OFFICIAL_AD_F1": 0.85,
    "INFLUENCER_PROMO_F1": 0.75,
    "ENGAGEMENT_BAIT_F1": 0.70,
    "OVERALL_ACCURACY": 0.80,
    "TOPIC_MSE_MAX": 0.05,
}


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
    Path(__file__).resolve().parent.parent.parent
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
# Model (duplicated from train_visual_classifier.py so checkpoints load cleanly)
# ---------------------------------------------------------------------------


class MobileNetV3SmallDualHead(nn.Module):
    """Dual-head MobileNetV3-Small for ScrollShield Tier 1 visual classification.

    Mirrors the training-time freezing logic so a state_dict trained with the
    training script loads here without any architectural drift.
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


def build_eval_transform():
    return transforms.Compose(
        [
            transforms.Resize(256),
            transforms.CenterCrop(IMG_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )


# ---------------------------------------------------------------------------
# Test set loading
# ---------------------------------------------------------------------------


def load_test_set(annotations_path: Path, splits_path: Path):
    """Return (paths, y_true_cls, y_true_topic) for the ``test`` split.

    ``paths`` is a list of absolute Paths to PNGs. ``y_true_cls`` is a numpy
    array of class indices, ``y_true_topic`` a numpy float32 array of shape
    (N, TOPIC_DIM).
    """
    annotations = json.loads(Path(annotations_path).read_text())
    splits = json.loads(Path(splits_path).read_text())
    test_ids = set(splits.get("test", []))
    by_id = {a["image_id"]: a for a in annotations}

    cls_to_idx = {c: i for i, c in enumerate(EXPECTED_CLASSIFICATIONS)}
    data_root = Path(annotations_path).resolve().parent

    paths, y_true_cls, y_true_topic = [], [], []
    for image_id in splits.get("test", []):
        if image_id not in by_id:
            print(f"[warn] test id {image_id} not in annotations; skipping")
            continue
        ann = by_id[image_id]
        cls = ann["classification"]
        path = data_root / "screenshots" / cls / f"{image_id}.png"
        paths.append(path)
        y_true_cls.append(cls_to_idx[cls])
        y_true_topic.append(ann["topic_categories"])
    print(f"[load] test set size = {len(paths)} (split test ids = {len(test_ids)})")
    return (
        paths,
        np.array(y_true_cls, dtype=np.int64),
        np.array(y_true_topic, dtype=np.float32),
    )


# ---------------------------------------------------------------------------
# Inference
# ---------------------------------------------------------------------------


def run_inference(checkpoint: Path, paths, smoke: bool):
    """Run CPU inference and return (y_pred_cls, y_pred_topic).

    Under ``smoke`` mode, missing checkpoints are tolerated (a fresh untrained
    model is built with a warning). Outside smoke mode a missing checkpoint
    raises ``FileNotFoundError``.
    """
    device = torch.device("cpu")
    checkpoint = Path(checkpoint)

    model = MobileNetV3SmallDualHead(pretrained=False).to(device)
    if checkpoint.exists():
        state = torch.load(checkpoint, map_location=device)
        model.load_state_dict(state)
        print(f"[infer] loaded checkpoint from {checkpoint}")
    else:
        msg = f"Checkpoint not found at {checkpoint}"
        if smoke:
            print(f"[warn] {msg}; using fresh untrained model — metrics meaningless")
        else:
            raise FileNotFoundError(msg)

    model.eval()
    transform = build_eval_transform()

    if not paths:
        return (
            np.zeros((0,), dtype=np.int64),
            np.zeros((0, TOPIC_DIM), dtype=np.float32),
        )

    y_pred_cls = []
    y_pred_topic = []
    with torch.no_grad():
        for path in paths:
            img = Image.open(path).convert("RGB")
            t = transform(img).unsqueeze(0).to(device)
            cls_logits, topic_pred = model(t)
            y_pred_cls.append(int(cls_logits.argmax(dim=1).item()))
            y_pred_topic.append(topic_pred.squeeze(0).cpu().numpy())
    return (
        np.array(y_pred_cls, dtype=np.int64),
        np.stack(y_pred_topic, axis=0).astype(np.float32),
    )


# ---------------------------------------------------------------------------
# Evaluation
# ---------------------------------------------------------------------------


def evaluate(y_true_cls, y_pred_cls, y_true_topic, y_pred_topic):
    """Compute metrics and print a Markdown report. Returns a dict suitable
    for JSON serialisation."""

    if len(y_true_cls) == 0:
        print("\n# Visual Classifier Evaluation Report\n")
        print("**Test set is empty — nothing to evaluate.**\n")
        return {
            "per_class": [],
            "confusion_matrix": [],
            "overall_accuracy": 0.0,
            "topic_mse": 0.0,
            "thresholds": {k: {"target": v, "actual": None, "pass": False}
                           for k, v in TARGET_THRESHOLDS.items()},
            "all_pass": False,
            "n_test": 0,
        }

    labels = list(range(NUM_CLASSES))
    precision, recall, f1, support = precision_recall_fscore_support(
        y_true_cls, y_pred_cls, labels=labels, zero_division=0.0
    )
    cm = confusion_matrix(y_true_cls, y_pred_cls, labels=labels)
    overall_acc = float(accuracy_score(y_true_cls, y_pred_cls))
    topic_mse = float(mean_squared_error(y_true_topic, y_pred_topic))

    cls_to_f1 = {EXPECTED_CLASSIFICATIONS[i]: float(f1[i]) for i in labels}
    threshold_results = {
        "OFFICIAL_AD_F1": cls_to_f1["OFFICIAL_AD"],
        "INFLUENCER_PROMO_F1": cls_to_f1["INFLUENCER_PROMO"],
        "ENGAGEMENT_BAIT_F1": cls_to_f1["ENGAGEMENT_BAIT"],
        "OVERALL_ACCURACY": overall_acc,
        "TOPIC_MSE_MAX": topic_mse,
    }

    threshold_table = {}
    all_pass = True
    for key, target in TARGET_THRESHOLDS.items():
        actual = threshold_results[key]
        if key == "TOPIC_MSE_MAX":
            passed = actual <= target
        else:
            passed = actual >= target
        all_pass = all_pass and passed
        threshold_table[key] = {
            "target": float(target),
            "actual": float(actual),
            "pass": bool(passed),
        }

    # ------- Markdown report -------
    print("\n# Visual Classifier Evaluation Report\n")
    print(f"Test set size: **{len(y_true_cls)}**\n")

    print("## Per-class metrics\n")
    print("| Class | Precision | Recall | F1 | Support |")
    print("|---|---:|---:|---:|---:|")
    for i, cls in enumerate(EXPECTED_CLASSIFICATIONS):
        print(
            f"| {cls} | {precision[i]:.4f} | {recall[i]:.4f} | "
            f"{f1[i]:.4f} | {int(support[i])} |"
        )

    print("\n## Confusion matrix\n")
    header = "| true \\ pred | " + " | ".join(EXPECTED_CLASSIFICATIONS) + " |"
    sep = "|---|" + "|".join(["---:"] * NUM_CLASSES) + "|"
    print(header)
    print(sep)
    for i, cls in enumerate(EXPECTED_CLASSIFICATIONS):
        row = " | ".join(str(int(cm[i, j])) for j in labels)
        print(f"| {cls} | {row} |")

    print(f"\n## Overall metrics\n")
    print(f"- Overall accuracy: **{overall_acc:.4f}**")
    print(f"- Topic vector MSE: **{topic_mse:.4f}**")

    print("\n## Threshold gate\n")
    print("| Threshold | Target | Actual | Result |")
    print("|---|---:|---:|:---:|")
    for key, info in threshold_table.items():
        marker = "PASS" if info["pass"] else "FAIL"
        op = "<=" if key == "TOPIC_MSE_MAX" else ">="
        print(
            f"| {key} | {op} {info['target']:.4f} | {info['actual']:.4f} | {marker} |"
        )
    print(f"\n**Overall gate: {'PASS' if all_pass else 'FAIL'}**\n")

    return {
        "per_class": [
            {
                "class": EXPECTED_CLASSIFICATIONS[i],
                "precision": float(precision[i]),
                "recall": float(recall[i]),
                "f1": float(f1[i]),
                "support": int(support[i]),
            }
            for i in labels
        ],
        "confusion_matrix": cm.astype(int).tolist(),
        "overall_accuracy": overall_acc,
        "topic_mse": topic_mse,
        "thresholds": threshold_table,
        "all_pass": bool(all_pass),
        "n_test": int(len(y_true_cls)),
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    p.add_argument("--smoke-test", action="store_true")
    p.add_argument(
        "--checkpoint",
        type=str,
        default=str(
            Path(__file__).resolve().parent.parent
            / "dataset/_artifacts/visual_classifier.pth"
        ),
    )
    p.add_argument(
        "--annotations",
        type=str,
        default=str(
            Path(__file__).resolve().parent.parent / "dataset/annotations.json"
        ),
    )
    p.add_argument(
        "--splits",
        type=str,
        default=str(Path(__file__).resolve().parent.parent / "dataset/splits.json"),
    )
    p.add_argument(
        "--report-out",
        type=str,
        default=None,
        help="Optional path to write the JSON evaluation report.",
    )
    p.add_argument(
        "--strict",
        action="store_true",
        help="Exit non-zero if any threshold fails. Forced off under --smoke-test.",
    )
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    assert_no_enum_drift()

    if args.smoke_test and args.strict:
        print("[smoke] forcing --strict OFF (smoke metrics are meaningless)")
        args.strict = False

    annotations_path = Path(args.annotations).resolve()
    splits_path = Path(args.splits).resolve()
    checkpoint_path = Path(args.checkpoint).resolve()

    if args.smoke_test:
        print(
            f"[smoke] device=cpu checkpoint={checkpoint_path} "
            f"(exists={checkpoint_path.exists()})"
        )

    paths, y_true_cls, y_true_topic = load_test_set(annotations_path, splits_path)
    y_pred_cls, y_pred_topic = run_inference(checkpoint_path, paths, smoke=args.smoke_test)

    if y_true_topic.shape != y_pred_topic.shape and len(y_true_topic) > 0:
        warnings.warn(
            f"topic shape mismatch: true={y_true_topic.shape} pred={y_pred_topic.shape}"
        )

    report = evaluate(y_true_cls, y_pred_cls, y_true_topic, y_pred_topic)

    if args.report_out:
        out_path = Path(args.report_out).resolve()
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(report, indent=2))
        print(f"[report] wrote JSON report to {out_path}")

    if args.strict and not report["all_pass"]:
        print("[strict] threshold gate failed; exiting 1")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
