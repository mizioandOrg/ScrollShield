"""Export trained PyTorch model to TFLite format.

Conversion path: PyTorch -> ONNX -> TFLite (via TensorFlow).
This stub creates a minimal TFLite file for Docker build validation.
"""

import os
import numpy as np


def main():
    print("Exporting model to TFLite format (stub)...")

    try:
        import tensorflow as tf

        input_layer = tf.keras.Input(shape=(768,), name="input")
        output_layer = tf.keras.layers.Dense(2, activation="softmax", name="output")(input_layer)
        model = tf.keras.Model(inputs=input_layer, outputs=output_layer)

        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        tflite_model = converter.convert()

        os.makedirs("output", exist_ok=True)
        output_path = "output/scrollshield_classifier.tflite"
        with open(output_path, "wb") as f:
            f.write(tflite_model)

        size_kb = len(tflite_model) / 1024
        print(f"TFLite model exported to {output_path} ({size_kb:.1f} KB)")

    except ImportError as e:
        print(f"TensorFlow not available: {e}")
        print("Creating placeholder TFLite file...")
        os.makedirs("output", exist_ok=True)
        with open("output/scrollshield_classifier.tflite", "wb") as f:
            f.write(b"PLACEHOLDER_TFLITE_MODEL")


if __name__ == "__main__":
    main()
