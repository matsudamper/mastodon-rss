# mastodon-rss
RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。

開発の進め方とロードマップは [TODO.md](TODO.md)、横断的な設計は
[docs/architecture.md](docs/architecture.md)、Mastodon から見える仕様
（エンドポイント・鍵の扱いなど）は [docs/mastodon-spec.md](docs/mastodon-spec.md) を参照。

## 必要なもの

JDK が 1 つあれば足りる。バージョンは問わない（Gradle を起動できればよい）。

ビルドに使う JDK 25 と、native-image に使う GraalVM 25 は Gradle が必要に応じて
取ってくる（`settings.gradle.kts` の foojay-resolver）。手元に入れてあるものが
条件に合えばそれを使い、無ければダウンロードして `~/.gradle/jdks` に置く。

Gradle 自体も wrapper が入っているので個別のインストールは不要。

### GitHub Packages の資格情報

管理 API のコード生成に使う graphql-java-codegen は
[matsudamper/graphql-java-codegen](https://github.com/matsudamper/graphql-java-codegen)
の fork のビルドを GitHub Packages から取る。無いと構成の時点で落ちるので、
`~/.gradle/gradle.properties` に置く。

```properties
gpr.user=<GitHub のユーザー名>
gpr.key=<read:packages を付けたパーソナルアクセストークン>
```

環境変数 `GITHUB_ACTOR` / `GITHUB_TOKEN` でも読む。CI はそちらを使っている。

## ビルド

### 全体

```sh
# ビルドとテスト
./gradlew build

# テストのみ
./gradlew test
```

### backend

```sh
# JVM で起動する（http://localhost:8080）
# DOMAIN は必須。手元で試すだけなら適当な値でよいが、
# Mastodon から実際に引かせるときは公開しているホスト名にすること
DOMAIN=example.com ./gradlew :backend:run
```

起動できたかは `GET /healthz` で見る。`{"status":"ok"}` が返れば動いている。

### backend の native-image

GraalVM は Gradle が用意するので、事前のインストールは要らない。初回は
ダウンロードするぶん時間がかかる。

```sh
# ネイティブバイナリを生成する
./gradlew :backend:nativeCompile

# 生成されたバイナリを起動する
DOMAIN=example.com ./backend/build/native/nativeCompile/mastodon-rss
```

### frontend

```sh
# 開発サーバーを起動する（http://localhost:8081、ホットリロードあり）
./gradlew :frontend:wasmJsBrowserDevelopmentRun

# 配布物を生成する
./gradlew :frontend:wasmJsBrowserDistribution
```

配布物は `frontend/build/dist/wasmJs/productionExecutable/` に出力される。

初回ビルドでは Kotlin/Wasm のツールチェイン（Node.js、yarn、webpack など）が
ダウンロードされるため時間がかかる。

### backend から画面を出す

```sh
./gradlew :frontend:wasmJsBrowserDistribution

# DOMAIN は画面の配信には関係しないが、必須なので入れる
DOMAIN=example.com \
STATIC_SRC_DIR=frontend/build/dist/wasmJs/productionExecutable \
  ./gradlew :backend:run
```

### 画面のパス

サーバーが持つパス以外は静的ファイルの配信に落ちる。ファイルがあればそれを返し、
無ければ `index.html` を返して画面側に解釈させる。どの画面を出すかはブラウザ側の判断になる。

| パス | 画面 |
| --- | --- |
| `/` | トップ |
| `/@{name}` | アカウント画面。フィードの取得状況と配信した記事 |
| `/admin` | 管理画面のトップ。ログインと、下の各画面への入口 |
| `/admin/accounts` | アカウントの一覧 |
| `/admin/accounts/new` | アカウントの追加 |
| それ以外 | 見つからない（HTTP は 200 のまま） |

管理画面の操作は 1 画面に並べず、操作ごとにパスを分ける。
開いた時点で必要のない問い合わせが走らず、URL でその操作を指せる。
`/admin` の下の知らないパスは、管理画面ではなく見つからない扱いにする。

### 管理画面のログイン

`/admin` にはログインが要る。パスワード 1 つで、ユーザー名は無い。

パスワードそのものはサーバーに置かず、ハッシュを `ADMIN_PASSWORD_HASH` に入れる。
ハッシュは標準入力にパスワードを渡して作る。引数にするとシェルの履歴と `ps` に平文で残る。

```sh
# 表示された 1 行がそのまま ADMIN_PASSWORD_HASH の値
./gradlew --quiet :backend:crypto:passwordHash
```

手元で試すときは `ADMIN_COOKIE_SECURE=false` を付ける。既定ではセッション Cookie に
`Secure` が付き、`http://localhost:8080` ではブラウザが Cookie を保存しないので、
ログインしてもログインしていない状態のままになる。

```sh
DOMAIN=example.com \
STATIC_SRC_DIR=frontend/build/dist/wasmJs/productionExecutable \
ADMIN_PASSWORD_HASH='pbkdf2-sha256:...' \
ADMIN_COOKIE_SECURE=false \
  ./gradlew :backend:run
```

`ADMIN_PASSWORD_HASH` が未設定でも起動する。最初のハッシュを作る前に起動できないと
先に進めないため。この場合はログインできず、画面と起動ログにその旨が出る。

ログインすると `/admin` に各画面への入口が出る。いまあるのはアカウントの一覧と追加、
アカウントごとのフィード登録と投稿で、配信状況はこれから作る。ログインしていない状態で下の画面を直接開いた場合は、
そこにフォームを出さず `/admin` に案内する。パスワードを入れる場所を 1 つに寄せるため。
セッションの持ち方と Cookie の扱いは [docs/architecture.md](docs/architecture.md) を参照。

### アカウントの追加

追加は `/admin/accounts/new`、一覧は `/admin/accounts`。
Mastodon から検索できる名前は、管理画面から追加して `accounts` テーブルに入ったもの。

名前は後から変えられない。Mastodon はリモートのアカウントを永続キャッシュするので、
アクターの ID が変わると相手には別のアカウントとして見える。別の名前にしたい場合は
作り直すことになる。

使える文字は英数字と `_` `.` `-` で、先頭と末尾は英数字か `_`、長さは 30 文字まで。
既にある名前は追加できない。

### フィードの自動取得

登録したフィードはサーバーが定期的に取りに行き、新着記事をそのアカウントから投稿する。
起動していれば操作は要らない。

取得の間隔はフィードごとに `feeds.poll_interval_seconds` が持ち、登録時の既定は 900 秒（15 分）。
管理画面から変える口はまだ無いので、変えるときは DB を直接更新する。
取得に失敗したときの内容は `feeds.last_error` に残り、サーバーのログにも出る。

### API
graphqlを使用している `POST /graphql`
スキーマファースト`backend/graphql`モジュール参照

結線は graphql-java-tools (kickstart) が行う。リフレクションを使うので、native-image
向けにクラスを登録する。登録はイメージのビルド時に `GraphQlReflectionFeature` が
クラスパスを走査して行うので、スキーマを触っても設定ファイルの更新は要らない。
ただしリゾルバの実装は `graphql.resolver` パッケージに置くこと（走査の対象から
外れていないかは `GraphQlReflectionTargetsTest` が見ている）。

native バイナリで動くことは確認済み（`/graphql` に query・mutation・変数・enum・
`Set-Cookie` まで通した）。そのために足した指定と、それぞれ何で落ちたかは
[native-image の README](backend/src/main/resources/META-INF/native-image/net.matsudamper/mastodon-rss-backend/README.md)
に書いてある。

画面は canvas に描いているので、ブラウザの持っているフォントは使われない。日本語を出すために
Noto Sans JP を `/fonts/*.ttf` として一緒に配信し、起動後に読み込んで当てている。
実体は `frontend/src/wasmJsMain/resources/fonts/`（SIL Open Font License 1.1。同じ場所に
`OFL.txt` を置いてある）で、読み込みは `:frontend` の `ui/Font.kt`。

表示している数値と記事はまだ仮の値で、画面の上にその旨を出している。

## 環境変数

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | バインドするアドレス |
| `PORT` | `8080` | 待ち受けポート |
| `DB_PATH` | `./data/mastodon-rss.db` | SQLite の DB ファイル。親ディレクトリは起動時に作られる |
| `DOMAIN` | **必須** | 外部に公開するドメイン。WebFinger の `acct:` とアクターの `id` に使う |
| `ACTOR_PRIVATE_KEY_PATH` | `./data/actor-private-key.pem` | アクターの秘密鍵 (PEM)。無ければ起動時に生成して書き出す |
| `ACTOR_PRIVATE_KEY_PEM` | なし | 秘密鍵の PEM を直接渡す場合に使う。`ACTOR_PRIVATE_KEY_PATH` とは併用できない |
| `STATIC_SRC_DIR` | なし | 配信する静的ファイルのディレクトリ。未設定なら何も配信しない |
| `ADMIN_PASSWORD_HASH` | なし | 管理画面のパスワードハッシュ。未設定でも起動するが、その間はログインできない |
| `ADMIN_COOKIE_SECURE` | `true` | セッション Cookie に `Secure` を付けるか。手元で http で試すときだけ `false` にする |

`DOMAIN` は scheme と末尾の `/` を書いても落として扱う。未設定だと起動しない。
アカウント名に使えるのは英数字と `_` `.` `-` で、先頭と末尾は英数字か `_`、
長さは 30 文字まで。

`DOMAIN` はアクターの ID に焼き込まれ、変えると相手からは別人のアカウントに見える。
理由は `ServerEnv.kt` と `ActorUsernameUtil.kt` の KDoc にある。

アカウントは管理画面から追加する。

エンドポイント、アカウントの引き当て、アクターの鍵の扱いなど、
ビルド以外の仕様は [docs/mastodon-spec.md](docs/mastodon-spec.md) にまとめてある。

## スキーマを変えるとき

`schema.sql` と同じ場所に置いた
[backend/repository/src/main/resources/db/README.md](backend/repository/src/main/resources/db/README.md)
を参照。開発用 DB から `dumpSchema` で書き出して commit し、実 DB へは sqlite3def で
手適用する。そこから jOOQ の型が作られるまでも書いてある。
