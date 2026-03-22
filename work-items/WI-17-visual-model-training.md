# WI-17: Visual Classifier Dataset & Training

## Source
- Module 2: Classification Pipeline (Tier 1 Visual Classification)
- Demo Proposal Section 6 (Doomscrolling Agent Fleet)
- Training data requirements for MobileNetV3-Small fine-tuning

## Goal
Build the labeled screenshot dataset and train the MobileNetV3-Small visual classifier that powers Tier 1 (primary) visual classification. Produce the `scrollshield_visual_classifier.tflite` model file.

## Context
The visual classifier is the primary detection path in ScrollShield's visual-first architecture. It classifies screen captures of social media feeds into 7 classes (ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO, ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL, UNKNOWN) and produces a 20-dimensional topic vector. The model must be small enough for on-device inference (< 5MB) and fast enough for real-time classification (< 60ms on Snapdragon 7-series).

## Dependencies
- **Hard**: WI-02 (Classification enum defines the 7 target classes, TopicCategory enum defines the 20 topic dimensions)
- **Integration**: WI-06 (VisualClassifier consumes the trained model), WI-15 (testing infrastructure validates the model), WI-18 (visual model benchmark validates on-device performance)

## Files to Create / Modify

- `ml/train_visual_classifier.py`
- `ml/export_visual_tflite.py`
- `ml/dataset/screenshots/` — raw screenshot images organized by class
- `ml/dataset/annotations.json` — annotation file
- `ml/dataset/splits.json` — train/val/test split definitions
- `ml/eval/evaluate_visual.py` — evaluation metrics script
- `app/src/main/assets/scrollshield_visual_classifier.tflite` — output model

## Detailed Specification

### Dataset Requirements

**Target classes** (7, matching Classification enum):
1. ORGANIC — normal user-generated content
2. OFFICIAL_AD — platform-labeled sponsored content with branded overlays, CTA buttons, product cards
3. INFLUENCER_PROMO — influencer content with product placements, discount codes, affiliate links
4. ENGAGEMENT_BAIT — manipulative visual patterns (fake buttons, misleading thumbnails, ragebait)
5. OUTRAGE_TRIGGER — inflammatory visual elements (all-caps overlays, warning graphics)
6. EDUCATIONAL — educational content (diagrams, tutorials, lecture framing)
7. UNKNOWN — ambiguous/unclassifiable

**Minimum dataset size**: 15,000 labeled screenshots
- ORGANIC: 5,000
- OFFICIAL_AD: 3,000
- INFLUENCER_PROMO: 2,500
- ENGAGEMENT_BAIT: 1,500
- OUTRAGE_TRIGGER: 1,500
- EDUCATIONAL: 1,000
- UNKNOWN: 500

**Platform distribution**: TikTok 40%, Instagram 30%, YouTube 30%

**Data acquisition**:
1. Agent fleet screenshots (60%): Capture full-screen screenshots from doomscrolling agents (Section 6 of proposal) across demographic profiles on TikTok, Instagram Reels, YouTube Shorts.
2. Manual annotation (25%): 3-5 annotators via Labelbox/Scale AI. Inter-annotator agreement target: Cohen's kappa > 0.85.
3. Synthetic augmentation (15%): rotation +/-5 degrees, brightness +/-20%, compression artifacts, resolution scaling 0.5x-1.5x, synthetic ad overlays on organic content.

**Annotation schema**:
```json
{
  "image_id": "string",
  "source_app": "tiktok|instagram|youtube",
  "classification": "ORGANIC|OFFICIAL_AD|...",
  "confidence": 0.0-1.0,
  "ad_indicators": [
    {"type": "cta_button|sponsored_label|product_card|discount_code|brand_logo", "bbox": [x, y, w, h]}
  ],
  "topic_categories": [0.0, ...],  // 20-dim topic vector ground truth
  "platform_version": "string",
  "device_resolution": "1080x2340",
  "capture_timestamp": "ISO8601",
  "annotator_id": "string"
}
```

**Validation split**: 70% train / 15% validation / 15% test, stratified by class and platform.

### Training Pipeline

#### `train_visual_classifier.py`
- Base model: MobileNetV3-Small pretrained on ImageNet (torchvision)
- Transfer learning: freeze first 10 convolutional layers, fine-tune remaining layers
- New classification head: `nn.Linear(576, 7)` (7-class softmax)
- New topic head: `nn.Linear(576, 20)` (20-dim regression, sigmoid activation)
- Loss: CrossEntropyLoss (classification) + MSELoss (topic vector), weighted 0.7 / 0.3
- Training: 50 epochs, batch size 32, lr 1e-4, cosine annealing scheduler
- Data augmentation: RandomHorizontalFlip, RandomRotation(5), ColorJitter(brightness=0.2, contrast=0.2), RandomResizedCrop(224, scale=(0.8, 1.0))
- Early stopping: patience 10 epochs on validation F1
- Hardware: single V100 GPU, ~4 hours training time

#### `export_visual_tflite.py`
- Convert PyTorch -> ONNX -> TFLite
- Int8 quantization (post-training quantization with representative dataset of 500 images)
- Output: `scrollshield_visual_classifier.tflite` (~3.4MB)
- Input tensor: `uint8[1][224][224][3]`
- Output tensors: `float32[1][7]`, `float32[1][20]`
- Validation: inference < 60ms on Snapdragon 7-series (via TFLite benchmark tool)

#### `evaluate_visual.py`
- Compute per-class precision, recall, F1
- Compute confusion matrix
- Compute topic vector MSE
- Target metrics:
  - F1 > 0.85 OFFICIAL_AD
  - F1 > 0.75 INFLUENCER_PROMO
  - F1 > 0.70 ENGAGEMENT_BAIT
  - Overall accuracy > 0.80
  - Topic vector MSE < 0.05

## Acceptance Criteria
- Dataset contains >= 15,000 labeled screenshots across 3 platforms
- Inter-annotator agreement (Cohen's kappa) > 0.85 on a 500-image validation set
- Trained model achieves F1 > 0.85 OFFICIAL_AD, F1 > 0.75 INFLUENCER_PROMO on test set
- Exported TFLite model < 5MB
- TFLite inference < 60ms on Snapdragon 7-series
- Model correctly outputs both 7-class probabilities and 20-dim topic vector
- Evaluation script produces per-class metrics and confusion matrix

## Notes
- The agent fleet (Section 6 of proposal) is the primary data source. If the fleet is not yet operational, use manual screenshot collection from test devices as a bootstrap.
- Dataset versioning: use DVC (Data Version Control) to track dataset versions alongside model versions.
- The visual model should be retrained monthly as platforms evolve their ad creative patterns.
- Consider federated learning in V2 for on-device model improvement without sharing raw data.
