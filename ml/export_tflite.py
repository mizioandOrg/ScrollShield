"""Export trained PyTorch model to TFLite format.

Conversion path: PyTorch (.pt state dict) -> ONNX (.onnx) -> TensorFlow SavedModel -> TFLite (.tflite)
Requires: torch, onnx, onnx-tf, tensorflow
"""

import os
import sys

import torch
import torch.nn as nn
import onnx


OUTPUT_DIR = os.environ.get("OUTPUT_DIR", "output")
PT_PATH = os.path.join(OUTPUT_DIR, "model.pt")
ONNX_PATH = os.path.join(OUTPUT_DIR, "text_classifier.onnx")
SAVEDMODEL_DIR = os.path.join(OUTPUT_DIR, "text_classifier_savedmodel")
TFLITE_PATH = os.path.join(OUTPUT_DIR, "scrollshield_classifier.tflite")

INPUT_SIZE = int(os.environ.get("INPUT_SIZE", "768"))
NUM_CLASSES = int(os.environ.get("NUM_CLASSES", "2"))


class SimpleClassifier(nn.Module):
    """Matches architecture defined in train_classifier.py."""

    def __init__(self, input_size=768, num_classes=2):
        super().__init__()
        self.linear = nn.Linear(input_size, num_classes)

    def forward(self, x):
        return self.linear(x)


def main():
    if not os.path.isfile(PT_PATH):
        print(f"Error: Trained model not found at {PT_PATH}", file=sys.stderr)
        sys.exit(1)

    # Step 1: Load PyTorch model
    model = SimpleClassifier(input_size=INPUT_SIZE, num_classes=NUM_CLASSES)
    model.load_state_dict(torch.load(PT_PATH, map_location="cpu", weights_only=True))
    model.eval()
    print(f"Loaded PyTorch model from {PT_PATH}")

    # Step 2: Export to ONNX
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    dummy_input = torch.randn(1, INPUT_SIZE)
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

    # Step 3: ONNX -> TensorFlow SavedModel -> TFLite
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
        print("Ensure onnx-tf and tensorflow are installed.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
