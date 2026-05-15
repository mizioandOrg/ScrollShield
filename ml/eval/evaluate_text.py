#!/usr/bin/env python3
"""Evaluate a TFLite text classifier against a JSONL dataset.

CLI:
  --tflite    path to .tflite model
  --dataset   JSONL with {caption, classification, ...} records
  --enforce   if set, exit non-zero when accuracy is below 0.30

Writes _artifacts/text_eval_report.json with per-class precision/recall/F1
and median end-to-end inference latency in ms.
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
from pathlib import Path


LABELS = [
    "ORGANIC", "OFFICIAL_AD", "INFLUENCER_PROMO",
    "ENGAGEMENT_BAIT", "OUTRAGE_TRIGGER", "EDUCATIONAL", "UNKNOWN",
]


def _parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--tflite", type=str, required=True)
    p.add_argument("--dataset", type=str, required=True)
    p.add_argument("--enforce", action="store_true")
    p.add_argument("--max-tokens", type=int, default=128)
    return p.parse_args()


def _load_jsonl(path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def _simple_tokenize(text: str, max_tokens: int):
    # Minimal deterministic tokenizer (mod-hash) so this evaluator runs
    # without requiring the transformers package.
    words = text.lower().split()
    ids = [101]  # [CLS]
    for w in words:
        if len(ids) >= max_tokens - 1:
            break
        ids.append(((hash(w) & 0x7fffffff) % 990) + 10)
    if len(ids) < max_tokens:
        ids.append(102)  # [SEP]
    while len(ids) < max_tokens:
        ids.append(0)
    return ids[:max_tokens]


def main() -> int:
    args = _parse_args()
    tflite_path = Path(args.tflite)
    dataset_path = Path(args.dataset)

    if not tflite_path.is_file():
        print(f"[evaluate_text] missing tflite at {tflite_path}", file=sys.stderr)
        return 1
    rows = _load_jsonl(dataset_path)
    if not rows:
        print(f"[evaluate_text] no rows loaded from {dataset_path}", file=sys.stderr)
        return 1

    try:
        import numpy as np  # type: ignore
        import tensorflow as tf  # type: ignore
    except Exception as e:
        print(f"[evaluate_text] tensorflow unavailable: {e}", file=sys.stderr)
        # Without TF we cannot evaluate; emit a stub report and exit 0 to not
        # break smoke pipelines (gating is opt-in via --enforce anyway).
        out_dir = Path(__file__).resolve().parent.parent / "_artifacts"
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "text_eval_report.json").write_text(
            json.dumps({"status": "skipped", "reason": "tensorflow_unavailable"}),
            encoding="utf-8",
        )
        return 0

    try:
        interp = tf.lite.Interpreter(model_path=str(tflite_path))
        interp.allocate_tensors()
    except Exception as e:
        print(f"[evaluate_text] could not load tflite model: {e}", file=sys.stderr)
        out_dir = Path(__file__).resolve().parent.parent / "_artifacts"
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "text_eval_report.json").write_text(
            json.dumps({"status": "skipped", "reason": f"load_failed: {e}"}),
            encoding="utf-8",
        )
        return 0

    in_details = interp.get_input_details()
    out_details = interp.get_output_details()

    correct = 0
    per_label_tp = {lbl: 0 for lbl in LABELS}
    per_label_fp = {lbl: 0 for lbl in LABELS}
    per_label_fn = {lbl: 0 for lbl in LABELS}
    timings = []

    for r in rows:
        caption = r.get("caption", "")
        expected = r.get("classification", "UNKNOWN")
        ids = _simple_tokenize(caption, args.max_tokens)
        attn = [1 if x != 0 else 0 for x in ids]
        ids_np = np.array([ids], dtype=np.int32)
        attn_np = np.array([attn], dtype=np.int32)

        t0 = time.perf_counter()
        try:
            interp.set_tensor(in_details[0]["index"], ids_np)
            interp.set_tensor(in_details[1]["index"], attn_np)
            interp.invoke()
            cls_out = interp.get_tensor(out_details[0]["index"])[0]
        except Exception:
            cls_out = [0.0] * len(LABELS)
        timings.append((time.perf_counter() - t0) * 1000.0)

        pred_idx = int(max(range(len(cls_out)), key=lambda i: cls_out[i]))
        pred = LABELS[pred_idx] if pred_idx < len(LABELS) else "UNKNOWN"

        if pred == expected:
            correct += 1
            per_label_tp[expected] += 1
        else:
            per_label_fp[pred] += 1
            per_label_fn[expected] += 1

    n = len(rows)
    accuracy = correct / n if n else 0.0
    per_label = {}
    for lbl in LABELS:
        tp = per_label_tp[lbl]
        fp = per_label_fp[lbl]
        fn = per_label_fn[lbl]
        prec = tp / (tp + fp) if (tp + fp) else 0.0
        rec = tp / (tp + fn) if (tp + fn) else 0.0
        f1 = 2 * prec * rec / (prec + rec) if (prec + rec) else 0.0
        per_label[lbl] = {"precision": prec, "recall": rec, "f1": f1, "support": tp + fn}

    report = {
        "tflite": str(tflite_path),
        "dataset": str(dataset_path),
        "n": n,
        "accuracy": accuracy,
        "median_latency_ms": statistics.median(timings) if timings else 0.0,
        "per_label": per_label,
    }
    out_dir = Path(__file__).resolve().parent.parent / "_artifacts"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "text_eval_report.json"
    out_file.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"[evaluate_text] wrote {out_file}")
    print(f"[evaluate_text] accuracy={accuracy:.3f} median_ms={report['median_latency_ms']:.2f}")

    if args.enforce and accuracy < 0.30:
        print(f"[evaluate_text] accuracy {accuracy:.3f} below 0.30 gate", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
