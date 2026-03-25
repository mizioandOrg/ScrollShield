FROM python:3.11-slim-bookworm AS trainer

RUN groupadd -r mluser && useradd -r -g mluser -m -d /home/mluser mluser

RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/ml

# Copy and install Python dependencies first for layer caching
COPY ml/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Copy ML source code
COPY ml/ .

RUN mkdir -p /workspace/ml/output && \
    chown -R mluser:mluser /workspace

USER mluser

# Single CMD runs training then TFLite export sequentially
CMD ["sh", "-c", "python train_classifier.py && python export_tflite.py"]

FROM alpine:3.19 AS artifact
RUN adduser -D -u 1000 mluser
WORKDIR /output
COPY --from=trainer /workspace/ml/output/ /output/
USER mluser
CMD ["ls", "-la", "/output/"]

# ---- Visual Classification Pipeline (WI-17) ----

FROM nvidia/cuda:12.2.2-runtime-ubuntu22.04 AS visual-base

RUN apt-get update && \
    apt-get install -y --no-install-recommends software-properties-common gpg-agent && \
    add-apt-repository ppa:deadsnakes/ppa && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        python3.11 python3.11-venv python3.11-dev python3.11-distutils \
        build-essential \
        libgl1 libglib2.0-0 libsm6 libxext6 libxrender1 \
        git && \
    ln -sf /usr/bin/python3.11 /usr/bin/python && \
    python -m ensurepip --upgrade && \
    python -m pip install --no-cache-dir --upgrade pip setuptools wheel && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r mluser && useradd -r -g mluser -m -d /home/mluser mluser

WORKDIR /workspace/ml

# --- Visual model training stage (WI-17) ---
FROM visual-base AS visual-trainer

# Install CUDA-matched PyTorch + torchvision
RUN python -m pip install --no-cache-dir \
    torch==2.1.2+cu122 torchvision==0.16.2+cu122 \
    --extra-index-url https://download.pytorch.org/whl/cu122

# Install remaining visual training dependencies
COPY ml/requirements-visual.txt requirements-visual.txt
RUN python -m pip install --no-cache-dir -r requirements-visual.txt

# Copy ML source code
COPY ml/ .

# Dataset mount point — large screenshot datasets are NOT baked into the image
RUN mkdir -p /workspace/ml/output /workspace/ml/datasets && \
    chown -R mluser:mluser /workspace

USER mluser

CMD ["sh", "-c", "python train_visual_classifier.py && python export_visual_tflite.py"]

# --- Visual model export stage (TFLite conversion) ---
FROM visual-base AS visual-export

RUN python -m pip install --no-cache-dir \
    onnx==1.15.0 \
    onnx-tf==1.10.0 \
    tensorflow==2.14.1

RUN groupadd -r mluser 2>/dev/null; useradd -r -g mluser -m -d /home/mluser mluser 2>/dev/null; true

WORKDIR /workspace/ml

COPY ml/ .

RUN mkdir -p /workspace/ml/output && \
    chown -R mluser:mluser /workspace

USER mluser

CMD ["sh", "-c", "python export_visual_tflite.py"]

# --- Visual model artifact extraction ---
FROM alpine:3.19 AS visual-artifact
RUN adduser -D -u 1000 mluser
WORKDIR /output
COPY --from=visual-export /workspace/ml/output/ /output/
USER mluser
CMD ["ls", "-la", "/output/"]
