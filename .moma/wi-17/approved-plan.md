# Approved Plan — WI-17 Visual Classifier Dataset & Training

**Approved at iteration 2 with score 10/10.**

This plan is the merged final spec: Iteration 1 baseline + Iteration 2 fixes for criteria 1 (freezing) and 5 (platform mix).

---

## Strategy

- Two existing stubs (`ml/train_visual_classifier.py`, `ml/export_visual_tflite.py`) are fully rewritten.
- New file `ml/eval/evaluate_visual.py` is created.
- Dataset scaffolding under `ml/dataset/` is created (annotations.json seed, splits.json seed, .gitignore, .gitkeep, screenshots.dvc placeholder, 7 class subdirs).
- TFLite asset at `app/src/main/assets/scrollshield_visual_classifier.tflite` is produced by running the export script in smoke mode.
- All three scripts inline an identical enum-drift check that parses `app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt` and asserts `EXPECTED_CLASSIFICATIONS` and `EXPECTED_TOPICS` match.

---

## `ml/train_visual_classifier.py` — full rewrite

### Header docstring (state explicitly)
- Purpose: Train MobileNetV3-Small dual-head visual classifier for ScrollShield Tier 1; supports `--smoke-test` for CPU end-to-end validation.
- Both `--enforce-minimums` and `--enforce-platform-mix` are unconditionally skipped under `--smoke-test`.
- "Platform-mix arithmetic": 7-entry seed at 3/2/2 = 0.4286/0.2857/0.2857 (max drift 0.029); 14-item smoke set at 6/4/4 = same ratio. Both within ±0.05 of 40/30/30.

### Imports
- stdlib: `argparse, json, math, os, random, re, sys, time`
- third-party: `numpy as np`, `torch`, `torch.nn as nn`, `torch.nn.functional as F`, `torch.optim as optim`, `from torch.utils.data import DataLoader, Dataset`, `from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights`, `from torchvision import transforms`, `from PIL import Image`, `from sklearn.metrics import f1_score`, `from sklearn.model_selection import StratifiedShuffleSplit`, `from collections import Counter`, `from pathlib import Path`

### Constants
```python
IMG_SIZE = 224
NUM_CLASSES = 7
TOPIC_DIM = 20
BATCH_SIZE = 32
LR = 1e-4
EPOCHS = 50
EARLY_STOPPING_PATIENCE = 10
LOSS_WEIGHTS = (0.7, 0.3)
FROZEN_STAGES = 10  # NOTE: top-level child modules of features, NOT individual Conv2d count

# Class minimum sizes for full dataset (15k total)
CLASS_MIN = {
    "ORGANIC": 5000, "OFFICIAL_AD": 3000, "INFLUENCER_PROMO": 2500,
    "ENGAGEMENT_BAIT": 1500, "OUTRAGE_TRIGGER": 1500,
    "EDUCATIONAL": 1000, "UNKNOWN": 500,
}
PLATFORM_MIX = {"tiktok": 0.40, "instagram": 0.30, "youtube": 0.30}
PLATFORM_MIX_TOLERANCE = 0.05  # +/- 5 percentage points

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)

# Smoke fixture deterministic platform plan: 14 items -> 6/4/4 = 0.4286/0.2857/0.2857
# (max drift 0.029 < tolerance 0.05)
SMOKE_PLATFORM_PLAN = ["tiktok"] * 6 + ["instagram"] * 4 + ["youtube"] * 4
```

### Enum mirror block + drift check
```python
EXPECTED_CLASSIFICATIONS = ["ORGANIC", "OFFICIAL_AD", "INFLUENCER_PROMO",
                            "ENGAGEMENT_BAIT", "OUTRAGE_TRIGGER",
                            "EDUCATIONAL", "UNKNOWN"]
EXPECTED_TOPICS = [
    ("COMEDY", 0), ("MUSIC", 1), ("FOOD", 2), ("SPORTS", 3),
    ("FASHION", 4), ("TECH", 5), ("EDUCATION", 6), ("GAMING", 7),
    ("FINANCE", 8), ("POLITICS", 9), ("ANIMALS", 10), ("TRAVEL", 11),
    ("ART", 12), ("NEWS", 13), ("RELATIONSHIPS", 14), ("CARS", 15),
    ("HOME", 16), ("PARENTING", 17), ("HEALTH", 18), ("NATURE", 19),
]
KOTLIN_ENUM_PATH = (Path(__file__).resolve().parent.parent
                    / "app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt")

def assert_no_enum_drift():
    """Re-parse the Kotlin enum source and verify it matches our mirror.
    Raises RuntimeError on drift. Implementer note: the Kotlin file may not
    yet exist (WI-02 may be pending); if so, print a warning and continue
    rather than fail — drift is only enforceable once WI-02 has shipped."""
    if not KOTLIN_ENUM_PATH.exists():
        print(f"[warn] {KOTLIN_ENUM_PATH} not found; skipping enum drift check")
        return
    text = KOTLIN_ENUM_PATH.read_text()
    # Match `enum class Classification { ... }` and split members
    m = re.search(r"enum class Classification\s*\{([^}]+)\}", text, re.DOTALL)
    if not m:
        raise RuntimeError("Could not find Classification enum in Kotlin source")
    members = [tok.strip().split("(")[0].strip()
               for tok in m.group(1).split(",")
               if tok.strip() and not tok.strip().startswith("//")]
    if members != EXPECTED_CLASSIFICATIONS:
        raise RuntimeError(
            f"Enum drift: Kotlin Classification = {members} "
            f"but expected {EXPECTED_CLASSIFICATIONS}")
    # Match TopicCategory entries: NAME(index, "label")
    m = re.search(r"enum class TopicCategory[^{]*\{(.+?)\}", text, re.DOTALL)
    if not m:
        raise RuntimeError("Could not find TopicCategory enum in Kotlin source")
    pairs = re.findall(r"(\w+)\s*\(\s*(\d+)", m.group(1))
    pairs = [(name, int(idx)) for name, idx in pairs]
    if pairs != EXPECTED_TOPICS:
        raise RuntimeError(
            f"Enum drift: Kotlin TopicCategory = {pairs} "
            f"but expected {EXPECTED_TOPICS}")
```

### Model
```python
class MobileNetV3SmallDualHead(nn.Module):
    """Dual-head MobileNetV3-Small for ScrollShield Tier 1 visual classification.

    WI-17 spec: "freeze first 10 convolutional layers". torchvision's
    mobilenet_v3_small.features is structured as 13 top-level child modules
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
        assert sum(1 for c in self.features.children()) == 13, \
            "MobileNetV3-Small features must have 13 stages"
        unfrozen = [i for i, c in enumerate(self.features.children())
                    if any(p.requires_grad for p in c.parameters())]
        assert unfrozen == [10, 11, 12], \
            f"Expected unfrozen stages [10,11,12], got {unfrozen}"
        # Heads
        self.cls_head = nn.Linear(576, 7)
        self.topic_head = nn.Sequential(nn.Linear(576, 20), nn.Sigmoid())

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        x = torch.flatten(x, 1)
        return self.cls_head(x), self.topic_head(x)
```

### Transforms
```python
def build_transforms(train: bool):
    if train:
        return transforms.Compose([
            transforms.RandomResizedCrop(IMG_SIZE, scale=(0.8, 1.0)),
            transforms.RandomHorizontalFlip(),
            transforms.RandomRotation(5),
            transforms.ColorJitter(brightness=0.2, contrast=0.2),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ])
    return transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(IMG_SIZE),
        transforms.ToTensor(),
        transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])
```

### Dataset class
- `ScreenshotDataset(Dataset)`: holds `[(path, class_idx, topic_vec_tensor)]` records + transform; `__getitem__` opens with PIL convert RGB.

### Loss
```python
def compute_loss(cls_logits, topic_pred, cls_target, topic_target):
    return (LOSS_WEIGHTS[0] * F.cross_entropy(cls_logits, cls_target)
            + LOSS_WEIGHTS[1] * F.mse_loss(topic_pred, topic_target))
```

### Validators
- `validate_class_minimums(annotations)`: counts per-class, raises RuntimeError on any class below `CLASS_MIN`.
- `validate_platform_mix(annotations, tolerance=PLATFORM_MIX_TOLERANCE)`: counts per-platform, asserts each is within ±tolerance of `PLATFORM_MIX[plat]`, raises RuntimeError on violation. Returns drift dict.

### Splits
- `stratified_split(records)`: composite key `f"{cls}|{plat}"`, `StratifiedShuffleSplit` 70/30 then 50/50 of the 30 → 70/15/15. Logs per-class and per-platform counts.

### Smoke fixture generator
```python
def ensure_smoke_dataset(root: Path):
    """Synthesize 14 deterministic 224x224 PNGs (2 per class) across 6/4/4
    tiktok/instagram/youtube. Writes annotations.json + splits.json with
    full WI-17 schema. Idempotent: skips if fixtures already present."""
    smoke_pairs = [(c, s) for c in EXPECTED_CLASSIFICATIONS for s in (0, 1)]  # 14 pairs
    # Assign platforms via SMOKE_PLATFORM_PLAN (length 14)
    annotations = []
    for (cls, sample_idx), plat in zip(smoke_pairs, SMOKE_PLATFORM_PLAN):
        image_id = f"smoke_{cls.lower()}_{plat}_{sample_idx:04d}"
        # ... synthesize image, write to root/screenshots/<cls>/<image_id>.png
        # ... build full-schema annotation dict
    # Write annotations.json (overwrites existing) and splits.json (8 train / 3 val / 3 test, stratified)
```

### Train function
- Calls `assert_no_enum_drift()`.
- If `args.smoke_test`: force `device='cpu'`, `epochs=1`, `batch_size=2`, `pretrained=False`, call `ensure_smoke_dataset()`.
- Skips validators under smoke; otherwise gates by `--enforce-minimums` / `--enforce-platform-mix`.
- AdamW(lr=1e-4, weight_decay=1e-4), CosineAnnealingLR(T_max=epochs), early-stop patience 10 on val macro-F1.
- Saves checkpoint to `ml/dataset/_artifacts/visual_classifier.pth` (mkdir -p first).

### CLI
- `--smoke-test`, `--epochs`, `--batch-size`, `--lr`, `--data-root` (default `ml/dataset`), `--checkpoint` (default `ml/dataset/_artifacts/visual_classifier.pth`), `--device` (auto), `--seed` (default 42), `--enforce-minimums`, `--enforce-platform-mix`.

---

## `ml/export_visual_tflite.py` — full rewrite

- Same enum mirror block + `assert_no_enum_drift()`.
- Inline duplicate `MobileNetV3SmallDualHead` with the SAME freezing logic and asserts (so checkpoint loads cleanly).
- Constants: `INPUT_SHAPE = (1, 3, 224, 224)`, `REPRESENTATIVE_COUNT = 500`, `MAX_MODEL_SIZE_BYTES = 5 * 1024 * 1024`.
- `load_model(checkpoint_path, smoke)`: reconstructs model with `pretrained=False`, loads state dict, eval().
- `export_onnx(model, onnx_path)`: `torch.onnx.export(model, dummy_uint8_or_float_input, onnx_path, input_names=["input"], output_names=["classification","topic_vector"], opset_version=13, dynamic_axes={"input":{0:"batch"}})`. `onnx.checker.check_model`.
- `onnx_to_savedmodel(onnx_path, sm_dir)`: `prepare(onnx.load(onnx_path)).export_graph(sm_dir)`.
- `build_representative_dataset(images_dir, count, smoke)`: yields NHWC float32 [0,1] tensors. Documents NCHW->NHWC transpose. Smoke=8 samples; real=500; falls back to zeros with warning if dir empty.
- `convert_to_tflite(sm_dir, tflite_path, rep_dataset_fn)`:
  ```python
  converter = tf.lite.TFLiteConverter.from_saved_model(sm_dir)
  converter.optimizations = [tf.lite.Optimize.DEFAULT]
  converter.representative_dataset = rep_dataset_fn
  converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
  converter.inference_input_type = tf.uint8
  converter.inference_output_type = tf.float32
  Path(tflite_path).parent.mkdir(parents=True, exist_ok=True)
  Path(tflite_path).write_bytes(converter.convert())
  ```
- `validate_tflite(tflite_path)`:
  - Open via `tf.lite.Interpreter`. Allocate tensors.
  - Assert input dtype `np.uint8`, shape `[1, 224, 224, 3]`.
  - Assert exactly 2 outputs; sort by last-dim and assert {7, 20} both float32.
  - Assert `os.path.getsize(tflite_path) < MAX_MODEL_SIZE_BYTES`. Print KB.
  - Run a `np.zeros((1,224,224,3), uint8)` inference, time it, print `inference_ms`.
  - Print benchmark hook block:
    ```
    # Snapdragon 7-series benchmark (target median < 60 ms):
    #   adb push <tflite> /data/local/tmp/
    #   adb shell /data/local/tmp/benchmark_model \
    #     --graph=/data/local/tmp/scrollshield_visual_classifier.tflite \
    #     --num_runs=100 --use_nnapi=true
    ```
- **Smoke fallback**: if `onnx_tf` or `tensorflow` import fails (or ONNX→TF chain fails under smoke), build a minimal `tf.keras` model with a single Conv2D + GlobalAveragePooling2D + two heads (Dense(7) softmax, Dense(20) sigmoid), input shape (224,224,3), then quantize via the same converter path with `inference_input_type=tf.uint8`, `inference_output_type=tf.float32`. Always run `validate_tflite()` afterwards.
- CLI: `--smoke-test`, `--checkpoint`, `--onnx-out` (default `ml/dataset/_artifacts/visual_classifier.onnx`), `--savedmodel-dir` (default `ml/dataset/_artifacts/visual_classifier_savedmodel`), `--tflite-out` (default `app/src/main/assets/scrollshield_visual_classifier.tflite`), `--rep-images-dir` (default `ml/dataset/screenshots`), `--rep-count`.

---

## `ml/eval/evaluate_visual.py` — new file

- Module docstring + same enum mirror + `assert_no_enum_drift()`.
- Inline duplicate `MobileNetV3SmallDualHead` with same freezing logic and asserts.
- Constants:
  ```python
  TARGET_THRESHOLDS = {
      "OFFICIAL_AD_F1": 0.85,
      "INFLUENCER_PROMO_F1": 0.75,
      "ENGAGEMENT_BAIT_F1": 0.70,
      "OVERALL_ACCURACY": 0.80,
      "TOPIC_MSE_MAX": 0.05,
  }
  ```
- `load_test_set(annotations_path, splits_path)` returns `(paths, y_true_cls, y_true_topic)`.
- `run_inference(checkpoint, paths, smoke)` runs CPU inference with val transforms; if checkpoint missing, builds fresh model and warns.
- `evaluate(...)`: sklearn `precision_recall_fscore_support(labels=range(7), zero_division=0.0)`, `confusion_matrix(labels=range(7))`, `accuracy_score`, `mean_squared_error`. Prints Markdown:
  - Per-class table (class name, P, R, F1, support).
  - Confusion matrix grid.
  - Overall accuracy + topic MSE.
  - PASS/FAIL threshold table for all 5 entries in `TARGET_THRESHOLDS`.
- CLI: `--smoke-test`, `--checkpoint`, `--annotations`, `--splits`, `--report-out` (optional JSON), `--strict`. Smoke forces `--strict` off.

---

## `ml/dataset/screenshots/`

Create:
- `ml/dataset/screenshots/.gitkeep` (zero-byte)
- `ml/dataset/screenshots/.gitignore`:
  ```
  # Don't commit raw screenshots — managed via DVC
  *.png
  *.jpg
  *.jpeg
  *.webp
  !.gitkeep
  ```
- 7 class subdirs: `ORGANIC/`, `OFFICIAL_AD/`, `INFLUENCER_PROMO/`, `ENGAGEMENT_BAIT/`, `OUTRAGE_TRIGGER/`, `EDUCATIONAL/`, `UNKNOWN/` — each with a `.gitkeep`.
- `ml/dataset/screenshots.dvc`:
  ```
  # DVC pointer placeholder — production dataset versions tracked separately.
  outs:
  - md5: 00000000000000000000000000000000
    size: 0
    nfiles: 0
    path: screenshots
  ```
- `ml/dataset/.gitignore`:
  ```
  _artifacts/
  _smoke_output/
  *.pth
  *.onnx
  *_savedmodel/
  ```

---

## `ml/dataset/annotations.json`

7-entry seed array, one per class. Platform mix: TikTok 3 / Instagram 2 / YouTube 2 (3/2/2 = 0.4286/0.2857/0.2857). All 5 ad_indicator types covered across the 7 entries (`cta_button`, `sponsored_label`, `product_card`, `discount_code`, `brand_logo`). Every entry has all WI-17 schema fields:
```json
{
  "image_id": "seed_organic_tiktok_0001",
  "source_app": "tiktok",
  "classification": "ORGANIC",
  "confidence": 1.0,
  "ad_indicators": [],
  "topic_categories": [/* 20 floats */],
  "platform_version": "tiktok-30.4.0",
  "device_resolution": "1080x2340",
  "capture_timestamp": "2026-05-08T12:00:00Z",
  "annotator_id": "seed-annotator"
}
```

---

## `ml/dataset/splits.json`

```json
{
  "train": ["seed_organic_tiktok_0001", "seed_official_ad_instagram_0001",
            "seed_influencer_promo_youtube_0001", "seed_engagement_bait_tiktok_0001"],
  "val":   ["seed_outrage_trigger_instagram_0001", "seed_educational_youtube_0001"],
  "test":  ["seed_unknown_tiktok_0001"]
}
```

The smoke-test path overwrites this with a 14-image split.

---

## `app/src/main/assets/scrollshield_visual_classifier.tflite`

Generated by `python ml/export_visual_tflite.py --smoke-test`. Must satisfy: input `uint8[1,224,224,3]`, two outputs `float32[1,7]` and `float32[1,20]`, file size < 5 MB. Asset directory created via `mkdir -p` by export script.

---

## Implementation Instructions (run after applying)

```
cd /home/devuser/ScrollShield2
python -m pip install -r ml/requirements.txt
python ml/train_visual_classifier.py --smoke-test
python ml/export_visual_tflite.py --smoke-test
python ml/eval/evaluate_visual.py --smoke-test
```

The Implementer must verify all three commands exit 0 and that the TFLite file at `app/src/main/assets/scrollshield_visual_classifier.tflite` exists and is < 5 MB after the export step.
