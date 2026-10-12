#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

# 変更後も再利用できるコンパイル結果と依存関係をキャッシュする。
# バンドルと native-image は生成に時間がかかる一方、ソース変更後は再利用しにくいので作らない。
./gradlew compileAll
