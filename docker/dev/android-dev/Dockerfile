FROM eclipse-temurin:17-jdk-jammy

# -------------------------------------------------------------------
# System packages: openssh-server + Android SDK build dependencies
# -------------------------------------------------------------------
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openssh-server \
        curl \
        unzip \
        git \
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
# Android SDK
# -------------------------------------------------------------------
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        -o /tmp/cmdline-tools.zip && \
    echo "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  /tmp/cmdline-tools.zip" \
        | sha256sum -c - && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator"

# -------------------------------------------------------------------
# Node.js (LTS) + Claude Code
# -------------------------------------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt-get update && \
    apt-get install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/* && \
    npm install -g @anthropic-ai/claude-code && \
    npm cache clean --force

# -------------------------------------------------------------------
# Gradle
# -------------------------------------------------------------------
RUN curl -fsSL https://services.gradle.org/distributions/gradle-8.5-bin.zip \
        -o /tmp/gradle.zip && \
    echo "9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026  /tmp/gradle.zip" \
        | sha256sum -c - && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip

ENV PATH="/opt/gradle-8.5/bin:${PATH}"

# -------------------------------------------------------------------
# devuser — the SSH login account
# -------------------------------------------------------------------
RUN groupadd -r devuser && \
    useradd -r -g devuser -m -d /home/devuser -s /bin/bash devuser && \
    mkdir -p /home/devuser/.ssh && \
    chmod 700 /home/devuser/.ssh && \
    chown devuser:devuser /home/devuser/.ssh && \
    groupadd -f kvm && \
    usermod -aG kvm devuser

# Give devuser read access to the SDK so gradle tasks work
RUN chown -R root:devuser ${ANDROID_HOME} && \
    chmod -R g+rX ${ANDROID_HOME}

# -------------------------------------------------------------------
# sshd — runs as root (required to drop privileges to devuser on login)
# sshd must be started by root to bind a privileged port and to call
# setuid/setgid when authenticating users.
# -------------------------------------------------------------------
RUN mkdir -p /var/run/sshd

# sshd_config: disable password auth, permit only the devuser account.
# NOTE: No ListenAddress directive — sshd binds 0.0.0.0 inside the
# container. Host-side restriction to 127.0.0.1 is enforced by the
# port mapping in docker-compose.dev.yml.
RUN echo "PermitRootLogin no" >> /etc/ssh/sshd_config && \
    echo "PasswordAuthentication no" >> /etc/ssh/sshd_config && \
    echo "PubkeyAuthentication yes" >> /etc/ssh/sshd_config && \
    echo "AllowUsers devuser" >> /etc/ssh/sshd_config && \
    echo "X11Forwarding no" >> /etc/ssh/sshd_config && \
    echo "PrintMotd no" >> /etc/ssh/sshd_config

# Host keys are stored on a named volume mounted at /etc/ssh/host-keys.
# The entrypoint generates them if absent on first start.

EXPOSE 22

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
