"""Export ScrollShield Tier-1 visual classifier from PyTorch to TFLite.

Pipeline (full)::

    PyTorch (.pth) -> ONNX (.onnx) -> TensorFlow SavedModel -> TFLite (.tflite, INT8)

Pipeline (smoke fallback)::

    Tiny tf.keras model -> TFLite (INT8) — used when ``onnx_tf`` / ``tensorflow``
    or the full chain is unavailable (CI sandbox without the full ML stack).

Both paths emit a quantised TFLite with input ``uint8[1,224,224,3]`` and outputs
``float32[1,7]`` (classification logits) and ``float32[1,20]`` (topic vector).
The asset must be < 5 MB.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import traceback
from pathlib import Path

import numpy as np


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

INPUT_SHAPE = (1, 3, 224, 224)
IMG_SIZE = 224
NUM_CLASSES = 7
TOPIC_DIM = 20
FROZEN_STAGES = 10
REPRESENTATIVE_COUNT = 500
MAX_MODEL_SIZE_BYTES = 5 * 1024 * 1024


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
    if not KOTLIN_ENUM_PATH.exists():
        print(f"[warn] {KOTLIN_ENUM_PATH} not found; skipping enum drift check")
        return
    text = KOTLIN_ENUM_PATH.read_text()
    m = re.search(r"enum class Classification\s*\{([^}]+)\}", text, re.DOTALL)
    if not m:
        raise RuntimeError("Could not find Classification enum in Kotlin source")
    body_clean = re.sub(r"//[^\n]*", "", m.group(1))
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
    pairs = [(name, int(idx)) for name, idx in re.findall(r"(\w+)\s*\(\s*(\d+)", m.group(1))]
    if pairs != EXPECTED_TOPICS:
        raise RuntimeError(
            f"Enum drift: Kotlin TopicCategory = {pairs} but expected {EXPECTED_TOPICS}"
        )


# ---------------------------------------------------------------------------
# Model (inline duplicate so checkpoint can be loaded standalone)
# ---------------------------------------------------------------------------


def _build_pytorch_model(pretrained: bool = False):
    import torch.nn as nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

    class MobileNetV3SmallDualHead(nn.Module):
        def __init__(self, pretrained: bool = False):
            super().__init__()
            weights = (
                MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
            )
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
            self.topic_head = nn.Sequential(
                nn.Linear(576, TOPIC_DIM), nn.Sigmoid()
            )

        def forward(self, x):
            import torch
            x = self.features(x)
            x = self.avgpool(x)
            x = torch.flatten(x, 1)
            return self.cls_head(x), self.topic_head(x)

    return MobileNetV3SmallDualHead(pretrained=pretrained)


# ---------------------------------------------------------------------------
# Stage 1 — load PyTorch checkpoint
# ---------------------------------------------------------------------------


def load_model(checkpoint_path: Path, smoke: bool):
    import torch

    model = _build_pytorch_model(pretrained=False)
    if checkpoint_path.exists():
        state_dict = torch.load(
            str(checkpoint_path), map_location="cpu", weights_only=True
        )
        model.load_state_dict(state_dict)
        print(f"[load] loaded checkpoint from {checkpoint_path}")
    else:
        msg = f"[warn] checkpoint not found at {checkpoint_path}; using random init"
        if not smoke:
            raise RuntimeError(msg.replace("[warn] ", ""))
        print(msg)
    model.eval()
    return model


# ---------------------------------------------------------------------------
# Stage 2 — PyTorch -> ONNX
# ---------------------------------------------------------------------------


def export_onnx(model, onnx_path: Path) -> None:
    import torch

    onnx_path.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.randn(*INPUT_SHAPE)
    torch.onnx.export(
        model,
        dummy,
        str(onnx_path),
        input_names=["input"],
        output_names=["classification", "topic_vector"],
        opset_version=13,
        dynamic_axes={"input": {0: "batch"}},
    )
    import onnx

    onnx.checker.check_model(str(onnx_path))
    print(f"[onnx] exported to {onnx_path}")


# ---------------------------------------------------------------------------
# Stage 3 — ONNX -> SavedModel
# ---------------------------------------------------------------------------


def onnx_to_savedmodel(onnx_path: Path, sm_dir: Path) -> None:
    from onnx_tf.backend import prepare  # type: ignore
    import onnx

    sm_dir.mkdir(parents=True, exist_ok=True)
    onnx_model = onnx.load(str(onnx_path))
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph(str(sm_dir))
    print(f"[savedmodel] exported to {sm_dir}")


# ---------------------------------------------------------------------------
# Representative dataset
# ---------------------------------------------------------------------------


def build_representative_dataset(images_dir: Path, count: int, smoke: bool):
    """Yields NHWC uint8 tensors in [0, 255].

    Note on layout: the PyTorch/ONNX/onnx-tf SavedModel consumes NCHW float32 in
    [0, 1]. To honor WI-17 (TFLite input must be uint8[1,224,224,3] NHWC), we
    wrap the SavedModel with a thin tf.function that accepts NHWC uint8 input,
    casts to float32, scales by 1/255, and transposes to NCHW before feeding the
    inner model. That wrapped graph is what the converter sees, so the
    representative dataset must match the wrapper input: uint8 NHWC.
    """
    from PIL import Image

    samples = []
    if images_dir.exists():
        for ext in ("*.png", "*.jpg", "*.jpeg", "*.webp"):
            samples.extend(images_dir.rglob(ext))
    target = 8 if smoke else count
    samples = samples[:target]

    def gen():
        if not samples:
            print(
                f"[warn] no representative images at {images_dir}; "
                f"falling back to zeros (n={target})"
            )
            for _ in range(target):
                yield [np.zeros((1, IMG_SIZE, IMG_SIZE, 3), dtype=np.uint8)]
            return
        for path in samples:
            try:
                img = Image.open(path).convert("RGB").resize((IMG_SIZE, IMG_SIZE))
                arr = np.asarray(img, dtype=np.uint8)
                arr = arr.reshape(1, IMG_SIZE, IMG_SIZE, 3)
                yield [arr]
            except Exception as e:
                print(f"[warn] failed to load {path}: {e}; using zeros")
                yield [np.zeros((1, IMG_SIZE, IMG_SIZE, 3), dtype=np.uint8)]

    return gen


# ---------------------------------------------------------------------------
# Smoke fallback Keras model
# ---------------------------------------------------------------------------


def _build_smoke_keras_model():
    import tensorflow as tf

    inp = tf.keras.Input(shape=(IMG_SIZE, IMG_SIZE, 3), name="input")
    x = tf.keras.layers.Conv2D(8, (3, 3), strides=2, padding="same", activation="relu")(inp)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    cls = tf.keras.layers.Dense(NUM_CLASSES, name="classification")(x)
    cls = tf.keras.layers.Softmax(name="classification_softmax")(cls)
    topic = tf.keras.layers.Dense(TOPIC_DIM, activation="sigmoid", name="topic_vector")(x)
    model = tf.keras.Model(inp, [cls, topic])
    return model


def _convert_keras_to_tflite(model, tflite_path: Path, rep_dataset_fn) -> None:
    import tensorflow as tf

    # The smoke-fallback Keras model takes NHWC float32 in [0, 1]; rep dataset
    # for the real chain emits NHWC uint8 in [0, 255]. Wrap to bridge the two.
    def _wrap_rep():
        for batch in rep_dataset_fn():
            arr = batch[0]
            if arr.dtype == np.uint8:
                yield [arr.astype(np.float32) / 255.0]
            else:
                yield [arr.astype(np.float32)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = _wrap_rep
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.float32
    tflite_path.parent.mkdir(parents=True, exist_ok=True)
    tflite_path.write_bytes(converter.convert())
    print(f"[tflite] (smoke fallback) wrote {tflite_path}")


def _wrap_savedmodel_nhwc_uint8(sm_dir: Path, wrapped_dir: Path) -> None:
    """Save a thin wrapper around the NCHW float32 SavedModel.

    The wrapper takes NHWC uint8 input, casts to float32, scales by 1/255, and
    transposes to NCHW before invoking the inner SavedModel. Outputs are passed
    through unchanged (float32 [1,7] and [1,20]).
    """
    import tensorflow as tf

    inner = tf.saved_model.load(str(sm_dir))
    inner_sig = inner.signatures["serving_default"]

    # Discover the inner input key (typically "input"); pick the only one.
    inner_in_keys = list(inner_sig.structured_input_signature[1].keys())
    assert len(inner_in_keys) == 1, (
        f"Expected single SavedModel input, got {inner_in_keys}"
    )
    in_key = inner_in_keys[0]

    class Wrapper(tf.Module):
        def __init__(self, inner_sig):
            super().__init__()
            self._inner_sig = inner_sig

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[1, IMG_SIZE, IMG_SIZE, 3], dtype=tf.uint8, name="input")
        ])
        def serving(self, x):
            x = tf.cast(x, tf.float32) / 255.0
            x = tf.transpose(x, perm=[0, 3, 1, 2])  # NHWC -> NCHW
            out = self._inner_sig(**{in_key: x})
            # Force deterministic output names
            return {
                "classification": out["classification"],
                "topic_vector": out["topic_vector"],
            }

    wrapper = Wrapper(inner_sig)
    wrapped_dir.mkdir(parents=True, exist_ok=True)
    tf.saved_model.save(
        wrapper,
        str(wrapped_dir),
        signatures={"serving_default": wrapper.serving},
    )
    print(f"[savedmodel-wrap] wrote NHWC-uint8 wrapper to {wrapped_dir}")


def convert_to_tflite(sm_dir: Path, tflite_path: Path, rep_dataset_fn) -> None:
    import tensorflow as tf

    # Wrap the NCHW float32 SavedModel with an NHWC uint8 entry point so the
    # converted TFLite has input uint8[1,224,224,3] (per WI-17). The rep
    # dataset (NHWC uint8) is fed directly into the wrapper, then quantized.
    wrapped_dir = sm_dir.parent / (sm_dir.name + "_wrapped_nhwc_uint8")
    _wrap_savedmodel_nhwc_uint8(sm_dir, wrapped_dir)

    # The wrapper takes uint8 input directly, but TFLite int8 PTQ requires the
    # converter to see a float-tensor input it can quantize. We therefore feed
    # the wrapped SavedModel as-is and let the converter quantize the cast op.
    # The rep dataset must match the wrapper input dtype (uint8).
    converter = tf.lite.TFLiteConverter.from_saved_model(str(wrapped_dir))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = rep_dataset_fn
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.float32
    tflite_path.parent.mkdir(parents=True, exist_ok=True)
    tflite_path.write_bytes(converter.convert())
    print(f"[tflite] wrote {tflite_path}")


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def validate_tflite(tflite_path: Path) -> None:
    import tensorflow as tf

    interp = tf.lite.Interpreter(model_path=str(tflite_path))
    interp.allocate_tensors()
    inputs = interp.get_input_details()
    outputs = interp.get_output_details()

    assert len(inputs) == 1, f"Expected 1 input, got {len(inputs)}"
    in_d = inputs[0]
    assert in_d["dtype"] == np.uint8, f"Input dtype must be uint8, got {in_d['dtype']}"
    assert list(in_d["shape"]) == [1, IMG_SIZE, IMG_SIZE, 3], (
        f"Input shape must be [1,224,224,3], got {list(in_d['shape'])}"
    )

    assert len(outputs) == 2, f"Expected 2 outputs, got {len(outputs)}"
    sorted_outs = sorted(outputs, key=lambda o: int(o["shape"][-1]))
    last_dims = [int(o["shape"][-1]) for o in sorted_outs]
    assert last_dims == [NUM_CLASSES, TOPIC_DIM], (
        f"Output last dims must be {{7,20}}, got {last_dims}"
    )
    for o in sorted_outs:
        assert o["dtype"] == np.float32, (
            f"Output {o['name']} dtype must be float32, got {o['dtype']}"
        )

    size_bytes = os.path.getsize(tflite_path)
    size_kb = size_bytes / 1024
    assert size_bytes < MAX_MODEL_SIZE_BYTES, (
        f"TFLite size {size_kb:.1f} KB exceeds {MAX_MODEL_SIZE_BYTES // 1024} KB limit"
    )
    print(f"[validate] tflite size: {size_kb:.1f} KB (< {MAX_MODEL_SIZE_BYTES // 1024} KB)")

    # CPU inference timing
    zero_in = np.zeros((1, IMG_SIZE, IMG_SIZE, 3), dtype=np.uint8)
    interp.set_tensor(in_d["index"], zero_in)
    t0 = time.perf_counter()
    interp.invoke()
    inference_ms = (time.perf_counter() - t0) * 1000.0
    print(f"[validate] cpu inference_ms={inference_ms:.2f}")

    print(
        "\n# Snapdragon 7-series benchmark (target median < 60 ms):\n"
        "#   adb push <tflite> /data/local/tmp/\n"
        "#   adb shell /data/local/tmp/benchmark_model \\\n"
        "#     --graph=/data/local/tmp/scrollshield_visual_classifier.tflite \\\n"
        "#     --num_runs=100 --use_nnapi=true\n"
    )


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


def export(args) -> int:
    assert_no_enum_drift()

    checkpoint_path = Path(args.checkpoint).resolve()
    onnx_path = Path(args.onnx_out).resolve()
    sm_dir = Path(args.savedmodel_dir).resolve()
    tflite_path = Path(args.tflite_out).resolve()
    rep_images_dir = Path(args.rep_images_dir).resolve()

    rep_count = args.rep_count if args.rep_count else (8 if args.smoke_test else REPRESENTATIVE_COUNT)
    rep_dataset_fn = build_representative_dataset(rep_images_dir, rep_count, args.smoke_test)

    use_fallback = False
    full_chain_error = None

    try:
        model = load_model(checkpoint_path, smoke=args.smoke_test)
        export_onnx(model, onnx_path)
        try:
            onnx_to_savedmodel(onnx_path, sm_dir)
        except Exception as e:
            full_chain_error = f"onnx -> savedmodel failed: {e}"
            raise
        convert_to_tflite(sm_dir, tflite_path, rep_dataset_fn)
    except Exception as e:
        traceback.print_exc()
        if not args.smoke_test:
            print(f"[error] export failed: {e}", file=sys.stderr)
            return 1
        print(
            f"[smoke-fallback] full ONNX->TF chain failed ({e}); "
            "switching to tiny Keras fallback model"
        )
        use_fallback = True

    if use_fallback:
        try:
            keras_model = _build_smoke_keras_model()
            _convert_keras_to_tflite(keras_model, tflite_path, rep_dataset_fn)
        except Exception as e:
            traceback.print_exc()
            print(f"[error] smoke fallback failed: {e}", file=sys.stderr)
            return 1

    validate_tflite(tflite_path)
    print(f"[done] tflite at {tflite_path}")
    return 0


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    here = Path(__file__).resolve().parent
    repo_root = here.parent
    p.add_argument("--smoke-test", action="store_true")
    p.add_argument(
        "--checkpoint",
        type=str,
        default=str(here / "dataset/_artifacts/visual_classifier.pth"),
    )
    p.add_argument(
        "--onnx-out",
        type=str,
        default=str(here / "dataset/_artifacts/visual_classifier.onnx"),
    )
    p.add_argument(
        "--savedmodel-dir",
        type=str,
        default=str(here / "dataset/_artifacts/visual_classifier_savedmodel"),
    )
    p.add_argument(
        "--tflite-out",
        type=str,
        default=str(repo_root / "app/src/main/assets/scrollshield_visual_classifier.tflite"),
    )
    p.add_argument(
        "--rep-images-dir",
        type=str,
        default=str(here / "dataset/screenshots"),
    )
    p.add_argument("--rep-count", type=int, default=0)
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    return export(args)


if __name__ == "__main__":
    sys.exit(main())
