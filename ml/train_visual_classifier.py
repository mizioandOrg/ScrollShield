"""Visual classifier training stub — MobileNetV3-Small on screenshot datasets.

This script trains a MobileNetV3-Small model for visual-first UI classification.
Dataset directory is expected at /workspace/ml/datasets (mounted via Docker volume).
Trained model is saved to /workspace/ml/output/visual_classifier.pth.
"""

import os
import sys

import torch
import torchvision
from torchvision import transforms
from torchvision.models import mobilenet_v3_small


DATASET_DIR = os.environ.get("DATASET_DIR", "/workspace/ml/datasets")
OUTPUT_DIR = os.environ.get("OUTPUT_DIR", "/workspace/ml/output")
MODEL_OUTPUT_PATH = os.path.join(OUTPUT_DIR, "visual_classifier.pth")

NUM_CLASSES = int(os.environ.get("NUM_CLASSES", "7"))
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", "32"))
NUM_EPOCHS = int(os.environ.get("NUM_EPOCHS", "50"))
LEARNING_RATE = float(os.environ.get("LEARNING_RATE", "1e-4"))


def main():
    if not os.path.isdir(DATASET_DIR):
        print(f"Warning: Dataset directory not found at {DATASET_DIR}", file=sys.stderr)
        print("Mount your screenshot dataset via Docker volume.", file=sys.stderr)
        print("Saving untrained model for build validation...", file=sys.stderr)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    model = mobilenet_v3_small(weights=None, num_classes=NUM_CLASSES)
    model = model.to(device)
    print(f"Initialized MobileNetV3-Small with {NUM_CLASSES} classes")

    # Placeholder: real training loop would use torchvision.datasets.ImageFolder
    # transform = transforms.Compose([
    #     transforms.Resize((224, 224)),
    #     transforms.RandomHorizontalFlip(),
    #     transforms.RandomRotation(5),
    #     transforms.ColorJitter(brightness=0.2, contrast=0.2),
    #     transforms.ToTensor(),
    #     transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    # ])
    # dataset = torchvision.datasets.ImageFolder(DATASET_DIR, transform=transform)
    # dataloader = torch.utils.data.DataLoader(dataset, batch_size=BATCH_SIZE, shuffle=True)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    torch.save(model.state_dict(), MODEL_OUTPUT_PATH)
    print(f"Model saved to {MODEL_OUTPUT_PATH}")


if __name__ == "__main__":
    main()
