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

# 通常の JDK と GraalVM は foojay-resolver が必要な版を用意する。
# CI の全ジョブに相当するタスクを一度に実行し、依存関係とツールチェインもキャッシュする。
./gradlew \
  ktlintCheck \
  test \
  :frontend:wasmJsBrowserDevelopmentExecutableDistribution \
  :backend:nativeTestBuild
