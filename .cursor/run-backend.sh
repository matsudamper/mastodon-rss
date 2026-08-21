#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# DOMAIN は必須。開発では公開しないので localhost でよい。
export DOMAIN="${DOMAIN:-localhost}"
# :frontend の成果物を同一オリジンで配信し、画面と GraphQL を 8080 だけで動かす。
export STATIC_SRC_DIR="${STATIC_SRC_DIR:-frontend/build/dist/wasmJs/productionExecutable}"
# http で試すので Secure Cookie は外す。付けるとログインしても Cookie が保存されない。
export ADMIN_COOKIE_SECURE="${ADMIN_COOKIE_SECURE:-false}"
# 開発用の管理パスワード(ci-password)のハッシュ。build.yml と同じ公開値で、本番では使わない。
export ADMIN_PASSWORD_HASH="${ADMIN_PASSWORD_HASH:-pbkdf2-sha256:210000:HP9l044PxKN0LJhMy5GyrQ:tWTwqvZDQgC75D39BMPPm2TvA3F9S3bUP1-XyH5l4HQ}"

exec ./gradlew --no-daemon :backend:run
