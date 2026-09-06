#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

bash .cursor/write-gradle-credentials.sh
bash .cursor/install-android-sdk.sh

# 依存とツールチェイン(ビルド用 JDK25 / GraalVM / Kotlin-Wasm の Node・yarn・webpack)を
# 先に取得し、全モジュールのコンパイルとフロントエンドのバンドル生成まで通しておく。
# 初回起動を速くし、構成の破綻をここで検出するため。
./gradlew --no-daemon compileAll :frontend:wasmJsBrowserDistribution
