# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-17 (Visual Classifier Dataset & Training) for the ScrollShield2 project. Build the labeled-screenshot dataset scaffolding and train the MobileNetV3-Small visual classifier that powers Tier 1 visual classification. The pipeline must produce `scrollshield_visual_classifier.tflite` with two outputs: a 7-class softmax (matching the `Classification` enum from WI-02) and a 20-dim topic vector (matching the `TopicCategory` enum from WI-02). Code must be runnable end-to-end via a `--smoke-test` mode that exercises every stage on a tiny synthetic subset without a GPU; the real 15k-image training run is not executed in this session.

## Context Files

- /home/devuser/ScrollShield2/work-items/WI-17-visual-model-training.md
- /home/devuser/ScrollShield2/work-items/WI-02-data-models.md
- /home/devuser/ScrollShield2/work-items/WI-06-classification-pipeline.md
- /home/devuser/ScrollShield2/work-items/WI-15-testing-ml-pipeline.md
- /home/devuser/ScrollShield2/work-items/WI-18-visual-signature-matching.md

## Target Files (to modify)

- ml/train_visual_classifier.py
- ml/export_visual_tflite.py
- ml/dataset/screenshots/
- ml/dataset/annotations.json
- ml/dataset/splits.json
- ml/eval/evaluate_visual.py
- app/src/main/assets/scrollshield_visual_classifier.tflite

(All paths relative to `/home/devuser/ScrollShield2/`.)

## Rules & Constraints

- Do not modify files outside the Target Files list.
- The `Classification` and `TopicCategory` enums from WI-02 are the source of truth — do not redefine them in the ML code; reference / mirror them exactly.
- Output TFLite model must be < 5 MB; inference must target < 60 ms on a Snapdragon 7-series device.
- Input tensor shape: `uint8[1][224][224][3]`. Output tensors: `float32[1][7]` (classification) and `float32[1][20]`  (topic vector).
- Do not run real training in this session (no GPU available). Produce code plus a reproducible `--smoke-test` path that runs end-to-end on CPU in seconds.
- Do not commit large binary datasets. Use DVC pointers or `.gitignore` for the `dataset/` tree; only commit a tiny smoke-test fixture if needed.

## Review Criteria

1. Training script uses MobileNetV3-Small pretrained on ImageNet with the first 10 conv layers frozen, dual-head architecture (7-class classification head + 20-dim topic regression head with sigmoid).
2. Loss is weighted CrossEntropyLoss (0.7) + MSELoss (0.3); training config matches spec (50 epochs, batch size 32, lr 1e-4, cosine annealing scheduler, early stopping patience 10 on validation F1).
3. Data augmentation pipeline matches spec exactly: RandomHorizontalFlip, RandomRotation(5), ColorJitter(brightness=0.2, contrast=0.2), RandomResizedCrop(224, scale=(0.8, 1.0)).
4. Annotation schema in `annotations.json` matches the JSON schema in WI-17 exactly (`image_id`, `source_app`, `classification`, `confidence`, `ad_indicators[]` with `type` and `bbox`, 20-dim `topic_categories`, `platform_version`, `device_resolution`, `capture_timestamp`, `annotator_id`).
5. Splits are 70/15/15 stratified by class AND platform; per-class minimums (ORGANIC 5000, OFFICIAL_AD 3000, INFLUENCER_PROMO 2500, ENGAGEMENT_BAIT 1500, OUTRAGE_TRIGGER 1500, EDUCATIONAL 1000, UNKNOWN 500); platform mix 40/30/30 TikTok/Instagram/YouTube.
6. Export pipeline goes PyTorch -> ONNX -> TFLite with int8 post-training quantization using a 500-image representative dataset; produces the correct input/output tensor shapes and dtypes.
7. Evaluation script computes per-class precision/recall/F1, confusion matrix, and topic-vector MSE; explicitly surfaces the WI-17 target thresholds (OFFICIAL_AD F1 > 0.85, INFLUENCER_PROMO F1 > 0.75, ENGAGEMENT_BAIT F1 > 0.70, overall accuracy > 0.80, topic MSE < 0.05).
8. Classification labels and topic vector indices are sourced from the WI-02 enums (no redefinition); the code must fail loudly if enum drift is detected.
9. Code is runnable end-to-end on a small smoke dataset without a GPU (CPU fallback, tiny epoch count, synthetic fixtures); large binaries/datasets are gitignored or DVC-tracked, not committed.
10. Output paths and artifact names match the spec exactly: model lands at `app/src/main/assets/scrollshield_visual_classifier.tflite`; model size < 5 MB is validated post-export; a benchmark hook for < 60 ms Snapdragon 7-series inference is documented.

## Implementation Instructions

```
cd /home/devuser/ScrollShield2
python -m pip install -r ml/requirements.txt
python ml/train_visual_classifier.py --smoke-test
python ml/export_visual_tflite.py --smoke-test
python ml/eval/evaluate_visual.py --smoke-test
```
