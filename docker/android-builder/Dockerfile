FROM eclipse-temurin:17-jdk-jammy AS builder

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/appuser/.gradle

RUN groupadd -r appuser && useradd -r -g appuser -m -d /home/appuser appuser

# Install minimal system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Install Android SDK command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Install Gradle to bootstrap the wrapper
RUN curl -fsSL https://services.gradle.org/distributions/gradle-8.5-bin.zip -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip
ENV PATH="/opt/gradle-8.5/bin:${PATH}"

WORKDIR /workspace

# Copy build configuration files
COPY build.gradle.kts settings.gradle.kts ./
COPY app/build.gradle.kts app/build.gradle.kts

# Generate Gradle wrapper
RUN gradle wrapper --gradle-version 8.5

# Set ownership and switch to non-root user
RUN chown -R appuser:appuser /workspace ${ANDROID_HOME} /home/appuser
USER appuser

# Cache dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy remaining source
COPY --chown=appuser:appuser . .

# Build the APK
RUN ./gradlew assembleDebug --no-daemon

FROM alpine:3.19 AS artifacts
RUN adduser -D -u 1000 appuser
WORKDIR /artifacts
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /artifacts/
USER appuser
CMD ["ls", "-la", "/artifacts/"]
