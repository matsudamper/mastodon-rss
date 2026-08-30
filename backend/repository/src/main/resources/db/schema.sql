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

CREATE TABLE feed_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    feed_id INTEGER NOT NULL REFERENCES feeds (id) ON DELETE CASCADE,
    -- 記事を区別する鍵。`:backend:rss` の FeedItemKey.value。
    -- フィードの中で一意。フィードをまたぐと重なりうるので、一意制約は (feed_id, item_key)
    item_key TEXT NOT NULL,
    title TEXT,
    link TEXT,
    -- 投稿する本文。サニタイズ済みの HTML を取り込みの時点で作って持つ
    content_html TEXT,
    published_at TEXT,
    imported_at TEXT NOT NULL,
    -- pending: 投稿待ち / posted: 投稿済み / skipped: 投稿しない
    state TEXT NOT NULL CHECK (state IN ('pending', 'posted', 'skipped')),
    posted_at TEXT,
    -- 投稿したときに配信した notes.public_id。記事と投稿の紐付けはこの 1 列だけ。
    -- 記事を消しても投稿は残す（投稿は相手がパーマリンクを引きに来る）ので、
    -- 参照はこちらから持つ
    note_id TEXT REFERENCES notes (public_id) ON DELETE SET NULL,
    UNIQUE (feed_id, item_key)
);

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

CREATE TABLE followers (
    -- 1 行が「username のアカウントを remote_actor_id がフォローしている」ことを表す
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- フォローされたこちらのアカウントの名前。引き当ての正は ActorDirectory で、
    -- こちらはその結果を名前で受ける。アクター ID は名前から決まる。
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
    -- URL のパスに入る識別子。AUTOINCREMENT の id をそのまま出すと
    -- 投稿の総数が外から分かってしまう。UUID v7 は先頭 48 bit に生成時刻を
    -- 埋める。published_at も ActivityPub で公開するので順序の新規露出はない。
    -- INSERT 時の INDEX 局所性のため v7 を使う
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

CREATE INDEX feed_items_feed_id_state_published_at_id ON feed_items (feed_id, state, published_at, id);

CREATE INDEX feed_items_note_id ON feed_items (note_id);

CREATE INDEX notes_username_published_at ON notes (username, published_at);
