#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

CMDLINE_TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-37.0"
  "build-tools;36.0.0"
)

if [ ! -x "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT
  curl -fsSL -o "${tmp}/cmdline-tools.zip" "${CMDLINE_TOOLS_ZIP_URL}"
  unzip -q "${tmp}/cmdline-tools.zip" -d "${tmp}"
  mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
fi

if [ ! -f "${HOME}/.android/licenses/android-sdk-license" ]; then
  { for _ in $(seq 1 200); do printf 'y\n'; done; } | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null
fi

sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --install "${SDK_PACKAGES[@]}"

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=${ANDROID_SDK_ROOT}" > "${repo_root}/local.properties"
