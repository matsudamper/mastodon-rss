#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "${GITHUB_ACTOR:-}" || -z "${GITHUB_TOKEN:-}" ]]; then
  cat >&2 <<'EOF'
GitHub Packages から依存関係を取得するため、Codex Cloud の環境変数に
GITHUB_ACTOR と GITHUB_TOKEN（read:packages 権限付き）を設定してください。
EOF
  exit 1
fi

# 変更後も再利用できるコンパイル結果と依存関係をキャッシュする。
# バンドルと native-image は生成に時間がかかる一方、ソース変更後は再利用しにくいので作らない。
./gradlew compileAll
