-- スキーマの唯一の定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
--
-- 手で編集しない。開発用 DB を直接いじって形を決めたら
--   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
-- で書き出して commit する。実 DB への適用は sqlite3def で手動。
-- 詳細は同じディレクトリの README.md を参照。
CREATE TABLE health_check (
    -- 起動時の書き込み確認に使うテーブル。
    -- 行は常に 1 件だけなので、CHECK で id を固定して UPSERT の対象を一意にする
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
