# mastodon-rss 開発ロードマップ

RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。
ライブラリに依存せず ActivityPub を自前実装する。

---

## 現在地と次の一手

現在地: Phase 0 の途中。`:backend` / `:frontend` の 2 モジュールに分割済み。
`:backend` は Ktor (CIO) の Hello World が JVM でも native-image でも動く。
`:frontend` は Compose Multiplatform for Web (Kotlin/Wasm) で Hello World を表示するところまで。
CI で backend / frontend / native-image の 3 ジョブが回っている。

次の一手: 以下の順で Phase 0 を閉じる。

| 順 | やること | なぜこの順か |
| --- | --- | --- |
| 0-1 | マルチモジュール化（完了） | あとから分割すると全ファイルが動くので最初にやる |
| 0-2 | `GET /healthz` | 生存確認の口がないと以降の CI 検証が書けない |
| 0-3 | kotlinx.serialization | DB の前に JSON を通しておくと healthz から検証できる |
| 0-4 | SQLite 接続 | native-image で最も割れやすい要素その1 |
| 0-5 | マイグレーション（自前連番 SQL） | jOOQ codegen の入力になるので codegen より先 |
| 0-6 | jOOQ codegen の Gradle タスク化 | native-image で最も割れやすい要素その2 |
| 0-7 | native-image 再検証 + CI 強化 | 0-4〜0-6 を積んだ状態で通ることが Phase 0 のゴール |

DB を ActivityPub (Phase 1) より先に入れるのは、native-image で壊れるとしたら
SQLite のネイティブライブラリと jOOQ のリフレクションが原因になる可能性が高く、
フェデレーションの実装が乗る前に潰しておきたいため。

## 使用技術

| 領域 | 技術 |
| --- | --- |
| 言語 | Kotlin |
| ランタイム | GraalVM (native-image) |
| HTTP サーバー | Ktor (CIO) |
| DB | SQLite |
| DB アクセス | jOOQ |
| UI | Compose Multiplatform for Web (Kotlin/Wasm) |

モジュールは `:backend`（サーバー）と `:frontend`（管理 UI）の 2 つ。
ビルド方法は [README.md](README.md) を参照。

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

設計上の中心的な決定: アクターの単位

- 案A: 1 フィード = 1 アクター（`@gihyo@example.com` のようにフィードごとにフォロー）
- 案B: 1 アカウントが全フィードを投稿（ハッシュタグで分ける）

→ 案A を採用する。 ただし Phase 1〜5 では「固定の 1 アクター」だけを作り、Phase 6 で複数化する。
最初から複数アクター対応にすると WebFinger・鍵管理・配信先解決が同時に複雑化して切り分けができなくなるため。

---

## Phase 0: 土台づくり（フェデレーションの話は一切しない）

GraalVM native-image は「あとで対応する」と致命傷になりやすいので、最初にすべての要素技術が
native-image で動くことだけを確認する。ここが一番の技術リスク。

- [x] Gradle + Kotlin JVM プロジェクトを作成
- [x] HTTP サーバーを選定 → Ktor (CIO engine) を採用
      - 候補は Ktor (CIO) / http4k / 素の `com.sun.net.httpserver` だった
      - native-image 実績と依存の軽さで Ktor (CIO) にした
- [x] CI（GitHub Actions）で JVM テスト + native-image ビルドを回す
- [x] JetBrains Compose のプロジェクト構成を決める → 案2（Compose HTML / Kotlin/Wasm）（下記「Compose の位置づけ」参照）
- [x] マイグレーション方式を決める → 自前の連番 SQL（Flyway は依存が重く native-image で追加対応が要るため見送り）

### 0-1. マルチモジュール化（完了）

- [x] `settings.gradle.kts` に `:backend` と `:frontend` を追加する
      - `:backend` — Ktor (CIO)、ルーティング、HTTP Signature、配信キュー、native-image ビルド
      - `:frontend` — Compose Multiplatform for Web (Kotlin/Wasm) の管理画面
- [x] 既存の `Application.kt` / `ApplicationTest.kt` を `:backend` に移す
- [x] `gradle/libs.versions.toml`（version catalog）に依存とバージョンを集約する
- [x] `graalvmNative` の設定を `:backend` に移し、ルートから `application` プラグインを外す
- [x] `./gradlew :backend:build` と `./gradlew :frontend:wasmJsBrowserDistribution` が通ることを確認する
- [x] CI を backend / frontend / native-image の 3 ジョブに分け、native バイナリのパスを追従させる
- [x] Compose の Kotlin/Wasm ビルド向けに `gradle.properties` でヒープを増やす
      - 既定値だと `compileProductionExecutableKotlinWasmJs` が OOM で落ちる
- [x] `:frontend` の dev サーバーのポートを 8081 にずらす（既定の 8080 は `:backend` と衝突する）

まだ切っていないモジュール（必要になった時点で追加する）:

- [ ] `:core` — ドメインモデル / DB アクセス (jOOQ) / ActivityPub の JSON モデル / RSS パーサ
      - いまは中身が無いので作っていない。0-4（SQLite）で `:backend` から切り出す
      - Kotlin JVM。Ktor に依存させない
- [ ] `:shared` — `:backend` と `:frontend` で共有する管理 API の DTO。KMP (`jvm` + `wasmJs`)
      - Phase 8 で管理 API を作るときに必要になる

依存とバージョンの現状:

- Kotlin 2.3.21 / Compose Multiplatform 1.11.1 / Ktor 3.5.2
- Compose 1.11.1 の klib は Kotlin 2.3.20 でビルドされているため、Kotlin は 2.3.20 以上が必須
- [ ] `compose.runtime` などの DSL ショートカットは deprecated 警告が出る
      - 移行先の `org.jetbrains.compose.*` 直接座標は 1.11.1 では未公開（material3 が alpha 止まり）
      - 1.12 系が安定したら直接座標に移行して version catalog に載せる

### 0-2. `GET /healthz`

- [x] `GET /healthz` が 200 と `{"status":"ok"}` を返す（JSON 化は 0-3 の後でよい）
- [x] `GET /` の Hello World を削除する（役目は終わり）
- [x] テストを追加する
- [x] CI の native 起動確認を `/` から `/healthz` に切り替える
- [x] ポートとバインドアドレスを環境変数で上書きできるようにする（`PORT` / `HOST`。デフォルトは `8080` / `0.0.0.0`）
      - native-image は起動時に環境変数を読む方が設定ファイルより素直

### 0-3. JSON シリアライザ（kotlinx.serialization）

リフレクション不使用（コンパイル時にシリアライザを生成する）ので native-image と相性が良い。

- [ ] `kotlin("plugin.serialization")` と `ktor-serialization-kotlinx-json` を入れ、`ContentNegotiation` を設定する
- [ ] `Json` の設定を決めて 1 箇所に集約する
      - `encodeDefaults = true`（ActivityPub は既定値の省略で相手側が転ぶことがある）
      - `explicitNulls = false`（`null` フィールドを出力しない）
      - `ignoreUnknownKeys = true`（受信側。相手の拡張プロパティで落ちないように）
- [ ] `/healthz` を JSON レスポンスに変える
- [ ] ActivityPub 向けの下ごしらえ（Phase 1 で効いてくるので、ここで型だけ用意しておく）
      - [ ] `@context` のような記号入りのキーは `@SerialName("@context")` で対応する
      - [ ] ActivityPub は「文字列 1 個」と「配列」のどちらも来るフィールドが多い（`@context`, `to`, `cc`, `type`）
            → 常に `List<String>` として扱い、単一文字列も配列に正規化するカスタム serializer を書く
      - [ ] `object` が「URL 文字列」と「埋め込みオブジェクト」の両方を取る箇所がある（`Undo`, `Accept`）
            → `JsonElement` で受けて分岐する型を用意する
      - [ ] Content-Type は Ktor 既定の `application/json` ではなく `application/activity+json` を返す必要がある
            → カスタム `ContentType` を定義して `respondText` / `respond` で明示する

### 0-4. SQLite 接続

- [ ] `org.xerial:sqlite-jdbc` を `:core`（未作成なら `:backend`）に入れ、テーブル作成 → INSERT → SELECT の疎通を通す
- [ ] 接続時に必ず入れる PRAGMA を 1 箇所にまとめる
      - `journal_mode=WAL`（読み書きの並行性。ただしファイル DB のみ有効）
      - `foreign_keys=ON`（SQLite は既定で OFF。忘れると外部キーが効かない）
      - `busy_timeout=5000`（`SQLITE_BUSY` の即時失敗を避ける）
      - `synchronous=NORMAL`（WAL 前提。耐久性と速度の折衷）
- [ ] コネクションの持ち方を決める
      - SQLite はライターが 1 本しか取れないので汎用プールは過剰
      - 読み取り用の複数接続 + 書き込み用の単一接続、あるいは全体を単一接続 + 直列化で始める
      - HikariCP は入れない方向で検討する（依存と native-image 設定を減らすため）
- [ ] DB ファイルのパスを環境変数で指定できるようにする（`DB_PATH`。デフォルトは `./data/mastodon-rss.db`）
- [ ] 親ディレクトリが無ければ起動時に作る

### 0-5. マイグレーション（自前の連番 SQL）

- [ ] `backend/src/main/resources/db/migration/V001__init.sql` の形式で SQL を置く
- [ ] `schema_version` テーブルで適用済みバージョンを管理する（`version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL`）
- [ ] 起動時に未適用のものをバージョン昇順で適用する。1 ファイル = 1 トランザクション
- [ ] 適用済みファイルの内容が変わっていないかチェックサムで検証する（任意。事故を早く見つけられる）
- [ ] マイグレーションファイルの列挙方法を native-image で動くやり方にする
      - jar 内リソースのディレクトリ走査は native-image では動かないことがある
      - → ファイル名の一覧を持つ `index` リソースを置くか、ビルド時にリストを生成するのが安全
- [ ] `resource-config.json`（または `nativeImageResources` 設定）にマイグレーション SQL を登録する
      - リソースは明示しないと native バイナリに入らない。ここは踏みやすい
- [ ] テスト: 一時ファイル DB に対して 2 回続けて適用しても壊れない（冪等である）ことを確認する
- [ ] テスト: 空の DB から最新まで適用できることを確認する

### 0-6. jOOQ のコード生成を Gradle タスク化

- [ ] codegen のパイプラインを組む
      1. 一時 SQLite ファイルを作る（`build/jooq/schema.db`）
      2. `db/migration` の SQL を順に適用する
      3. その DB を入力に jOOQ codegen を実行する
      4. 出力を `build/generated/jooq` に置き、`:core`（未作成なら `:backend`）の sourceSet に加える
- [ ] `compileKotlin` が codegen タスクに依存するようにする（初回ビルドで生成物が無くて落ちないように）
- [ ] マイグレーション SQL が変わったら codegen が再実行されるよう入力を宣言する（up-to-date チェックを効かせる）
- [ ] 生成コードは git 管理しない（`build/` 配下なので `.gitignore` 済み）
- [ ] jOOQ の SQLite dialect を使う（OSS 版で対応している）
- [ ] `nu.studer.jooq` プラグインを使うか、素の `JavaExec` で回すかを決める
      - プラグインは楽だが Gradle との相性問題を踏むことがある。素の `JavaExec` + `configuration` の方が読める場合もある
- [ ] jOOQ のログ設定を入れる（何もしないと起動時にバナーと警告が出る）

### 0-7. native-image ビルドを通す — ここが Phase 0 の本体

0-4〜0-6 を積んだ状態で native バイナリが動くことが Phase 0 のゴール。

- [x] Gradle プラグイン（`org.graalvm.buildtools.native`）を導入し、Hello World の `nativeCompile` と起動を確認
- [ ] sqlite-jdbc のネイティブライブラリ同梱を確認する
      - sqlite-jdbc は jar 内の `.so` を実行時にテンポラリへ展開して `System.load` する作りなので、native-image でそのまま動くとは限らない
      - 新しめのバージョンは `META-INF/native-image` に設定を同梱していることがある。まず素で試して、駄目なら JNI 設定を書く
      - 展開先が読めない環境向けに `org.sqlite.tmpdir` の指定が要るか確認する
- [ ] JCA（RSA / SHA-256）が native-image 上で動くことを確認する
      - Phase 1 の鍵生成と Phase 2 の署名で必須。ここで確認しておかないと Phase 2 で詰まる
      - 確認内容: `KeyPairGenerator.getInstance("RSA")` / `Signature.getInstance("SHA256withRSA")` / `MessageDigest.getInstance("SHA-256")`
      - `--enable-all-security-services` は現行の GraalVM では非推奨・削除されている。代替の設定方法を確認する
- [ ] jOOQ のリフレクション設定（`reflect-config.json`）を用意する
- [ ] GraalVM tracing agent (`-agentlib:native-image-agent`) を回す Gradle タスクを用意する
      - 設定を手で書くより、一度エージェントで収集してから削るほうが早い
      - 出力先は `server/src/main/resources/META-INF/native-image/`
- [ ] リフレクション/リソース設定はどこから来たものか分かるようコメントか README を添える

### 0-8. CI の強化

- [ ] native ジョブの起動確認を「`/healthz` が 200」＋「SQLite に書き込めて読み戻せる」まで広げる
      - 一時ディレクトリを `DB_PATH` に渡して起動 → 書き込みを叩く口を用意するか、起動時のマイグレーション成功をログで確認する
- [ ] 起動確認スクリプトで、サーバーが立たなかった場合にログを出して失敗させる（いまはループを抜けて `grep` で落ちるだけで原因が見えない）
- [ ] `kill` を `trap` で確実に行い、ジョブが残留プロセスで詰まらないようにする
- [ ] Kotlin のフォーマッタ（ktlint など）を入れるか決める。入れるならこのタイミング

### ✅ チェックポイント 0
ネイティブバイナリ 1 個を起動して `curl localhost:8080/healthz` が通り、SQLite に書き込める。
加えて、native バイナリ上で RSA 鍵ペア生成と SHA256withRSA 署名ができる。

> ### Compose の位置づけ（決定済み: 案2）
> Compose Desktop（Skiko / JVM）は GraalVM native-image では現実的に動かない。
> サーバーとUIを同一バイナリにする前提を維持するため、案2 を採用する。
> - 案1: サーバー = native-image バイナリ、管理UI = Compose Desktop の別アプリ（通常のJVM）。両者は HTTP API で通信。
> - 案2（採用）: 管理UI を Kotlin/Wasm の Compose で書き、サーバーが静的配信。単一バイナリを維持できる。
> - 案3: サーバーも JVM で動かし、native-image をやめる。
>
> 実装は Compose Multiplatform for Web（canvas 描画）を使う。
> DOM ベースの Compose HTML ではなく、Compose Desktop と同じ `androidx.compose.*` の
> API がそのまま使える方。`ComposeViewport` に描画する。
>
> 同一 Gradle プロジェクト内でモジュールを分ければ、UI とバックエンドは分離できる:
> - `:frontend`（Kotlin/Wasm, Compose）をビルドすると `.wasm` + JS + HTML が出る
> - それを `:backend` の `processResources` で `resources/static/` に取り込む
> - `:backend` は `staticResources("/admin", "static")` で配信する
> - 型の共有が必要になったら `:shared`（KMP: `jvm` + `wasmJs`）に管理 API の DTO を置く
>
> いまは `:frontend` を独立してビルド・起動できるところまで。
> `:backend` への静的配信の取り込みは Phase 8 でやる。

---

## Phase 1: 固定アクターが Mastodon から「見つかる」

ここが最初のフェデレーション検証ポイント。 署名も DB もまだ不要。静的な JSON を2つ返すだけ。

ActivityPub のアカウント発見は WebFinger → Actor の 2 ホップで行われる。

- [ ] RSA 2048bit の鍵ペアを 1 組生成し、PEM でファイル or 環境変数に保存（固定。ローテーションは考えない）
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
      - ドメインは早めに固定する。 アクター ID にドメインが焼き込まれ、Mastodon 側にキャッシュされるため
- [ ] `GET /.well-known/nodeinfo` + `/nodeinfo/2.1`（任意だが実装しておくと調査が楽）

### ✅ チェックポイント 1
Mastodon の検索窓に `@feed@example.com` と入力して、プロフィールカードが表示される。
（この時点ではフォローボタンを押しても成立しない。それが Phase 2）

> テスト時の注意
> Mastodon はリモートアクターを永続キャッシュする。開発中にアクターの内容や鍵を変えても即座には反映されない。
> 試行錯誤のたびに `feed1`, `feed2`, ... とユーザー名を変えるのが最も手戻りが少ない。
> 検証相手は自分で立てた Mastodon（docker compose）か、テスト用途を許容する小規模インスタンスを使うこと。

---

## Phase 2: フォローが成立する（HTTP Signatures）

ActivityPub のサーバー間通信は HTTP Signatures (draft-cavage-http-signatures) で認証する。
「受信の検証」と「送信の署名」の両方が必要。ここが実装の山場。

- [ ] `POST /users/feed/inbox` を受ける（まずは中身をログに落とすだけ）
- [ ] 署名の検証（受信）
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
- [ ] 署名の生成（送信） — 上記の逆。POST 時は `Digest` を必ず含める
- [ ] `Follow` アクティビティを受けたら `Accept` を相手の `inbox` に POST し返す
      - `Accept` の `object` には受信した Follow アクティビティを丸ごと入れる（id だけだと通らない実装がある）
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
- [ ] 初回登録時の暴発防止 — 既存記事を全部投稿しない。初回は「取り込み済み」としてマークするだけ
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

## Phase 8: 管理 UI（Compose Multiplatform for Web / Kotlin/Wasm）

サーバーが完成してから作る。UI が先だとフェデレーションのデバッグができない。
`:frontend` モジュールと Hello World は 0-1 で作成済み。

- [x] `:frontend` モジュール（Kotlin/Wasm + Compose）を作り、Hello World を表示する
- [ ] `:shared` モジュール（KMP: `jvm` + `wasmJs`）を作り、管理 API の DTO を置く
- [ ] `:frontend` のビルド成果物を `:backend` の resources に取り込むタスクを組む
      - `:backend:processResources` が `:frontend:wasmJsBrowserDistribution` に依存するようにする
      - `.wasm` を含むリソースが native バイナリに入ることを確認する（`resource-config.json`）
      - `.wasm` の Content-Type が `application/wasm` で返ることを確認する（Ktor の既定に無い可能性がある）
- [ ] サーバー側に管理 API（フィード CRUD、アクター一覧、配信状況、手動再取得）
- [ ] 管理 API に認証をかける（Basic 認証かトークン。inbox と違って外に開けてはいけない）
- [ ] 開発時は frontend の dev サーバー (8081) から backend (8080) を叩くので CORS か proxy 設定が要る
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
      - Phase 1 に入る前に必須。これが決まらないと WebFinger も Actor JSON も書けない
- [ ] アクターの `type`: `Service` を推奨（bot 表示になる）。`Person` だと人間アカウントに見える
- [ ] WebFinger の acct ドメインと Actor URL のホストを揃えるか、`host-meta` でリダイレクトするか
- [ ] 検証用 Mastodon をどう用意するか（docker compose でローカルに立てるのが安全）
- [x] Compose の位置づけ → 案2: Compose Multiplatform for Web (Kotlin/Wasm) を `:backend` が静的配信
- [x] マイグレーション方式 → 自前の連番 SQL（`schema_version` テーブルで管理）

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
| native バイナリでマイグレーションが動かない | SQL がリソースとして同梱されていない（`resource-config.json` 未登録）/ jar 内ディレクトリ走査に頼っている |
| 外部キー制約が効かない | SQLite は `PRAGMA foreign_keys` が既定で OFF。接続ごとに ON にする必要がある |
| `SQLITE_BUSY` が出る | ライターを複数持っている / `busy_timeout` 未設定 |

## 参考仕様

- ActivityPub — https://www.w3.org/TR/activitypub/
- Activity Streams 2.0 / Vocabulary — https://www.w3.org/TR/activitystreams-core/
- WebFinger (RFC 7033) — https://datatracker.ietf.org/doc/html/rfc7033
- HTTP Signatures (draft-cavage-http-signatures-12) — Mastodon が実装しているのはこのドラフト版
- Mastodon 実装ドキュメント — https://docs.joinmastodon.org/spec/activitypub/
