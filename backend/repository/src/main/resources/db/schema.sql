-- スキーマの唯一の定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
--
-- 手で編集しない。開発用 DB を直接いじって形を決めたら
--   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
-- で書き出して commit する。実 DB への適用は sqlite3def で手動。
-- 詳細は同じディレクトリの README.md を参照。
CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- acct とパスに入る名前。大文字小文字だけが違う名前を別々に持てると、
    -- 相手からはどちらを指しているか決まらないので NOCASE で一意にする
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    created_at TEXT NOT NULL
);

CREATE TABLE followers (
    -- 1 行が「username のアカウントを remote_actor_id がフォローしている」ことを表す
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- フォローされたこちらのアカウントの名前。accounts への外部キーにしないのは、
    -- ACTOR_USERNAME で決まる組み込みアカウントが accounts に行を持たないため。
    -- 引き当ての正は ActorDirectory で、こちらはその結果を名前で受ける
    username TEXT COLLATE NOCASE NOT NULL,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    -- 受け取った Follow の id。Accept を返し損ねると相手は同じ id で送り直してくるので、
    -- 一意にして二重に受けても行が増えないようにする
    follow_activity_uri TEXT NOT NULL UNIQUE,
    -- pending: Follow を受けたが Accept をまだ返せていない。accepted: 返せた
    state TEXT NOT NULL CHECK (state IN ('pending', 'accepted')),
    created_at TEXT NOT NULL,
    accepted_at TEXT,
    -- 同じ相手が二重にフォローしている状態を作らない。解除は行を消すので、
    -- 解除したあとの再フォローは別の Follow の id で新しい行になる
    UNIQUE (username, remote_actor_id)
);

CREATE TABLE health_check (
    -- 起動時の書き込み確認に使うテーブル。
    -- 行は常に 1 件だけなので、CHECK で id を固定して UPSERT の対象を一意にする
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);

CREATE TABLE notes (
    -- こちらから配信した投稿。相手がパーマリンクを引きに来るので、送ったものは残す
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 投稿したこちらのアカウントの名前。followers と同じ理由で外部キーにしない
    username TEXT COLLATE NOCASE NOT NULL,
    -- URL のパスに入る識別子。採番した id をそのまま出すと、
    -- 投稿の総数と作られた順が外から分かってしまう
    public_id TEXT NOT NULL UNIQUE,
    -- 配信した本文の HTML。サニタイズ済みのものを入れる
    content_html TEXT NOT NULL,
    -- 相手に見せる公開日時。並び順もこれで決まる
    published_at TEXT NOT NULL
);

CREATE TABLE remote_actors (
    -- 相手のサーバーのアクター。フォロワーの inbox と公開鍵の置き場
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 相手のアクター文書の URL。相手を指す唯一の識別子で、
    -- 署名の keyId からフラグメントを落とした形と一致する
    actor_uri TEXT NOT NULL UNIQUE,
    inbox TEXT NOT NULL,
    -- 同じインスタンス宛の配信をまとめる先。持たない実装があるので NULL を許す
    shared_inbox TEXT,
    public_key_pem TEXT NOT NULL,
    -- 最後にアクター文書を取り直した時刻。相手が鍵を替えると古い鍵では
    -- 検証が通らなくなるので、取り直す判断に使う
    fetched_at TEXT NOT NULL
);

CREATE INDEX notes_username_published_at ON notes (username, published_at);

CREATE TABLE feeds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE REFERENCES accounts (id) ON DELETE CASCADE,
    url TEXT NOT NULL UNIQUE,
    title TEXT,
    site_url TEXT,
    format TEXT,
    poll_interval_seconds INTEGER NOT NULL,
    etag TEXT,
    last_modified TEXT,
    last_fetched_at TEXT,
    last_succeeded_at TEXT,
    last_error TEXT,
    initial_import_done INTEGER NOT NULL DEFAULT 0 CHECK (initial_import_done IN (0, 1)),
    created_at TEXT NOT NULL
);
