-- 起動時の書き込み確認に使うテーブル。
-- 行は常に 1 件だけなので、CHECK で id を固定して UPSERT の対象を一意にする。
CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
