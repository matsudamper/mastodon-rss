#!/usr/bin/env bash
set -euo pipefail

# GitHub Packages(read:packages)の資格情報を Gradle が読む場所に置く。
# settings.gradle.kts が graphql-java-codegen の fork を Packages から引くため、
# これが無いと構成段階で必ず落ちる。値はコミットせず、Cloud Agent のシークレット
# (環境変数)から起動ごとに書き出す。
if [ -z "${GPR_USER:-}" ] || [ -z "${GPR_KEY:-}" ]; then
  cat >&2 <<'MSG'
GPR_USER / GPR_KEY が未設定です。GitHub Packages(read:packages)の資格情報が要ります。
Cloud Agent のシークレットに GPR_USER(GitHub ユーザー名)と GPR_KEY(read:packages を
付けた Personal Access Token)を登録してください。README の gpr.user / gpr.key に対応します。
MSG
  exit 1
fi

mkdir -p "${HOME}/.gradle"
umask 077
{
  echo "gpr.user=${GPR_USER}"
  echo "gpr.key=${GPR_KEY}"
} > "${HOME}/.gradle/gradle.properties"
