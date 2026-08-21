#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# ホットリロード付きの開発サーバー(http://localhost:8081)。フロントエンドだけを
# 触るとき用。画面と GraphQL を一緒に確かめるときは 8080 のバックエンドを使う。
exec ./gradlew --no-daemon :frontend:wasmJsBrowserDevelopmentRun --console=plain
