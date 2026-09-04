#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${HOME}/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

# compileSdk と別バージョンの platform を入れても AGP が SDK 未検出で落ちるので catalog を正とする
compile_sdk="$(sed -n 's/^androidCompileSdk = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
if [ -z "${compile_sdk}" ]; then
  echo "gradle/libs.versions.toml から androidCompileSdk を読めなかった" >&2
  exit 1
fi

if [ ! -x "${SDKMANAGER}" ]; then
  echo "[setup] cmdline-tools を導入する"
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT
  curl -fsSL -o "${tmp}/cmdline-tools.zip" "${CMDLINE_TOOLS_URL}"
  unzip -q "${tmp}/cmdline-tools.zip" -d "${tmp}"
  rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
fi

echo "[setup] Android SDK パッケージを導入する (compileSdk=${compile_sdk})"
# yes だと SIGPIPE で 141 を返し pipefail に引っかかるので、有限個の y を流す
{ for _ in $(seq 1 200); do printf 'y\n'; done; } | "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" --licenses > /dev/null
"${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" --install \
  "platform-tools" \
  "platforms;android-${compile_sdk}.0" \
  "build-tools;${compile_sdk}.0.0" > /dev/null

# 環境変数(ANDROID_HOME)はセットアップ後のシェルに残らないので local.properties に書く
echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties

if [ ! -f "${HOME}/.android/debug.keystore" ]; then
  echo "[setup] debug.keystore を作る"
  mkdir -p "${HOME}/.android"
  keytool -genkeypair -keystore "${HOME}/.android/debug.keystore" \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" > /dev/null
fi

# settings.gradle.kts が graphql-java-codegen の fork を GitHub Packages から引くため、
# read:packages 付きの資格情報が無いと構成段階で必ず落ちる。値はコミットせず、
# 起動ごとに Gradle が読む場所へ書き出す。
gpr_user="${GPR_USER:-${GITHUB_ACTOR:-}}"
gpr_key="${GPR_KEY:-${GITHUB_TOKEN:-}}"
if [ -n "${gpr_user}" ] && [ -n "${gpr_key}" ]; then
  mkdir -p "${HOME}/.gradle"
  umask 077
  {
    echo "gpr.user=${gpr_user}"
    echo "gpr.key=${gpr_key}"
  } > "${HOME}/.gradle/gradle.properties"
else
  echo "[setup] GPR_USER / GPR_KEY が無いので GitHub Packages の資格情報を書けなかった" >&2
fi

echo "[setup] 完了"
