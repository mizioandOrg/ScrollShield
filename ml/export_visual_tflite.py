"""Export visual classifier from PyTorch to TFLite via ONNX.

Pipeline: PyTorch (.pth) -> ONNX (.onnx) -> TensorFlow SavedModel -> TFLite (.tflite)
Requires: torch, torchvision, onnx, onnx-tf, tensorflow
"""

import os
import sys

import torch
import onnx
from torchvision.models import mobilenet_v3_small


OUTPUT_DIR = os.environ.get("OUTPUT_DIR", "/workspace/ml/output")
PTH_PATH = os.path.join(OUTPUT_DIR, "visual_classifier.pth")
ONNX_PATH = os.path.join(OUTPUT_DIR, "visual_classifier.onnx")
SAVEDMODEL_DIR = os.path.join(OUTPUT_DIR, "visual_classifier_savedmodel")
TFLITE_PATH = os.path.join(OUTPUT_DIR, "scrollshield_visual_classifier.tflite")

NUM_CLASSES = int(os.environ.get("NUM_CLASSES", "7"))


def main():
    if not os.path.isfile(PTH_PATH):
        print(f"Error: Trained model not found at {PTH_PATH}", file=sys.stderr)
        sys.exit(1)

    # Step 1: Load PyTorch model
    model = mobilenet_v3_small(weights=None, num_classes=NUM_CLASSES)
    model.load_state_dict(torch.load(PTH_PATH, map_location="cpu", weights_only=True))
    model.eval()
    print("Loaded PyTorch model")

    # Step 2: Export to ONNX
    dummy_input = torch.randn(1, 3, 224, 224)
    torch.onnx.export(
        model,
        dummy_input,
        ONNX_PATH,
        input_names=["input"],
        output_names=["output"],
        opset_version=13,
        dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
    )
    print(f"Exported ONNX model to {ONNX_PATH}")

    # Step 3: ONNX -> TensorFlow SavedModel
    try:
        from onnx_tf.backend import prepare
        import tensorflow as tf

        onnx_model = onnx.load(ONNX_PATH)
        tf_rep = prepare(onnx_model)
        tf_rep.export_graph(SAVEDMODEL_DIR)
        print(f"Converted to TensorFlow SavedModel at {SAVEDMODEL_DIR}")

        # Step 4: TensorFlow SavedModel -> TFLite
        converter = tf.lite.TFLiteConverter.from_saved_model(SAVEDMODEL_DIR)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()

        with open(TFLITE_PATH, "wb") as f:
            f.write(tflite_model)

        size_kb = len(tflite_model) / 1024
        print(f"Exported TFLite model to {TFLITE_PATH} ({size_kb:.1f} KB)")

    except ImportError as e:
        print(f"TFLite export dependencies not available: {e}", file=sys.stderr)
        print("Run this script in the visual-export Docker stage.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
