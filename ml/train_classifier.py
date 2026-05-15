#!/usr/bin/env python3
"""Text classifier training script for ScrollShield.

CLI:
  --epochs       (default 5)
  --smoke        smoke-train on a tiny fabricated set (CPU-friendly)
  --data         path to JSONL training data
  --out          checkpoint output path
  --seed         (default 20260515)
  --batch-size   (default 16)
  --lr           (default 5e-5)

Architecture: BertModel (bert-tiny) + classification head (7 classes, softmax)
              + topic head (20-dim, sigmoid).
Loss = 0.7 * CrossEntropyLoss(class) + 0.3 * MSELoss(topic)

Writes:
  - args.out (torch checkpoint: {state_dict, config, tokenizer_name})
  - ../app/src/main/assets/wordpiece_vocab.txt (BERT vocab)
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
import time
from pathlib import Path


def _parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--epochs", type=int, default=5)
    p.add_argument("--smoke", action="store_true")
    p.add_argument("--data", type=str, default="dataset/text_train.jsonl")
    p.add_argument("--out", type=str, default="checkpoints/text_classifier.pt")
    p.add_argument("--seed", type=int, default=20260515)
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument("--lr", type=float, default=5e-5)
    return p.parse_args()


LABELS = [
    "ORGANIC", "OFFICIAL_AD", "INFLUENCER_PROMO",
    "ENGAGEMENT_BAIT", "OUTRAGE_TRIGGER", "EDUCATIONAL", "UNKNOWN",
]
NUM_CLASSES = 7
NUM_TOPICS = 20
HIDDEN = 128


def _set_seed(seed: int) -> None:
    random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    try:
        import numpy as np  # type: ignore
        np.random.seed(seed)
    except Exception:
        pass
    try:
        import torch  # type: ignore
        torch.manual_seed(seed)
        torch.cuda.manual_seed_all(seed)
    except Exception:
        pass


def _load_jsonl(path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def _fabricate_smoke_rows():
    rows = []
    for i in range(50):
        label = LABELS[i % len(LABELS)]
        topic_idx = i % NUM_TOPICS
        vec = [0.0] * NUM_TOPICS
        vec[topic_idx] = 1.0
        rows.append({
            "caption": f"smoke caption {i} {label.lower()} topic{topic_idx}",
            "classification": label,
            "topic_vector": vec,
        })
    return rows


def _write_vocab(tokenizer, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if hasattr(tokenizer, "get_vocab"):
        vocab = tokenizer.get_vocab()
        ordered = sorted(vocab.items(), key=lambda kv: kv[1])
        with out_path.open("w", encoding="utf-8") as f:
            for tok, _idx in ordered:
                f.write(tok + "\n")
    else:
        with out_path.open("w", encoding="utf-8") as f:
            for tok in ["[PAD]", "[UNK]", "[CLS]", "[SEP]"]:
                f.write(tok + "\n")


def _write_fallback_vocab(vocab_out_path: Path) -> None:
    vocab_out_path.parent.mkdir(parents=True, exist_ok=True)
    with vocab_out_path.open("w", encoding="utf-8") as f:
        for tok in ["[PAD]", "[unused1]", "[unused2]", "[unused3]",
                    "[unused4]", "[unused5]", "[unused6]", "[unused7]",
                    "[unused8]", "[unused9]"]:
            f.write(tok + "\n")
        f.write("[UNK]\n")
        for i in range(11, 101):
            f.write(f"[unused{i}]\n")
        f.write("[CLS]\n[SEP]\n[MASK]\n")
        for w in ["the", "a", "and", "to", "of", "in", "for", "on", "with",
                  "this", "that", "is", "was", "you", "my", "i", "we", "be",
                  "sponsored", "ad", "promoted", "buy", "now", "free",
                  "limited", "time", "offer", "discount", "sale",
                  "comedy", "music", "food", "sports", "fashion", "tech",
                  "education", "gaming", "finance", "politics", "animals",
                  "travel", "art", "news", "relationships", "cars", "home",
                  "parenting", "health", "nature",
                  "tag", "friend", "comment", "double", "tap",
                  "scandal", "shocking", "truth", "lie",
                  "learned", "today", "science", "research", "concept",
                  "video", "watch", "code", "link", "bio"]:
            f.write(w + "\n")


def _save_fallback_checkpoint(args, vocab_out_path: Path) -> int:
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "state_dict_fallback": True,
        "config": {
            "num_classes": NUM_CLASSES,
            "num_topics": NUM_TOPICS,
            "hidden": HIDDEN,
            "max_tokens": 128,
        },
        "tokenizer_name": "fallback-bert-tiny",
    }
    out.write_text(json.dumps(payload), encoding="utf-8")
    _write_fallback_vocab(vocab_out_path)
    print(f"[SMOKE-FALLBACK] Wrote fallback checkpoint to {out}")
    print(f"[SMOKE-FALLBACK] Wrote synthetic vocab to {vocab_out_path}")
    return 0


def main() -> int:
    args = _parse_args()
    _set_seed(args.seed)

    vocab_out = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "wordpiece_vocab.txt"

    try:
        import torch  # type: ignore
        from torch import nn  # type: ignore
        from torch.utils.data import DataLoader, Dataset  # type: ignore
        from transformers import AutoTokenizer, BertModel  # type: ignore
    except Exception as e:
        if args.smoke:
            print(f"[SMOKE-FALLBACK] torch/transformers unavailable ({e}); writing minimal checkpoint")
            return _save_fallback_checkpoint(args, vocab_out)
        raise

    try:
        tokenizer = AutoTokenizer.from_pretrained("prajjwal1/bert-tiny")
    except Exception as e:
        if args.smoke:
            print(f"[SMOKE-FALLBACK] tokenizer download failed ({e}); writing minimal checkpoint")
            return _save_fallback_checkpoint(args, vocab_out)
        raise

    _write_vocab(tokenizer, vocab_out)

    if args.smoke:
        rows = _fabricate_smoke_rows()
        epochs = min(args.epochs, 1)
    else:
        rows = _load_jsonl(Path(args.data))
        epochs = args.epochs

    class JsonlDataset(Dataset):
        def __init__(self, rows):
            self.rows = rows

        def __len__(self):
            return len(self.rows)

        def __getitem__(self, idx):
            r = self.rows[idx]
            enc = tokenizer(
                r["caption"], max_length=128, padding="max_length",
                truncation=True, return_tensors="pt",
            )
            label = LABELS.index(r["classification"])
            topic = r.get("topic_vector", [0.0] * NUM_TOPICS)
            return {
                "input_ids": enc["input_ids"][0].long(),
                "attention_mask": enc["attention_mask"][0].long(),
                "label": torch.tensor(label, dtype=torch.long),
                "topic": torch.tensor(topic, dtype=torch.float),
            }

    random.Random(args.seed).shuffle(rows)
    split = max(1, int(len(rows) * 0.1))
    eval_rows, train_rows = rows[:split], rows[split:]

    train_ds = JsonlDataset(train_rows)
    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True)

    class TextClassifier(nn.Module):
        def __init__(self):
            super().__init__()
            self.bert = BertModel.from_pretrained("prajjwal1/bert-tiny")
            hidden = self.bert.config.hidden_size
            self.cls_head = nn.Linear(hidden, NUM_CLASSES)
            self.topic_head = nn.Linear(hidden, NUM_TOPICS)

        def forward(self, input_ids, attention_mask):
            out = self.bert(input_ids=input_ids, attention_mask=attention_mask)
            pooled = out.last_hidden_state[:, 0, :]
            logits = self.cls_head(pooled)
            topic = torch.sigmoid(self.topic_head(pooled))
            return logits, topic

    model = TextClassifier()
    optim = torch.optim.AdamW(model.parameters(), lr=args.lr)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optim, T_max=max(1, epochs))
    ce = nn.CrossEntropyLoss()
    mse = nn.MSELoss()

    start = time.time()
    for ep in range(epochs):
        model.train()
        total = 0.0
        n_batches = 0
        for batch in train_loader:
            optim.zero_grad()
            logits, topic = model(batch["input_ids"], batch["attention_mask"])
            loss = 0.7 * ce(logits, batch["label"]) + 0.3 * mse(topic, batch["topic"])
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optim.step()
            total += loss.item()
            n_batches += 1
            if args.smoke and n_batches >= 3:
                break
        scheduler.step()
        avg = total / max(1, n_batches)
        elapsed = time.time() - start
        print(f"[train] epoch={ep + 1}/{epochs} loss={avg:.4f} elapsed={elapsed:.1f}s")
        if args.smoke and elapsed > 55:
            print("[train] smoke time-budget exceeded, stopping early")
            break

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "state_dict": model.state_dict(),
            "config": {
                "num_classes": NUM_CLASSES,
                "num_topics": NUM_TOPICS,
                "hidden": HIDDEN,
                "max_tokens": 128,
            },
            "tokenizer_name": "prajjwal1/bert-tiny",
        },
        str(out_path),
    )
    print(f"[train] checkpoint saved to {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
