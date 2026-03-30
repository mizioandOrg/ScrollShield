FROM python:3.11-slim-bookworm

# -------------------------------------------------------------------
# System packages
# -------------------------------------------------------------------
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openssh-server \
        build-essential \
        git \
        curl \
        ca-certificates \
        htop \
        screen \
        nano && \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends gh && \
    rm -rf /var/lib/apt/lists/*

# -------------------------------------------------------------------
# Python dependencies — installed at build time using the same
# requirements files referenced by CI, so the dev environment matches
# the CI/training environment exactly.
# Context for this Dockerfile is the repo root (/workspace).
# -------------------------------------------------------------------
WORKDIR /workspace

COPY ml/requirements.txt /tmp/ml-requirements.txt
RUN pip install --no-cache-dir -r /tmp/ml-requirements.txt

COPY ml/requirements-visual.txt /tmp/ml-requirements-visual.txt
# CUDA-matched PyTorch must be installed first, before requirements-visual.txt,
# because that file pins torch/torchvision without the +cu122 suffix.
# We pre-install the CUDA build then skip torch in the requirements file.
RUN pip install --no-cache-dir \
        torch==2.1.2 \
        torchvision==0.16.2 && \
    pip install --no-cache-dir -r /tmp/ml-requirements-visual.txt

# -------------------------------------------------------------------
# devuser — the SSH login account
# -------------------------------------------------------------------
RUN groupadd -r devuser && \
    useradd -r -g devuser -m -d /home/devuser -s /bin/bash devuser && \
    mkdir -p /home/devuser/.ssh && \
    chmod 700 /home/devuser/.ssh && \
    chown devuser:devuser /home/devuser/.ssh

# -------------------------------------------------------------------
# sshd — runs as root (required to bind port and drop to devuser)
# NOTE: No ListenAddress directive — sshd binds 0.0.0.0 inside the
# container. Host restriction to 127.0.0.1 is in docker-compose.dev.yml.
# -------------------------------------------------------------------
RUN mkdir -p /var/run/sshd

RUN echo "PermitRootLogin no" >> /etc/ssh/sshd_config && \
    echo "PasswordAuthentication no" >> /etc/ssh/sshd_config && \
    echo "PubkeyAuthentication yes" >> /etc/ssh/sshd_config && \
    echo "AllowUsers devuser" >> /etc/ssh/sshd_config && \
    echo "X11Forwarding no" >> /etc/ssh/sshd_config && \
    echo "PrintMotd no" >> /etc/ssh/sshd_config

EXPOSE 22

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
