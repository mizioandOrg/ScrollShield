# Approved Plan — Visual-First ML Pipeline Docker Infrastructure

**Score: 10/10 at Iteration 4**

## Overview

Update existing Docker infrastructure to support the visual-first classification architecture (WI-16, WI-17, WI-18) while preserving all existing text pipeline functionality.

## Changes Applied

### Modified files (3):

1. **`docker/ml-pipeline/Dockerfile`** — Appended 4 new stages (existing trainer + artifact stages untouched):
   - `visual-base`: FROM nvidia/cuda:12.2.2-runtime-ubuntu22.04 with Python 3.11 via deadsnakes PPA, system deps for OpenCV/image processing, git for DVC
   - `visual-trainer`: CUDA-matched PyTorch + torchvision, visual training deps, dataset mount point, non-root mluser
   - `visual-export`: onnx-tf + TensorFlow for ONNX→TFLite conversion (no GPU needed)
   - `visual-artifact`: Alpine-based artifact extraction from visual-trainer output

2. **`docker-compose.yml`** — Added 2 new services + 1 volume:
   - `ml-visual-pipeline`: targets visual-trainer, GPU passthrough (nvidia, count 1), 16G memory, dataset bind mount (read-only), NVIDIA env vars
   - `ml-visual-export`: targets visual-export, no GPU, model output volume
   - `ml-visual-output`: named volume for visual model artifacts

3. **`.dockerignore`** — Appended exclusions for datasets, model artifacts, DVC cache

### New files (3):

4. **`ml/requirements-visual.txt`** — opencv-python-headless, onnx, albumentations, scikit-learn, dvc[s3], Pillow (all pinned)

5. **`ml/train_visual_classifier.py`** — MobileNetV3-Small training stub (imports torch, torchvision, validates dataset dir, saves checkpoint)

6. **`ml/export_visual_tflite.py`** — Full PyTorch→ONNX→TF SavedModel→TFLite export pipeline using onnx_tf

## Key Design Decisions

- **CUDA base image** (nvidia/cuda:12.2.2-runtime-ubuntu22.04) for GPU training — python:3.11-slim lacks CUDA libraries
- **Python 3.11 via deadsnakes PPA** — Ubuntu 22.04 ships Python 3.10, PPA needed for 3.11
- **Separate requirements-visual.txt** — keeps text pipeline deps independent; torch/torchvision installed inline with +cu122 suffix
- **TensorFlow isolated to export stage** — avoids ~500MB bloat in training image
- **Dataset bind mount (:ro)** — large screenshot datasets mounted read-only, never baked into images
- **OpenCV system deps isolated** — libgl1, libglib2.0-0 etc. only in visual-base, not in text trainer

## Iteration History

- **Iteration 1 (5/10)**: Missing onnx-tf, no CUDA base image, volume paths incomplete, OpenCV bloating text trainer, stubs undefined
- **Iteration 2 (8/10)**: Fixed all iteration 1 issues; Python 3.11 PPA omission, TF bloat in trainer, apt cleanup gaps
- **Iteration 3 (9/10)**: Fixed PPA, split TF to export stage; DVC missing, CUDA base ambiguous, compose service incomplete
- **Iteration 4 (10/10)**: Added DVC + Pillow, explicit CUDA base, fully specified visual trainer compose service
