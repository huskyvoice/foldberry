# syntax=docker/dockerfile:1

# FoldBerry のビルド環境。PC 側には JDK / SDK / adb を入れず、すべてこのイメージに閉じ込める(仕様書 §11.1)。
# バージョンはすべて ARG でここに固定する。値は 2026-11 時点の stable(Google SDK リポジトリ定義で実在確認済みの組)。

FROM eclipse-temurin:21-jdk-noble

# cmdline-tools の配布 zip。ファイル名にビルド番号が入るため固定する。
ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
# API 37 は素の android-37 が無く、マイナー付き(37.0)のみ提供される。
ARG ANDROID_API=37.0
ARG ANDROID_BUILD_TOOLS_VERSION=37.0.0
# AGP 9.5 の最小かつ既定の Gradle。wrapper 生成用に本体も入れる(通常ビルドは ./gradlew)。
ARG GRADLE_VERSION=9.6.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_BUILD_TOOLS_VERSION=${ANDROID_BUILD_TOOLS_VERSION} \
    GRADLE_USER_HOME=/root/.gradle \
    ANDROID_USER_HOME=/root/.android \
    LANG=C.UTF-8

# apksigner を直接呼べるよう build-tools も PATH に入れる。
ENV PATH=/opt/gradle/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}:${PATH}

# ping / nc はワイヤレスデバッグの接続失敗を切り分けるため(仕様書 §14 Warning)。
RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        unzip curl ca-certificates openssl iputils-ping netcat-openbsd procps; \
    rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools"; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"; \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools; \
    mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"; \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools

RUN set -eux; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_API}" \
        "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"; \
    sdkmanager --list_installed

RUN set -eux; \
    curl -fsSL -o /tmp/gradle.zip \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"; \
    unzip -q /tmp/gradle.zip -d /opt; \
    mv "/opt/gradle-${GRADLE_VERSION}" /opt/gradle; \
    rm /tmp/gradle.zip; \
    gradle --version

WORKDIR /work
