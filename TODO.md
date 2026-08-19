# mastodon-rss 開発ロードマップ

RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。
ライブラリに依存せず ActivityPub を自前実装する。

ここに書くのはこれからやることと、その順番の理由。終わったフェーズは
「これまでにやったこと」に要約だけ残す。フェーズの流れに乗せない、いつでもいいものは
[GitHub の Issue](https://github.com/matsudamper/mastodon-rss/issues) に置く。実装の詳細は書かない。横断的な設計は
[docs/architecture.md](docs/architecture.md)、外部への応答は
[docs/mastodon-spec.md](docs/mastodon-spec.md)、ビルドと環境変数は [README.md](README.md)、
native-image で踏んだことは `META-INF/native-image/` の README にある。

---

## 現在地と次の一手

次の一手: Phase 3 のスキーマ設計とフォロワーの永続化。

- Phase 0（土台づくり）完了。`:backend` / `:backend:feature-mastodon` / `:backend:crypto` /
  `:backend:repository` / `:backend:rss` / `:backend:graphql` / `:frontend` のモジュール構成で、
  native バイナリが起動して SQLite に読み書きできる
- Phase 1（アクターの発見）完了。`social-rss.matsudamper.net` で WebFinger と Actor を公開している
- Phase 2（フォローの成立）完了。inbox の署名を検証し、`Follow` に `Accept` を返す。
  フォロワーの記録はまだしないので、`Undo` が届いても何もしない（Phase 3）
- Phase 5 のうちフィードの解析（`:backend:rss`）だけ先に実装した。取得（HTTP）と保存（DB）は
  繋いでいないので、まだ何も流れない。ActivityPub 側と独立していて後戻りが出ないため前倒しした
- Phase 6 と Phase 8 の一部（管理画面の枠、GraphQL の口とログイン、アカウントの追加と一覧）も
  先に入っている。どこまで入っているかは各フェーズの項目を参照

## 使用技術

| 領域 | 技術 |
| --- | --- |
| 言語 | Kotlin（整形は ktlint / `ktlint_official`） |
| ランタイム | GraalVM (native-image) |
| HTTP サーバー | Ktor (CIO) |
| DB | SQLite |
| DB アクセス | jOOQ（`schema.sql` から codegen。実 DB への適用は sqlite3def で手動） |
| 署名 | JCA（RSA / SHA256withRSA）。ライブラリは足さない |
| UI | Compose Multiplatform for Web (Kotlin/Wasm) |
| 管理 API | GraphQL。スキーマ優先。サーバーは kobylynskyi の codegen + graphql-java-tools、クライアントは Apollo Kotlin |
| 画面遷移 | Navigation Compose 3（JetBrains 版） |

管理 API を GraphQL にするのは [kake-bo](https://github.com/matsudamper/kake-bo) と
揃えるため。ActivityPub 側は相手の実装が決まっているので REST のまま。

モジュールの分け方と、それぞれに何を入れるかは
[docs/architecture.md](docs/architecture.md) を参照。ビルド方法は [README.md](README.md)。

依存の追従で残っているもの:

- [ ] `compose.runtime` などの DSL ショートカットは deprecated 警告が出る
      - 移行先の `org.jetbrains.compose.*` 直接座標は 1.11.1 では未公開（material3 が alpha 止まり）
      - 1.12 系が安定したら直接座標に移行して version catalog に載せる

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

アクターは 2 種類になる。

| 種類 | 例 | 役割 |
| --- | --- | --- |
| 運用者用 | `@admin@example.com` | 運用者のアカウント。フィードとは紐付けず、記事は流さない |
| フィード用 | `@gihyo@example.com` | フィード 1 本ぶんの記事を流す。`feeds` と 1:1 |

`admin` のいまの用途は告知だが、フィードを流さないアカウントという点が本質で、
用途はそれに限らない。フィード用アクターのプロフィールには `admin` へのリンクを置き、
問い合わせ先が分かるようにする。

---

## これまでにやったこと

- Phase 0: native バイナリでサーバーを起動し、SQLite、RSA 署名、RSS 解析を利用できる土台を作った
- Phase 1: WebFinger と Actor を公開し、Mastodon からアクターを発見できるようにした
- Phase 2: inbox の HTTP Signature を検証し、`Follow` に `Accept` を返せるようにした
  - [ ] `AUTHORIZED_FETCH` が有効な Mastodon からはまだフォローできない。無署名の GET を
        拒否され、相手の鍵と inbox が取得できないため
        （[送信 GET にも HTTP Signature を付ける #62](https://github.com/matsudamper/mastodon-rss/issues/62)）
- Phase 3 の準備として jOOQ を採用し、`schema.sql` をスキーマの正にした

## Phase 3: フォロワーを永続化する

Phase 2 まではオンメモリでよい。ここで初めて DB が要る。

- [ ] スキーマ設計（開発用 DB で形を決めて `dumpSchema` で `schema.sql` に書き出す）
      - `actors`（ローカルアクター: name, display_name, private_key, public_key, created_at）
      - `remote_actors`（inbox, shared_inbox, public_key, fetched_at）
      - `followers`（actor_id, remote_actor_id, follow_activity_id, state, created_at）
      - `deliveries`（配信キュー: target_inbox, payload, attempts, next_retry_at, state）
- [ ] follow を INSERT / UPDATE（jOOQ の DSL で）
- [ ] `Undo{Follow}` を処理してフォロー解除
- [ ] `Delete{Actor}`（アカウント削除・引っ越し）を処理してフォロワーを掃除
      - 削除済みアクターは鍵を取得できないので、署名検証に失敗しても握り潰す例外パスが要る
- [ ] `GET /users/admin/followers`（OrderedCollection、ページング）
- [ ] 冪等性: 同じ `Follow` を二重に受けても重複行を作らない（activity id で一意制約）
- [ ] フォロワーがいるなら鍵の自動生成を拒否して起動を止める
      - Phase 1 の鍵の生成条件は「ファイルが無い」だけなので、鍵を失った状態でも
        新しい鍵を作って何事もなく起動する。このときアクターは相手から見て別人になり、
        既存のフォロワーへの署名が全部通らなくなる。いまは警告ログを出すだけ
      - 既定値のままなら実害は小さい。`DB_PATH` も `ACTOR_PRIVATE_KEY_PATH` も
        `./data` 配下で、docker compose では同じボリュームなので、鍵を失うときは
        フォロワーごと失っている。問題は 2 つが独立した環境変数で別々の場所を
        指せることで、DB は残して鍵だけ失う構成が作れてしまう
      - フォロワーを保存するまでは判定材料が無いのでここで入れる。
        `followers` が空でなければ生成せずに落とす
      - 上の `actors` テーブルは `private_key` を持つ設計になっている。鍵の置き場を
        ファイルから DB に移すなら、この項目は「移行時に鍵を引き継ぐ」に変わる。
        どちらにするかは Phase 6 の複数アクター化と合わせて決める

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
- [ ] `GET /users/admin/outbox`（OrderedCollection）
- [ ] `GET /notes/{id}` で単体の Note を返す（Mastodon がパーマリンクを引きに来る）
- [ ] 投稿を発火させる管理用画面

### ✅ チェックポイント 4
フォロワーのホームタイムラインに投稿が現れ、リンクをクリックできる。

---

## Phase 5: RSS を取り込んで自動投稿する

ここでようやく本来の機能。ActivityPub 側はもう触らない。

フィードを読む部分（`:backend:rss`）だけ先に実装した。取得と保存は繋いでいないので、
まだ何も流れない。どこまでやったかは各項目の下に書いた。

- [x] RSS 2.0 / RSS 1.0 (RDF) / Atom 1.0 のパーサを自作（StAX。`FeedParser`）
      - 外部エンティティと DTD は切ってある。入口はバイト列で、文字コードは XML 宣言と
        BOM から判定させる（先に String にすると Shift_JIS の配信元で文字が壊れる）
      - 日時は RFC 822 と RFC 3339 に加えて崩れた形も読む（`FeedDates`）。
        読めなければ null にして記事は捨てない
      - [ ] 繋ぐときに `:backend` の native-image へ `-H:+AddAllCharsets` を足す
            - native バイナリには既定で一部の文字コードしか入らず、Shift_JIS の
              フィードを読んだ時点で `UnsupportedCharsetException` になる
            - `:backend:rss` の `nativeTest` には指定済み。これが無いと Shift_JIS の
              テストが native でだけ落ちることを確認している（そうやって見つけた）
- [ ] `feeds` / `feed_items` テーブル
      - この時点ではアクターが 1 つしか無いので、検証はフィード 1 本で行う。
        `ACTOR_USERNAME` にそのフィード用の名前を入れて動かす
      - `admin` から記事を流さないこと。`admin` は運用者のアカウントで、
        ここで記事を流すとフォロワーが付き、Phase 6 で分割したときに
        その人たちには何も届かなくなる（1 アカウントから複数への分割は
        `Move` では表現できず、引っ越しを通知する手段が無い）
      - 複数フィードを同時に動かせるようになるのは Phase 6。
        `feeds.actor_id` を足してフィードごとのアクターに振り分ける
      - `FeedRepository` と `FeedItemRepository` を interface だけ先に置いた
        （`:backend:repository`）。テーブルがまだ `schema.sql` に無く、
        テーブルが無ければ jOOQ の生成物も無いので実装は書けない。
        `Repositories` からも取れない
- [x] 差分検出: `guid` / `id` / `link` を主キーに、なければ URL + タイトルのハッシュ
      - `FeedItemKey`。優先順は `id`（`guid` / Atom の `id` / `rdf:about`）→ `link` → ハッシュ
      - 保存側の突き合わせは `FeedItemRepository.findExistingKeys` に置いた（実装は未定）
- [ ] 条件付き GET（`ETag` / `If-Modified-Since`）でフィード配信元に優しくする
      - 保存する値の形（`FeedFetchValidators`）と、記録する口だけ interface に置いた。
        送るのは HTTP クライアントを持つ `:backend` 側の仕事なので未実装
- [ ] スケジューラ（定期ポーリング）。フィードごとに間隔を設定可能に
      - 間隔を持つ場所（`Feed.pollIntervalSeconds`）と、対象を引く口
        （`FeedRepository.findDue`）は interface に置いた。回す部分は未実装
- [ ] 初回登録時の暴発防止 — 既存記事を全部投稿しない。初回は「取り込み済み」としてマークするだけ
      - 記録する場所（`Feed.initialImportDone` と `FeedItemState.SKIPPED`）だけ用意した。
        判断する処理は取り込みを書くときに入れる
- [x] HTML サニタイズ（Mastodon が許可するタグに絞る）
      - `HtmlSanitizer`。許可したタグと属性以外を落とし、`href` はスキームも見る。
        閉じられていないタグは末尾で閉じる（壊れた入れ子をそのまま流すと受信側の表示が崩れる）
- [ ] 本文の長さ調整（インスタンスによっては 500 文字制限。タイトル + リンクを基本形に）
      - 切り詰め（`FeedText.truncate`。コードポイント単位で切り、単語の途中なら空白まで戻す）
        は用意した。投稿の本文をどう組み立てるかは Phase 4 の `Note` を書くときに決める
- [ ] 取得失敗・パース失敗時のエラーハンドリングとログ
      - パースの失敗は `FeedParseException` にした。取得の失敗と、失敗をどう記録して
        どこに出すかは未実装（記録する口は `FeedRepository.recordFetchFailure`）

### ✅ チェックポイント 5
実在の RSS を登録して放置し、新着記事が自動でタイムラインに流れる。

---

## Phase 6: 複数アクター（フィードごとのアカウント）

「全体像」の案A をここで実現する。RSS フィード 1 本につきアカウントを 1 つ作り、
利用者は読みたいフィードのアカウントだけをフォローする。Phase 1 で固定していた部分を動的にする。

Phase 1〜5 で作った `admin` はフィード用ではなく、運用者のアカウントとして残す。
記事は流さない。いまの用途はサービスの状況やメンテナンスの告知。
フィード用アカウントは `feeds` に紐付いた別のアクターとして作る。

> 用途例（実装の予定は無い）
> 運用者のアカウントなのでメンションを受け取れる。`@admin@example.com` に
> RSS の URL を送ると購読が追加される、といった入口にもできる。やるとしたら
> inbox で `Create{Note}` を処理することになるが、いまは考えないでおく。

- [ ] `feeds` とアクターの対応を決める
      - `feeds.actor_id` で 1:1 に紐付ける。フィードを登録したらアクターを 1 つ作り、
        フィードを消したらそのアクターも消す
      - ユーザー名の決め方: フィードの URL やタイトルから作ると衝突するし、
        後から変えられない（相手側にキャッシュされる）。登録時に明示的に指定させる
      - `accounts` テーブルは先に作った。`feeds` から参照するときに
        `accounts.id` を外部キーにする
      - `ACTOR_USERNAME` と同じ名前、既にある名前は追加時に弾いている
- [x] アクターを DB 駆動に変更（起動時ハードコードをやめる）
      - `accounts` テーブルを引く（`StoredActorNames`）。`ACTOR_USERNAME` のアカウントは
        設定のまま残していて、管理画面からは消せない
- [x] WebFinger を動的解決（任意の `acct:` を DB 引きして応答）
      - `ActorDirectory` の 1 か所を通すので、パスの `{username}` と一緒に動的になった
- [ ] アクターごとに鍵ペアを生成して保存
      - Phase 1 の鍵はファイル 1 本。ここで `actors.private_key` に移すかを決める
        （Phase 3 の「フォロワーがいるなら鍵の自動生成を拒否する」と合わせて判断する）
- [ ] アクター作成 / 削除の API
      - 作成は入れた（`Mutation.admin.addAccount`）。一覧は `Query.admin.adminAccounts`。
        どちらもログインが要る
      - 削除はまだ。`Delete{Actor}` を配信してから消す。黙って消すと相手側に残り続ける
- [ ] アクター情報更新時に `Update{Actor}` を配信（アイコン・説明文の変更を伝播させる）
- [ ] アイコン / ヘッダー画像（`icon` / `image`）の配信
- [ ] フィードアクターのプロフィールに `admin` へのリンクを置く
      - Mastodon がプロフィールに出す「リンク集」は Actor JSON の `attachment`。
        `{ "type": "PropertyValue", "name": "管理", "value": "<a href=\"...\">@admin@example.com</a>" }`
        の配列で、`value` は HTML を入れる
      - フィードのアカウントだけを見た人が、どこに問い合わせればいいか分かるようにする
      - フィードの配信元 URL も同じ `attachment` に並べると分かりやすい
      - `attachment` を変えたら `Update{Actor}` を配信しないと相手側の表示が古いまま
- [ ] 配信はアクター単位になる。フォロワーも投稿もアクターごとに分かれるので、
      Phase 4 の配信キューが「どのアクターとして署名するか」を持つ必要がある
- [x] Phase 1 で入れた `test-` の使い捨てアクターを消す
      - 残したままだと、誰でも `test-<任意>` を引けるアカウントが本番に残る。
        Phase 3 でフォロワーを永続化した後だと、使い捨てのつもりのアクターにフォロワーが付く

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
      - 終了時の WAL の畳み込みだけ入っている。シャットダウンフックから DB を閉じ、
        止めた後なら `.db` 単体をコピーしても中身が揃う。動かしたままコピーする手順は未着手
- [ ] 未対応アクティビティ（`Like`, `Announce`, `Create{Note}` の返信など）を安全に無視する

---

## Phase 8: 管理 UI（Compose Multiplatform for Web / Kotlin/Wasm）

サーバーが完成してから作る。UI が先だとフェデレーションのデバッグができない。

画面の枠（ルーティングとアカウント画面の見た目）と、管理 API の口（GraphQL）とログインは
先に入れた。仕組みの説明は [docs/architecture.md](docs/architecture.md) にある。

- [x] `:frontend` モジュール（Kotlin/Wasm + Compose）と画面の枠
      - `/` トップ / `/@ユーザー名` アカウント画面 / `/admin` 管理画面 / それ以外は見つからない。
        判定は `navigation/Screen.kt` の 1 箇所
      - 管理画面の中も操作ごとにパスを分ける（`/admin/accounts`、`/admin/accounts/new`、
        `/admin/accounts/@{name}`）。
        1 画面に並べると、開いた時点で必要のない問い合わせが走り、URL でその操作を指せない
      - 画面遷移は Navigation Compose 3（JetBrains 版）。履歴の持ち主はブラウザ側に一本化し、
        `popstate` を受けて URL からバックスタックを作り直す。両方で履歴を持つとずれる
      - [ ] アカウント画面の中身を実データにする。フィードと記事は Phase 5、
            数値は管理 API を繋いでから。画面には仮の値である旨を出している
            - 存在しないアカウントは見つからない表示にした。残っているのは
              フィードと記事と数値
- [x] 日本語のフォントを配信して読み込む
      - canvas に描いているので、何もしないと日本語が豆腐になる。`index.html` の
        `@font-face` も効かない。静的ファイルと一緒に配信し、起動後に取ってきて
        `FontFamily` を組み立てる（`ui/Font.kt`）
      - 入れたのは Noto Sans JP の W400 / W500 / W700。1 つ読めるたびに差し替える
      - [ ] 実機で表示を確認する。読み込みの経路は通したが、実際の見た目はまだ見ていない
      - [ ] ttf のままなので 1 ファイル 5MB 台ある。日本語の常用範囲にサブセットすると
            桁で小さくなる。woff2 は Skia が読めないので ttf のまま subset する
- [x] `:backend` が静的ファイルを配信する（`STATIC_SRC_DIR`。root から配信し、無ければ `index.html`）
- [ ] スキーマに書けない定数（パスワードの長さ制限、環境変数名、画面のパス）を
      `:backend` と `:frontend` で共有する
      - `:shared`（KMP: `jvm` + `wasmJs`）にはいま `/graphql` のパスだけ入れてある
- [ ] `:frontend` の成果物を配置するデプロイスクリプトを用意する
      （インフラ側で用意する。このリポジトリの範囲外。「ビルドと配布の分け方」を参照）
- [ ] 管理 API を GraphQL にする（スキーマ優先で、間の型は生成する）
      - 口と結線は動いている。エンドポイントは `POST /graphql` の 1 つで、管理用は
        `Query.admin` / `Mutation.admin` の下にまとめ、認可はエンドポイントではなく
        フィールドごとに見る。載っているのはログインとアカウントの追加・一覧・参照だけなので、
        フィード CRUD などが載ってからチェックを付ける
      - kickstart はリフレクションで結線するので、native-image 向けの登録が要る。
        リゾルバの実装は必ず `graphql.resolver` に置くこと（`GraphQlReflectionTargetsTest` が見ている）
      - native バイナリで query / mutation / 変数 / enum / `Set-Cookie` / スキーマ検証まで通した。
        JVM のテストはこの経路の問題を出さないので、依存を足したら native ビルドを通すこと
- [ ] 管理 API に認証をかける（パスワード 1 つ + メモリ上のセッション）
      - ログインの仕組みは実装済み。`Query.admin.session` / `Mutation.admin.login` /
        `Mutation.admin.logout`、ハッシュは `ADMIN_PASSWORD_HASH`（PBKDF2-HMAC-SHA256）。
        未設定でも起動できる
      - 残っているのはハッシュ生成を画面から行えるようにすること（いまは
        `./gradlew --quiet :backend:crypto:passwordHash`）と、総当たり対策（Phase 7）
- [ ] サーバー側に管理 API の残り（フィード CRUD、配信状況、手動再取得）
      - アカウントの一覧 (`Query.admin.adminAccounts`)、1 件の参照 (`Query.admin.adminAccount`)、
        追加 (`Mutation.admin.addAccount`) は入れた
- [ ] 開発時は frontend の dev サーバー (8081) から backend (8080) を叩くので CORS か proxy 設定が要る
      - webpack の devServer proxy で `/graphql` を 8080 に転送する。
        オリジンが同じままなら CORS も Cookie の SameSite も緩めずに済む
- [ ] Compose でフィード一覧 / 追加 / 削除
- [ ] アクターごとのフォロワー数・最終投稿・配信エラーの表示
- [ ] フィードのプレビュー（投稿前にどう見えるか）
- [ ] 手動投稿・再配信のトリガー

---

## Phase 9: リリース

- [ ] native-image のビルドを本番向けに最適化（PGO、`--gc=G1` など）
- [x] 設定の外出し（ドメイン、DB パス、ポート）
      - 環境変数に寄せた。`ServerConfig` と `DatabaseConfig` が入口。
        ポーリング間隔は Phase 5 でフィードごとに持つので、ここには入れない
- [x] Dockerfile と docker-compose.yml
      - multi-stage build。実行用のステージ（debian:13-slim）には JDK を持ち込まない。
        DB は名前付きボリューム。`HEALTHCHECK` で `/healthz` を叩く
- [x] `main` へのマージで GitHub Packages（ghcr.io）にイメージを publish する
      - タグは `latest` と commit SHA。戻せるよう latest だけにはしない

---

## つまずきやすい点（先に知っておく）

| 症状 | 原因になりやすいもの |
| --- | --- |
| 検索しても出てこない | `Content-Type` が `application/activity+json` でない / WebFinger の `subject` 不一致 |
| フォローが保留のまま | `Accept` を返していない / `Accept` の `object` が id だけ |
| 署名検証に失敗する | 署名文字列の再構築時の改行・小文字化・ヘッダー順序 |
| 相手から 401 が返る | `Digest` ヘッダ未送信 / `Date` のずれ / secure mode で GET に署名がない |
| 投稿が届かない | `to` に Public が入っていない / `cc` に followers がない |
| アクターを直しても反映されない | Mastodon 側のキャッシュ（ユーザー名を変えて試す） |
| native-image で落ちる | リフレクション設定不足（`@Serializable` 型や jOOQ の `Record` の登録漏れ）・SQLite ネイティブライブラリ・ビルド時初期化と実行時初期化の食い違い |
| JVM のテストは通るのに native だけ落ちる | テストが native で実行されていない。`nativeTest` の対象に入れられないか検討する |
| 起動時に `no such table` で落ちる | スキーマの適用忘れ。sqlite3def で `schema.sql` を適用する（起動時の自動適用は無い） |
| native バイナリで Shift_JIS のフィードだけ読めない | 文字コードが同梱されていない（`-H:+AddAllCharsets` 未指定） |
| ログが 1 行も出ない | SLF4J の実装が classpath に無い。`No SLF4J providers were found` が出て以降すべて NOP になる |
| 外部キー制約が効かない | SQLite は `PRAGMA foreign_keys` が既定で OFF。接続ごとに ON にする必要がある |
| `SQLITE_BUSY` が出る | ライターを複数持っている / `busy_timeout` 未設定 |

## 参考仕様

- ActivityPub — https://www.w3.org/TR/activitypub/
- Activity Streams 2.0 / Vocabulary — https://www.w3.org/TR/activitystreams-core/
- WebFinger (RFC 7033) — https://datatracker.ietf.org/doc/html/rfc7033
- HTTP Signatures (draft-cavage-http-signatures-12) — Mastodon が実装しているのはこのドラフト版
- Mastodon 実装ドキュメント — https://docs.joinmastodon.org/spec/activitypub/
