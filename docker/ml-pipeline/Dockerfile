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
