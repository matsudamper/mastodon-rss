#!/usr/bin/env bash
set -euo pipefail

# SessionStart フックはローカルの CLI や IDE でも走る。手元の local.properties や
# gradle.properties を書き換えたくないので、フックからのときはリモートだけで動かす。
# 手で実行するぶんにはどこでも動く
if [ -n "${CLAUDE_PROJECT_DIR:-}" ] && [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# セットアップスクリプト欄から呼ぶときは cwd がリポジトリ外なので、自分の位置から解決する
cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"

# 構築済みの SDK を無視して入れ直さないよう、local.properties の sdk.dir も候補にする
sdk_dir_in_local_properties=""
if [ -f local.properties ]; then
  sdk_dir_in_local_properties="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' local.properties | tail -n 1)"
fi
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${sdk_dir_in_local_properties:-${HOME}/android-sdk}}}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
# AGP 9.4.0 の DEFAULT_BUILD_TOOLS_REVISION。compileSdk とは独立していて、
# 揃えないと AGP がビルド中に別バージョンを取りに行く
BUILD_TOOLS_VERSION="36.0.0"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

# compileSdk と別バージョンの platform を入れても AGP が SDK 未検出で落ちるので catalog を正とする
compile_sdk="$(sed -n 's/^androidCompileSdk = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
if [ -z "${compile_sdk}" ]; then
  echo "gradle/libs.versions.toml から androidCompileSdk を読めなかった" >&2
  exit 1
fi

# SessionStart フックからも呼ぶので、揃っているときは sdkmanager を起動しない。
# sdkmanager は何もすることが無くてもリモートのリポジトリを引きに行って数秒かかる
if [ ! -f "${ANDROID_SDK_ROOT}/platforms/android-${compile_sdk}.0/android.jar" ] ||
  [ ! -d "${ANDROID_SDK_ROOT}/build-tools/${BUILD_TOOLS_VERSION}" ] ||
  [ ! -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]; then
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
    "build-tools;${BUILD_TOOLS_VERSION}" > /dev/null
fi

# 環境変数(ANDROID_HOME)はセットアップ後のシェルに残らないので local.properties に書く。
# cmake.dir など他のローカル設定を消さないよう sdk.dir の行だけ差し替える
tmp_local_properties="$(mktemp ./.local.properties.XXXXXX)"
if [ -f local.properties ]; then
  grep -v -E '^[[:space:]]*sdk\.dir[[:space:]]*=' local.properties > "${tmp_local_properties}" || true
fi
echo "sdk.dir=${ANDROID_SDK_ROOT}" >> "${tmp_local_properties}"
mv "${tmp_local_properties}" local.properties

android_user_home="${ANDROID_USER_HOME:-${HOME}/.android}"
if [ ! -f "${android_user_home}/debug.keystore" ]; then
  echo "[setup] debug.keystore を作る"
  mkdir -p "${android_user_home}"
  keytool -genkeypair -keystore "${android_user_home}/debug.keystore" \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" > /dev/null
  # 既定の umask だと 0644 になり、秘密鍵を同一ホストの別ユーザーにコピーされる
  chmod 600 "${android_user_home}/debug.keystore"
fi

# settings.gradle.kts が graphql-java-codegen の fork を GitHub Packages から引くため、
# read:packages 付きの資格情報が無いと構成段階で必ず落ちる。値はコミットせず、
# 起動ごとに Gradle が読む場所へ書き出す。
gradle_user_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
gradle_properties="${gradle_user_home}/gradle.properties"

read_property() {
  [ -f "${gradle_properties}" ] || return 0
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\\(.*\\)$/\\1/p" "${gradle_properties}" | tail -n 1
}

# 環境変数はセッションごとに配られる新しい値なので、あればそちらで上書きする。
# 無ければファイルの値をそのまま使う(手で書いた資格情報だけの環境を失敗させない)
gpr_user="${GPR_USER:-${GITHUB_ACTOR:-$(read_property 'gpr\.user')}}"
gpr_key="${GPR_KEY:-${GITHUB_TOKEN:-$(read_property 'gpr\.key')}}"

describe_source() {
  # 値そのものは出さない。どこから取れたかだけ示す
  for name in "$@"; do
    eval "value=\${${name}:-}"
    [ -n "${value}" ] && { echo "${name}"; return 0; }
  done
  echo "無し"
}

if [ -z "${gpr_user}" ] || [ -z "${gpr_key}" ]; then
  cat >&2 <<MSG
GitHub Packages(read:packages)の資格情報が揃っていない。無いと Gradle は構成段階で落ちる。
  gpr.user: $(describe_source GPR_USER GITHUB_ACTOR) / ${gradle_properties}: $([ -n "$(read_property 'gpr\.user')" ] && echo 有り || echo 無し)
  gpr.key : $(describe_source GPR_KEY GITHUB_TOKEN) / ${gradle_properties}: $([ -n "$(read_property 'gpr\.key')" ] && echo 有り || echo 無し)
MSG
  # セットアップスクリプトとして走る段階では GITHUB_ACTOR / GITHUB_TOKEN がまだ無い。
  # ここで失敗させるとセッションが起動しないので、フックの回に書けることへ賭ける
  if [ -z "${CLAUDE_PROJECT_DIR:-}" ]; then
    echo "[setup] 資格情報はセッション開始時に書き直す" >&2
    exit 0
  fi
  exit 1
fi

mkdir -p "${gradle_user_home}"
tmp_properties="$(mktemp "${gradle_user_home}/.gradle.properties.XXXXXX")"
chmod 600 "${tmp_properties}"
if [ -f "${gradle_properties}" ]; then
  grep -v -E '^[[:space:]]*gpr\.(user|key)[[:space:]]*=' "${gradle_properties}" > "${tmp_properties}" || true
fi
{
  echo "gpr.user=${gpr_user}"
  echo "gpr.key=${gpr_key}"
} >> "${tmp_properties}"
mv "${tmp_properties}" "${gradle_properties}"

echo "[setup] 完了"
