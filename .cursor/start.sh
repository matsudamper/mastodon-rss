#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

bash .cursor/write-gradle-credentials.sh

# 実行用 DB にスキーマを適用する。起動時の自動適用は無いため、空の DB のままだと
# バックエンドの verifyWritable が `no such table: health_check` で落ちる。
# sqlite3def は入れていないので、テーブルが無いときだけ schema.sql を当てる。
DB_PATH="${DB_PATH:-./data/mastodon-rss.db}"
mkdir -p "$(dirname "$DB_PATH")"
if ! sqlite3 "$DB_PATH" "SELECT 1 FROM health_check LIMIT 1;" >/dev/null 2>&1; then
  sqlite3 "$DB_PATH" < backend/repository/src/main/resources/db/schema.sql
fi
