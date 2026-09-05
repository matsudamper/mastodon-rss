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

次の一手: Phase 5 の定期ポーリング（新着記事を自動投稿する）。

- Phase 0（土台づくり）完了。`:backend` / `:backend:feature-mastodon` / `:backend:crypto` /
  `:backend:repository` / `:backend:rss` / `:backend:graphql` / `:frontend` のモジュール構成で、
  native バイナリが起動して SQLite に読み書きできる
- Phase 1（アクターの発見）完了。`social-rss.matsudamper.net` で WebFinger と Actor を公開している
- Phase 2（フォローの成立）完了。inbox の署名を検証し、`Follow` に `Accept` を返す
- Phase 3（フォロワーの永続化）完了。実機で再起動後もフォロワーが保持されることを確認した
- Phase 4（投稿の配信）完了。実機で管理画面からの投稿がタイムラインに出ることを確認した
- Phase 5 のうち、フィードの解析（`:backend:rss`）と、管理画面から URL を取得して
  `feeds` に保存し、登録時に既存記事を取り込み、未投稿を手動投稿できるところまで入れた。
  管理画面から最新の取り込みと投稿もできる。
  定期ポーリングは未実装なので、新着の自動投稿はまだ流れない。
  ActivityPub 側と独立していて後戻りが出ないため前倒しした
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

→ 案A を採用する。アカウントは管理画面から追加し、`accounts` に持つ。

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

- [x] スキーマ設計（`remote_actors` / `followers` / `notes` を足す）
- [x] `Follow` を記録してから `Accept` を返す
      - [ ] `Accept` を返した後の記録に何度も失敗した場合は諦めてログに残すだけ。
            相手にはフォロー中と見えるのに投稿が届かない状態が残る。
            後から整合させる仕組みは配信キューと一緒に考える
- [x] `Undo{Follow}` を処理してフォロー解除
- [x] `Delete{Actor}`（アカウント削除・引っ越し）を処理してフォロワーを掃除
      - [ ] 署名を検証できない `Delete` は 202 で受け流すだけで掃除しない。
            相手のアクター文書がまだ引ける間に届いたものしか消せない
- [x] `GET /users/{name}/followers`（OrderedCollection、cursor でページング）
- [x] 冪等性: 同じ `Follow` を二重に受けても重複行を作らない
- [x] フォロワーがいるなら鍵の自動生成を拒否して起動を止める
      - [ ] 鍵を失った状態での起動は警告ログを出すだけ。鍵の置き場を DB に移すかは
            Phase 6 の複数アクター化と合わせて決める

### ✅ チェックポイント 3
プロセスを再起動してもフォロワー数が保持される。アンフォローすると減る。

- [x] 実機で確認する

---

## Phase 4: 1件の投稿がタイムラインに流れる

RSS はまだ絡めない。手動トリガーで固定文字列を投稿する。

- [x] `Note` オブジェクトの生成
      - [ ] リンクを `<a href="...">` として `content` に埋めるのは未実装。
            本文の組み立ては Phase 5 の取り込みで決まる
- [x] `Create` アクティビティで包んで全フォロワーの inbox に POST
- [x] `sharedInbox` があればそちらにまとめて送る
- [ ] 配信キュー: 失敗時に指数バックオフでリトライ、上限到達で諦める
      - 入れていない。いまは失敗したらログに残して諦める。要るのは実際に
        取りこぼしが見えてから
- [x] `GET /users/{name}/outbox`（OrderedCollection、cursor でページング）
- [x] `GET /notes/{id}` で単体の Note を返す
- [x] 投稿を発火させる管理用画面（`/admin/accounts/@{name}`）

### ✅ チェックポイント 4
フォロワーのホームタイムラインに投稿が現れ、リンクをクリックできる。

- [x] 実機で確認する。本文へのリンクの埋め込みは Phase 5 で入る

---

## Phase 5: RSS を取り込んで自動投稿する

ここでようやく本来の機能。ActivityPub 側はもう触らない。

フィードを読む部分（`:backend:rss`）と、管理画面からフィードを登録するところまで実装し、
実機で登録できることを確認した。登録時の既存記事の取り込みは入れた。
定期ポーリングは未実装なので、新着の自動投稿はまだ流れない。
どこまでやったかは各項目の下に書いた。

- [x] RSS 2.0 / RSS 1.0 (RDF) / Atom 1.0 のパーサを自作（StAX。`FeedParser`）
      - 外部エンティティと DTD は切ってある。入口はバイト列で、文字コードは XML 宣言と
        BOM から判定させる（先に String にすると Shift_JIS の配信元で文字が壊れる）
      - 日時は RFC 822 と RFC 3339 に加えて崩れた形も読む（`FeedDates`）。
        読めなければ null にして記事は捨てない
      - [x] 繋ぐときに `:backend` の native-image へ `-H:+AddAllCharsets` を足す
            - native バイナリには既定で一部の文字コードしか入らず、Shift_JIS の
              フィードを読んだ時点で `UnsupportedCharsetException` になる
            - `:backend:rss` の `nativeTest` には指定済み。これが無いと Shift_JIS の
              テストが native でだけ落ちることを確認している（そうやって見つけた）
- [x] `feeds` / `feed_items` テーブル
      - `feeds` は `account_id` で `accounts.id` と 1:1。
        管理画面から URL のプレビューと保存まで対応し、実機で確認済み
      - `feed_items` は登録時の取り込みで使う
      - この時点ではフィード 1 本で検証する。管理画面からフィード用のアカウントを
        追加して動かす
      - 運用者用のアカウントから記事を流さないこと。フィード用と混ぜると
        フォロワーが付き、後から分けたときにその人たちには何も届かなくなる
        （1 アカウントから複数への分割は `Move` では表現できず、引っ越しを
        通知する手段が無い）
- [x] 差分検出: `guid` / `id` / `link` を主キーに、なければ URL + タイトルのハッシュ
      - `FeedItemKey`。優先順は `id`（`guid` / Atom の `id` / `rdf:about`）→ `link` → ハッシュ
      - 保存側の突き合わせは `FeedItemRepository.findExistingKeys`
- [ ] 条件付き GET（`ETag` / `If-Modified-Since`）でフィード配信元に優しくする
      - 保存する値の形（`FeedFetchValidators`）と、記録する口だけ interface に置いた。
        送るのは HTTP クライアントを持つ `:backend` 側の仕事なので未実装
- [ ] スケジューラ（定期ポーリング）。フィードごとに間隔を設定可能に
      - 間隔を持つ場所（`Feed.pollIntervalSeconds`）と、対象を引く
        `FeedRepository.findDue` は実装済み。回す部分は未実装
- [x] 初回登録時に既存記事を取り込み、未投稿を確認して手動投稿できる
- [x] 管理画面から最新を取り込んで投稿できる
      定期ポーリングは未実装。新着の自動投稿はまだ流れない
- [x] HTML サニタイズ（Mastodon が許可するタグに絞る）
      - `HtmlSanitizer`。許可したタグと属性以外を落とし、`href` はスキームも見る。
        閉じられていないタグは末尾で閉じる（壊れた入れ子をそのまま流すと受信側の表示が崩れる）
- [ ] 本文の長さ調整（インスタンスによっては 500 文字制限。タイトル + リンクを基本形に）
      - 切り詰め（`FeedText.truncate`。コードポイント単位で切り、単語の途中なら空白まで戻す）
        は用意した。未投稿の手動投稿は題名、説明、リンクにする。新着の本文の組み立ては
        定期ポーリングを書くときに決める
- [ ] 取得失敗・パース失敗時のエラーハンドリングとログ
      - パースの失敗は `FeedParseException` にした。取得の失敗と、失敗をどう記録して
        どこに出すかは未実装（記録する口は `FeedRepository.recordFetchFailure`）

### ✅ チェックポイント 5
実在の RSS を登録して放置し、新着記事が自動でタイムラインに流れる。

---

## Phase 6: 複数アクター（フィードごとのアカウント）

案A の残り。RSS フィード 1 本につきアカウントを 1 つ作り、利用者は読みたいフィードの
アカウントだけをフォローする。引き当てはすでに DB 駆動。残っているのはフィードとの
対応、鍵のアカウントごと化、削除の配信。

運用者用のアカウントはフィード用ではなく、記事は流さない。いまの用途はサービスの
状況やメンテナンスの告知。フィード用アカウントは `feeds` に紐付いた別のアクターとして作る。

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
      - 既にある名前は追加時に弾いている
- [x] アクターを DB 駆動に変更（起動時ハードコードをやめる）
      - `accounts` テーブルを引く（`StoredActorNames`）
- [x] WebFinger を動的解決（任意の `acct:` を DB 引きして応答）
      - `ActorDirectory` の 1 か所を通すので、パスの `{username}` と一緒に動的になった
- [ ] アクターごとに鍵ペアを生成して保存
      - Phase 1 の鍵はファイル 1 本。ここで `actors.private_key` に移すかを決める
        （Phase 3 の「フォロワーがいるなら鍵の自動生成を拒否する」と合わせて判断する）
- [x] アクター作成 / 削除の API
- [ ] アクター情報更新時に `Update{Actor}` を配信（アイコン・説明文の変更を伝播させる）
- [ ] アイコン / ヘッダー画像（`icon` / `image`）の配信
- [ ] フィードアクターのプロフィールに `admin` へのリンクを置く
      - Mastodon がプロフィールに出す「リンク集」は Actor JSON の `attachment`。
        `{ "type": "PropertyValue", "name": "管理", "value": "<a href=\"...\">@admin@example.com</a>" }`
        の配列で、`value` は HTML を入れる
      - フィードのアカウントだけを見た人が、どこに問い合わせればいいか分かるようにする
      - フィードの配信元 URL も同じ `attachment` に並べると分かりやすい
      - `attachment` を変えたら `Update{Actor}` を配信しないと相手側の表示が古いまま
      - フィードのサイトと配信元 URL は `attachment` に出るようにした。
        `admin` へのリンクと `Update{Actor}` の配信は未着手
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
        `/admin/accounts/@{name}`、`/admin/accounts/@{name}/feeds/new`）。
        1 画面に並べると、開いた時点で必要のない問い合わせが走り、URL でその操作を指せない
      - ダイアログもパスを持つ 1 画面として積む。下に敷く画面は残したまま重ねる
        （`Screen.Overlay` と `TransparentScreenSceneStrategy`）
      - 画面遷移は Navigation Compose 3（JetBrains 版）。履歴の持ち主はブラウザ側に一本化し、
        `popstate` を受けて URL からバックスタックを作り直す。両方で履歴を持つとずれる
      - [x] アカウント画面の中身を実データにする
            - 存在しないアカウントは見つからない表示にした
            - 投稿・フォロワー数・投稿数・フィードは公開 API から表示する
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
- [ ] 開発時は frontend の dev サーバー (8081) から backend (8080) を叩くので CORS か proxy 設定が要る
      - webpack の devServer proxy で `/graphql` を 8080 に転送する。
        オリジンが同じままなら CORS も Cookie の SameSite も緩めずに済む
- [ ] Compose でフィード一覧 / 追加 / 削除
      - フィードの追加は `/admin/accounts/@{name}/feeds/new` のダイアログに入れた。
        投稿ごとの元記事の表示と、記事と投稿の削除は `/admin/accounts/@{name}` に入れた。
        フィード自体の削除は未実装
- [ ] アクターごとのフォロワー数・最終投稿・配信エラーの表示
      - フォロワー数は `/admin/accounts/@{name}` に出している。最終投稿と配信エラーは未着手
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
