"""ScrollShield ML classifier training stub.

This stub creates a minimal model for Docker build validation.
Real implementation will use DistilBERT-tiny per WI-15.
"""

import os
import torch
import torch.nn as nn


class SimpleClassifier(nn.Module):
    def __init__(self, input_size=768, num_classes=2):
        super().__init__()
        self.linear = nn.Linear(input_size, num_classes)

    def forward(self, x):
        return self.linear(x)


def main():
    print("Training ScrollShield classifier (stub)...")
    model = SimpleClassifier()

    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    dummy_input = torch.randn(4, 768)
    dummy_labels = torch.tensor([0, 1, 0, 1])

    for epoch in range(2):
        output = model(dummy_input)
        loss = nn.CrossEntropyLoss()(output, dummy_labels)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        print(f"  Epoch {epoch+1}: loss={loss.item():.4f}")

    os.makedirs("output", exist_ok=True)
    torch.save(model.state_dict(), "output/model.pt")
    print("Model saved to output/model.pt")


if __name__ == "__main__":
    main()
