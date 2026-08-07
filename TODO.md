# mastodon-rss 開発ロードマップ

RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。
ライブラリに依存せず ActivityPub を自前実装する。

## 使用技術

| 領域 | 技術 |
| --- | --- |
| 言語 | Kotlin |
| ランタイム | GraalVM (native-image) |
| DB | SQLite |
| DB アクセス | jOOQ |
| UI | JetBrains Compose |

---

## 全体像

```
[RSS/Atom] --fetch--> [取り込み/差分検出] --> [SQLite]
                                               |
                                        [配信キュー]
                                               |
                        HTTP Signature で署名した POST
                                               |
                                               v
                                  [Mastodon 等の inbox]

Mastodon --WebFinger--> /.well-known/webfinger
Mastodon --GET--------> /users/{name}          (Actor JSON)
Mastodon --POST(署名)--> /users/{name}/inbox    (Follow / Undo / Delete)
```

**設計上の中心的な決定: アクターの単位**

- 案A: 1 フィード = 1 アクター（`@gihyo@example.com` のようにフィードごとにフォロー）
- 案B: 1 アカウントが全フィードを投稿（ハッシュタグで分ける）

→ **案A を採用する。** ただし Phase 1〜5 では「固定の 1 アクター」だけを作り、Phase 6 で複数化する。
最初から複数アクター対応にすると WebFinger・鍵管理・配信先解決が同時に複雑化して切り分けができなくなるため。

---

## Phase 0: 土台づくり（フェデレーションの話は一切しない）

GraalVM native-image は「あとで対応する」と致命傷になりやすいので、**最初にすべての要素技術が
native-image で動くことだけを確認する**。ここが一番の技術リスク。

- [x] Gradle + Kotlin JVM プロジェクトを作成
- [ ] HTTP サーバーを選定して `GET /healthz` が 200 を返す
      - 候補: Ktor (CIO engine) / http4k / 素の `com.sun.net.httpserver`
      - native-image 実績と依存の軽さで選ぶ
      - Ktor (CIO) を選定。`GET /` で Hello World を返すところまで実装済み。`/healthz` 自体は未実装
- [ ] JSON シリアライザを導入（kotlinx.serialization 推奨。リフレクション不使用で native-image と相性が良い）
- [ ] SQLite 接続（xerial sqlite-jdbc）でテーブル作成 → INSERT → SELECT
- [ ] jOOQ のコード生成を Gradle タスク化（SQLite スキーマ → 生成クラス）
      - マイグレーション（Flyway か自前の連番 SQL）でスキーマを作り、それを jOOQ codegen の入力にする
- [ ] JetBrains Compose のプロジェクト構成を決める（下記「Compose の位置づけ」参照）
- [ ] **native-image ビルドを通す** — ここが Phase 0 の本体
      - [ ] sqlite-jdbc のネイティブライブラリ同梱を確認
      - [ ] JCA（RSA / SHA-256）が native-image 上で動くことを確認（`java.security` 系の設定が要る場合あり）
      - [ ] jOOQ のリフレクション設定（`reflect-config.json`）を用意
      - [ ] 必要なら GraalVM tracing agent (`-agentlib:native-image-agent`) で設定を自動収集
      - Gradle プラグイン（`org.graalvm.buildtools.native`）導入済み。CI 上で `nativeCompile` が通り、Hello World の起動確認も成功済み
- [x] CI（GitHub Actions）で JVM テスト + native-image ビルドを回す

### ✅ チェックポイント 0
ネイティブバイナリ 1 個を起動して `curl localhost:8080/healthz` が通り、SQLite に書き込める。

> ### Compose の位置づけ（Phase 0 で決めておく）
> **Compose Desktop（Skiko / JVM）は GraalVM native-image では現実的に動かない。**
> サーバーとUIを同一バイナリにする前提は捨て、以下のどれかを選ぶ:
> - **案1（推奨）**: サーバー = native-image バイナリ、管理UI = Compose Desktop の別アプリ（通常のJVM）。両者は HTTP API で通信。
> - 案2: 管理UI を Compose HTML (Kotlin/Wasm または Kotlin/JS) で書き、サーバーが静的配信。単一バイナリを維持できる。
> - 案3: サーバーも JVM で動かし、native-image をやめる。
>
> この判断はレイヤ分割（`:core` / `:server` / `:ui`）に直結するので、Phase 0 で決めてマルチモジュール構成に落とす。

---

## Phase 1: 固定アクターが Mastodon から「見つかる」

**ここが最初のフェデレーション検証ポイント。** 署名も DB もまだ不要。静的な JSON を2つ返すだけ。

ActivityPub のアカウント発見は WebFinger → Actor の 2 ホップで行われる。

- [ ] RSA 2048bit の鍵ペアを 1 組生成し、PEM でファイル or 環境変数に保存（**固定**。ローテーションは考えない）
      - 秘密鍵: PKCS#8 (`BEGIN PRIVATE KEY`)
      - 公開鍵: X.509 SubjectPublicKeyInfo (`BEGIN PUBLIC KEY`) ← Actor JSON にはこちらを入れる
- [ ] `GET /.well-known/webfinger?resource=acct:feed@example.com`
      - Content-Type: `application/jrd+json`
      - `subject` はリクエストされた `acct:` をそのまま返す
      - `links` に `rel: "self"`, `type: "application/activity+json"`, `href: <Actor の URL>`
      - 未知の resource は 404
- [ ] `GET /users/feed`（Actor エンドポイント）
      - Content-Type: `application/activity+json`
      - `@context`: `["https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"]`
      - `id` / `type: "Service"` / `preferredUsername` / `name` / `summary`
      - `inbox` / `outbox` / `followers` / `following`
      - `publicKey`: `{ id: "<actor>#main-key", owner: "<actor>", publicKeyPem: "..." }`
      - `preferredUsername` は WebFinger の acct 名と一致させること
- [ ] `Accept` ヘッダで content negotiation（`application/activity+json` と `application/ld+json` を受ける）
- [ ] HTTPS で外部公開する経路を用意（開発中は Cloudflare Tunnel / ngrok など）
      - **ドメインは早めに固定する。** アクター ID にドメインが焼き込まれ、Mastodon 側にキャッシュされるため
- [ ] `GET /.well-known/nodeinfo` + `/nodeinfo/2.1`（任意だが実装しておくと調査が楽）

### ✅ チェックポイント 1
Mastodon の検索窓に `@feed@example.com` と入力して、プロフィールカードが表示される。
（この時点ではフォローボタンを押しても成立しない。それが Phase 2）

> **テスト時の注意**
> Mastodon はリモートアクターを永続キャッシュする。開発中にアクターの内容や鍵を変えても即座には反映されない。
> 試行錯誤のたびに `feed1`, `feed2`, ... とユーザー名を変えるのが最も手戻りが少ない。
> 検証相手は自分で立てた Mastodon（docker compose）か、テスト用途を許容する小規模インスタンスを使うこと。

---

## Phase 2: フォローが成立する（HTTP Signatures）

ActivityPub のサーバー間通信は **HTTP Signatures (draft-cavage-http-signatures)** で認証する。
「受信の検証」と「送信の署名」の両方が必要。ここが実装の山場。

- [ ] `POST /users/feed/inbox` を受ける（まずは中身をログに落とすだけ）
- [ ] **署名の検証（受信）**
      - [ ] `Digest: SHA-256=<base64>` ヘッダとボディの SHA-256 を突き合わせる
      - [ ] `Signature` ヘッダをパース（`keyId`, `algorithm`, `headers`, `signature`）
      - [ ] `keyId`（例: `https://mastodon.social/users/foo#main-key`）のアクターを GET して公開鍵を取得
      - [ ] `headers` の並び順どおりに署名文字列を再構築
            - `(request-target): post /users/feed/inbox`
            - `host: example.com`
            - `date: ...`
            - `digest: ...`
      - [ ] RSA-SHA256 で検証
      - [ ] `Date` のずれが大きいリクエストは拒否（リプレイ対策）
      - [ ] 検証失敗は 401、成功は 202 Accepted を返す
- [ ] **署名の生成（送信）** — 上記の逆。POST 時は `Digest` を必ず含める
- [ ] `Follow` アクティビティを受けたら `Accept` を相手の `inbox` に POST し返す
      - `Accept` の `object` には受信した Follow アクティビティを**丸ごと**入れる（id だけだと通らない実装がある）
      - `Accept` 自身にもユニークな `id` を振る
- [ ] リモートアクターの取得結果をキャッシュ（毎回 GET しない）
- [ ] 送信 GET にも署名を付ける
      - Mastodon の `AUTHORIZED_FETCH`（secure mode）が有効なインスタンスは無署名 GET を拒否する

### ✅ チェックポイント 2
Mastodon からフォローボタンを押す → 数秒後に「フォロー中」で確定する（保留のまま戻らない）。

---

## Phase 3: フォロワーを永続化する

Phase 2 まではオンメモリでよい。ここで初めて DB が要る。

- [ ] スキーマ設計とマイグレーション
      - `actors`（ローカルアクター: name, display_name, private_key, public_key, created_at）
      - `remote_actors`（inbox, shared_inbox, public_key, fetched_at）
      - `followers`（actor_id, remote_actor_id, follow_activity_id, state, created_at）
      - `deliveries`（配信キュー: target_inbox, payload, attempts, next_retry_at, state）
- [ ] jOOQ 経由で follow を INSERT / UPDATE
- [ ] `Undo{Follow}` を処理してフォロー解除
- [ ] `Delete{Actor}`（アカウント削除・引っ越し）を処理してフォロワーを掃除
      - 削除済みアクターは鍵を取得できないので、署名検証に失敗しても握り潰す例外パスが要る
- [ ] `GET /users/feed/followers`（OrderedCollection、ページング）
- [ ] 冪等性: 同じ `Follow` を二重に受けても重複行を作らない（activity id で一意制約）

### ✅ チェックポイント 3
プロセスを再起動してもフォロワー数が保持される。アンフォローすると減る。

---

## Phase 4: 1件の投稿がタイムラインに流れる

RSS はまだ絡めない。手動トリガーで固定文字列を投稿する。

- [ ] `Note` オブジェクトの生成
      - `id` / `type: "Note"` / `attributedTo` / `content`（HTML）/ `published`（ISO 8601）
      - `to: ["https://www.w3.org/ns/activitystreams#Public"]`
      - `cc: ["<actor>/followers"]`
      - リンクは `<a href="...">` として `content` に埋める
- [ ] `Create` アクティビティで包んで全フォロワーの inbox に POST
- [ ] `sharedInbox` があればそちらにまとめて送る（同一インスタンス宛の重複配信を避ける）
- [ ] 配信キュー: 失敗時に指数バックオフでリトライ、上限到達で諦める
- [ ] `GET /users/feed/outbox`（OrderedCollection）
- [ ] `GET /notes/{id}` で単体の Note を返す（Mastodon がパーマリンクを引きに来る）
- [ ] 投稿を発火させる管理用エンドポイント（開発用。あとで UI から叩く）

### ✅ チェックポイント 4
フォロワーのホームタイムラインに投稿が現れ、リンクをクリックできる。

---

## Phase 5: RSS を取り込んで自動投稿する

ここでようやく本来の機能。ActivityPub 側はもう触らない。

- [ ] RSS 2.0 / Atom 1.0 のパーサを自作（`javax.xml` の StAX か DOM）
      - 両フォーマットの差分吸収（`item`/`entry`, `pubDate`/`updated`, `description`/`summary`/`content`）
- [ ] `feeds` / `feed_items` テーブル
- [ ] 差分検出: `guid` / `id` / `link` を主キーに、なければ URL + タイトルのハッシュ
- [ ] 条件付き GET（`ETag` / `If-Modified-Since`）でフィード配信元に優しくする
- [ ] スケジューラ（定期ポーリング）。フィードごとに間隔を設定可能に
- [ ] **初回登録時の暴発防止** — 既存記事を全部投稿しない。初回は「取り込み済み」としてマークするだけ
- [ ] HTML サニタイズ（Mastodon が許可するタグに絞る。`<p> <br> <a> <span>` 程度）
- [ ] 本文の長さ調整（インスタンスによっては 500 文字制限。タイトル + リンクを基本形に）
- [ ] 取得失敗・パース失敗時のエラーハンドリングとログ

### ✅ チェックポイント 5
実在の RSS を登録して放置し、新着記事が自動でタイムラインに流れる。

---

## Phase 6: 複数アクター（フィードごとのアカウント）

Phase 1 で固定していた部分を動的にする。

- [ ] アクターを DB 駆動に変更（起動時ハードコードをやめる）
- [ ] WebFinger を動的解決（任意の `acct:` を DB 引きして応答）
- [ ] アクターごとに鍵ペアを生成して保存
- [ ] アクター作成 / 削除の API
- [ ] アクター情報更新時に `Update{Actor}` を配信（アイコン・説明文の変更を伝播させる）
- [ ] アイコン / ヘッダー画像（`icon` / `image`）の配信

### ✅ チェックポイント 6
新しいフィードを登録すると、そのフィード専用のアカウントが Mastodon から検索・フォローできる。

---

## Phase 7: 運用に耐えるようにする

- [ ] 配信の並列化とインスタンス単位のレート制限
- [ ] 到達不能インスタンスの検出とバックオフ / 自動停止
- [ ] `410 Gone` / `404` を返す inbox のフォロワーを整理
- [ ] ログ・メトリクス（配信成功率、キュー滞留、フィード取得失敗）
- [ ] `robots.txt`、リクエストサイズ上限、inbox のレート制限（DoS 対策）
- [ ] 秘密鍵の保管方法を見直す（ファイルパーミッション / 暗号化）
- [ ] バックアップ（SQLite のファイルコピー + WAL の扱い）
- [ ] 未対応アクティビティ（`Like`, `Announce`, `Create{Note}` の返信など）を安全に無視する

---

## Phase 8: 管理 UI（JetBrains Compose）

サーバーが完成してから作る。UI が先だとフェデレーションのデバッグができない。

- [ ] サーバー側に管理 API（フィード CRUD、アクター一覧、配信状況、手動再取得）
- [ ] Compose でフィード一覧 / 追加 / 削除
- [ ] アクターごとのフォロワー数・最終投稿・配信エラーの表示
- [ ] フィードのプレビュー（投稿前にどう見えるか）
- [ ] 手動投稿・再配信のトリガー

---

## Phase 9: リリース

- [ ] native-image のビルドを本番向けに最適化（PGO、`--gc=G1` など）
- [ ] 設定ファイルの外出し（ドメイン、DB パス、ポート、ポーリング間隔）
- [ ] systemd unit / Dockerfile
- [ ] セットアップ手順の README
- [ ] リバースプロキシ設定例（nginx / Caddy）

---

## 事前に決めておくこと

- [ ] 本番ドメイン（アクター ID に焼き込まれ、後から変えられない）
- [ ] アクターの `type`: `Service` を推奨（bot 表示になる）。`Person` だと人間アカウントに見える
- [ ] WebFinger の acct ドメインと Actor URL のホストを揃えるか、`host-meta` でリダイレクトするか
- [ ] 検証用 Mastodon をどう用意するか（docker compose でローカルに立てるのが安全）

## つまずきやすい点（先に知っておく）

| 症状 | 原因になりやすいもの |
| --- | --- |
| 検索しても出てこない | `Content-Type` が `application/activity+json` でない / WebFinger の `subject` 不一致 |
| フォローが保留のまま | `Accept` を返していない / `Accept` の `object` が id だけ |
| 署名検証に失敗する | 署名文字列の再構築時の改行・小文字化・ヘッダー順序 |
| 相手から 401 が返る | `Digest` ヘッダ未送信 / `Date` のずれ / secure mode で GET に署名がない |
| 投稿が届かない | `to` に Public が入っていない / `cc` に followers がない |
| アクターを直しても反映されない | Mastodon 側のキャッシュ（ユーザー名を変えて試す） |
| native-image で落ちる | JCA・SQLite ネイティブライブラリ・jOOQ のリフレクション設定不足 |

## 参考仕様

- ActivityPub — https://www.w3.org/TR/activitypub/
- Activity Streams 2.0 / Vocabulary — https://www.w3.org/TR/activitystreams-core/
- WebFinger (RFC 7033) — https://datatracker.ietf.org/doc/html/rfc7033
- HTTP Signatures (draft-cavage-http-signatures-12) — Mastodon が実装しているのはこのドラフト版
- Mastodon 実装ドキュメント — https://docs.joinmastodon.org/spec/activitypub/
