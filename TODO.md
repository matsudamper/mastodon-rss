# mastodon-rss 開発ロードマップ

RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。
ライブラリに依存せず ActivityPub を自前実装する。

---

## 現在地と次の一手

現在地: Phase 0 は完了。`:backend` / `:backend:crypto` / `:backend:repository` / `:frontend` の
4 モジュール構成。サーバー専用の crypto と repository は `backend/` の下に置いている。
`:backend` は Ktor (CIO) + kotlinx.serialization で `/healthz` を返し、JVM でも native-image でも動く。
`:backend:crypto` は RSA 鍵の生成と PEM 変換、SHA256withRSA の署名・検証。
`nativeTest` で native バイナリ上でも動くことを確認済み。
`:backend:repository` は SQLite に接続し、起動時にマイグレーションを適用するところまで。
`:frontend` は Compose Multiplatform for Web (Kotlin/Wasm) で Hello World を表示するところまで。
CI で ktlint / JVM ビルド・テスト / frontend / crypto の native テスト / native-image の 5 ジョブが回っている。

Phase 1 はコードの側は書けた。アクターは `admin` 固定（`ACTOR_USERNAME` で変更可）で、
`DOMAIN` は必須。WebFinger と Actor が返るところまで実装し、native バイナリでも
実際に叩いて確認している。

次の一手: HTTPS で外に出して、Mastodon から実際に検索する（チェックポイント 1）。
ここだけは手元では検証できず、本番ドメインと公開経路が要る。
検索するのは `@admin@<ドメイン>` ではなく `@test-1@<ドメイン>` から。Mastodon は
リモートアクターを永続キャッシュするので、`admin` で失敗すると `admin` が使えなくなる。
`nodeinfo` は任意なので、詰まったときの調査用に後から足せばよい。

Phase 0 でやったことと順序の理由:

| 順 | やること | なぜこの順か |
| --- | --- | --- |
| 0-1 | マルチモジュール化（完了） | あとから分割すると全ファイルが動くので最初にやる |
| 0-2 | `GET /healthz`（完了） | 生存確認の口がないと以降の CI 検証が書けない |
| 0-3 | kotlinx.serialization（完了） | DB の前に JSON を通しておくと healthz から検証できる |
| 0-4 | SQLite 接続（完了） | native-image で最も割れやすい要素その1 |
| 0-5 | マイグレーション（自前連番 SQL）（完了） | jOOQ codegen の入力になるので codegen より先 |
| 0-6 | JCA の native 確認（完了） | Phase 1 の鍵生成と Phase 2 の署名の前提。最も安く済み、詰まると後続が全部止まる |
| 0-7 | reflect-config を無くす（完了） | Phase 1 で `@Serializable` 型が増える前にやる。手で足す運用は先に破綻する |
| 0-8 | ktlint を入れるか決める（完了 → 入れた） | いつでもよいが Phase 1 に入る前が切りが良い |

jOOQ の codegen は Phase 0 から外した。現在のスキーマは `health_check` と `schema_version` だけで、
生成しても使う場所が無く、native-image のリフレクション設定だけが先に増える。
スキーマが実際に必要になる Phase 3 の直前に、採用するかどうかごと判断する。
詳細は Phase 2 と Phase 3 の間に置いた「jOOQ を採用するかの判断」を参照。

DB を ActivityPub (Phase 1) より先に入れたのは、native-image で壊れるとしたら
SQLite のネイティブライブラリが原因になる可能性が高く、
フェデレーションの実装が乗る前に潰しておきたかったため。

## 使用技術

| 領域 | 技術 |
| --- | --- |
| 言語 | Kotlin（整形は ktlint / `ktlint_official`） |
| ランタイム | GraalVM (native-image) |
| HTTP サーバー | Ktor (CIO) |
| DB | SQLite |
| DB アクセス | 素の JDBC（jOOQ を入れるかは Phase 3 の直前に判断する） |
| 署名 | JCA（RSA / SHA256withRSA）。ライブラリは足さない |
| UI | Compose Multiplatform for Web (Kotlin/Wasm) |
| 管理 API | GraphQL。サーバーは graphql-java、クライアントは Apollo Kotlin（Phase 8 で作る） |
| 画面遷移 | Navigation Compose 3（JetBrains 版）（Phase 8 で作る） |

管理 API を GraphQL にするのは [kake-bo](https://github.com/matsudamper/kake-bo) と
揃えるため。ActivityPub 側は相手の実装が決まっているので REST のまま。

モジュールは `:backend`（サーバー）、`:backend:crypto`（鍵と署名）、
`:backend:repository`（DB アクセス）、`:frontend`（管理 UI）の 4 つ。
crypto と repository は JVM のライブラリに依存していて `:frontend` からは使えないため、
`backend/` の下にネストしている。ビルド方法は [README.md](README.md) を参照。

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

- [x] `:backend:repository` — DB アクセス。0-4 で追加した
      - 当初は `:core`（ドメインモデル / DB アクセス / ActivityPub の JSON モデル / RSS パーサ）
        という括りを想定していたが、責務が広すぎるので DB アクセスに絞った
      - Kotlin JVM。Ktor に依存させない。公開するのは interface だけ
- [x] `:backend:crypto` — 鍵と署名。0-6 で追加した
      - Kotlin JVM。依存は Kotlin 標準ライブラリと JCA だけで、Ktor も JDBC も入らない
      - 分けた理由は `nativeTest` を回せるようにするため。`:backend` のテストは
        `ktor-server-test-host` 経由で `kotlinx-coroutines-debug` を引き込み、
        その先の ByteBuddy と JNA が実行時のバイトコード書き換えに依存するので
        native-image では動かない。JCA の確認をそこに同居させると検証できなくなる
      - Phase 2 の署名文字列の組み立てと Digest の計算もここに置く予定
- [ ] `:shared` — `:backend` と `:frontend` で共有するもの。KMP (`jvm` + `wasmJs`)
      - 中身は GraphQL のスキーマと、スキーマに書けない定数だけ。Phase 8 で追加する

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

- [x] `kotlin("plugin.serialization")` を入れる
      - 当初は `ktor-serialization-kotlinx-json` と `ContentNegotiation` も入れたが、
        0-7 でリフレクションを避けるため両方外した
- [x] `Json` の設定を決めて 1 箇所に集約する
      - `encodeDefaults = true`（ActivityPub は既定値の省略で相手側が転ぶことがある）
      - `explicitNulls = false`（`null` フィールドを出力しない）
      - `ignoreUnknownKeys = true`（受信側。相手の拡張プロパティで落ちないように）
      - 実体は `backend/src/main/kotlin/net/matsudamper/mastodon/rss/json/AppJson.kt`
- [x] `/healthz` を JSON レスポンスに変える
- [x] ActivityPub 向けの下ごしらえ（Phase 1 で効いてくるので、ここで型だけ用意しておく）
      - [x] `@context` のような記号入りのキーは `@SerialName("@context")` で対応する
            - Phase 1 の `activitypub/Actor.kt` で実際に使った。
              実物のフィールドが無い状態で用意しても検証できないので、そこまで待った
      - [x] ActivityPub は「文字列 1 個」と「配列」のどちらも来るフィールドが多い（`@context`, `to`, `cc`, `type`）
            → 常に `List<String>` として扱い、単一文字列も配列に正規化するカスタム serializer を書く
            - `activitypub/StringListSerializer.kt`。出力は 1 要素なら文字列、それ以外は配列に戻す
              （常に配列にすると `"type": ["Note"]` になり、単一文字列前提の実装が解釈できない）
      - [x] `object` が「URL 文字列」と「埋め込みオブジェクト」の両方を取る箇所がある（`Undo`, `Accept`）
            → `JsonElement` で受けて分岐する型を用意する
            - `activitypub/LinkOrObject.kt`。`Link` / `Embedded` の sealed interface として持つ
      - [x] Content-Type は Ktor 既定の `application/json` ではなく `application/activity+json` を返す必要がある
            → カスタム `ContentType` を定義して `respondText` / `respond` で明示する
            - `activitypub/ActivityPubContentTypes.kt` に `activity+json` / `ld+json` / `jrd+json` を定義した。
              `Accept` に応じた選択は 0-7 で `ActivityPubContentTypes.negotiate()` に移した

### 0-4. SQLite 接続

DB アクセスは `:core` ではなく `:backend:repository` モジュールに置くことにした。
公開するのは `Repositories` interface と `DatabaseConfig` だけで、
JDBC を使う実装は `internal` にして呼び出し側から見えないようにする。
sqlite-jdbc を `implementation` で入れているため、`:backend` の compile classpath にも漏れない。

- [x] `org.xerial:sqlite-jdbc` を `:backend:repository` に入れ、テーブル作成 → INSERT → SELECT の疎通を通す
- [x] 接続時に必ず入れる PRAGMA を 1 箇所にまとめる
      - `journal_mode=WAL`（読み書きの並行性。ただしファイル DB のみ有効）
      - `foreign_keys=ON`（SQLite は既定で OFF。忘れると外部キーが効かない）
      - `busy_timeout=5000`（`SQLITE_BUSY` の即時失敗を避ける）
      - `synchronous=NORMAL`（WAL 前提。耐久性と速度の折衷）
      - `SqliteConnectionManager` にまとめ、適用されていることをテストで確認している
- [x] コネクションの持ち方を決める
      - SQLite はライターが 1 本しか取れないので汎用プールは過剰
      - → 接続 1 本 + `ReentrantLock` で直列化する構成にした。
        読み取りが詰まるようなら読み取り用の接続を複数持つ形に広げる
      - HikariCP は入れていない（依存と native-image 設定を減らすため）
- [x] DB ファイルのパスを環境変数で指定できるようにする（`DB_PATH`。デフォルトは `./data/mastodon-rss.db`）
- [x] 親ディレクトリが無ければ起動時に作る

追加でやったこと:

- 接続は `DriverManager` ではなく `SQLiteDataSource` から取る。
  `DriverManager` は `ServiceLoader` でドライバを探すため、native-image で追加設定が要ることがある
- 起動時に `Repositories.verifyWritable()` を呼び、書き込んだ値を読み戻せるか確かめる。
  native バイナリでは SQLite のネイティブライブラリ展開に失敗しても起動自体は通ってしまうため

### 0-5. マイグレーション（自前の連番 SQL）

- [x] `backend/repository/src/main/resources/db/migration/V001__init.sql` の形式で SQL を置く
      - 置き場所は `:backend` ではなく `:backend:repository`。SQL とそれを読むコードを同じモジュールに置く
      - V001 の中身は `health_check` テーブルだけ。フォロワーなどのスキーマ設計は Phase 3 でやる
- [x] `schema_version` テーブルで適用済みバージョンを管理する
      - `version INTEGER PRIMARY KEY, name TEXT NOT NULL, checksum TEXT NOT NULL, applied_at TEXT NOT NULL`
      - チェックサム検証のために `name` と `checksum` を足した
- [x] 起動時に未適用のものをバージョン昇順で適用する。1 ファイル = 1 トランザクション
      - `createRepositories()` の中で適用しきる
- [x] 適用済みファイルの内容が変わっていないかチェックサムで検証する（任意。事故を早く見つけられる）
      - SHA-256。DB にあるバージョンのファイルが手元に無い場合も、古いバイナリの疑いとして弾く
- [x] マイグレーションファイルの列挙方法を native-image で動くやり方にする
      - jar 内リソースのディレクトリ走査は native-image では動かないことがある
      - → Gradle の `generateMigrationIndex` タスクで `db/migration/index` を生成する。
        手で書くと SQL を足したときに更新を忘れて「JVM では動くが native では動かない」状態になる
- [x] `resource-config.json`（または `nativeImageResources` 設定）にマイグレーション SQL を登録する
      - リソースは明示しないと native バイナリに入らない。ここは踏みやすい
      - `backend/repository/src/main/resources/META-INF/native-image/net.matsudamper/mastodon-rss-repository/` に置いた。
        リソースを持つモジュール自身が設定も持つ形にしている
- [x] テスト: 一時ファイル DB に対して 2 回続けて適用しても壊れない（冪等である）ことを確認する
- [x] テスト: 空の DB から最新まで適用できることを確認する

追加でやったこと:

- `splitSqlStatements()` で SQL を文単位に分割する。JDBC は 1 文しか受け取れないため。
  文字列リテラルとコメントの中の `;` では切らない。
  トリガーの `BEGIN ... END;` には未対応（使う段になったら拡張する）
- インストール版（jar 経由）を実際に起動して、ディレクトリ作成・マイグレーション適用・
  WAL の有効化・書き込みの往復を確認した
- native バイナリでの確認は手元に GraalVM が無いため CI の native-image ジョブに任せている。
  0-7 でリソース設定が効いていることを改めて確認する

### 0-6. JCA が native-image 上で動くことを確認する（完了）

Phase 1 のアクター公開鍵と Phase 2 の HTTP Signatures は、どちらも JCA の RSA に乗る。
native バイナリで RSA が使えないと両方が同時に止まるので、実装より先に確かめた。

- [x] `:backend:crypto` モジュールを作り、Phase 1 でそのまま使う形で実装する
      - `RsaKeys` — 2048bit の鍵ペア生成、PKCS#8 / X.509 の PEM 入出力
      - `RsaSignature` — SHA256withRSA の署名と検証
      - 秘密鍵は `BEGIN PRIVATE KEY`、公開鍵は `BEGIN PUBLIC KEY`。
        Mastodon が読むのは X.509 SubjectPublicKeyInfo なので、
        OpenSSL が古い形式で出す `BEGIN RSA PUBLIC KEY`（PKCS#1）ではない
- [x] `nativeTest` で JVM と同じテストを native バイナリとして実行する仕組みを入れる
      - `:backend:crypto` に `org.graalvm.buildtools.native` を入れ、CI に
        「crypto の native テスト」ジョブを足した
- [x] 不正な署名で例外を投げないことをテストで固定する
      - inbox は誰でも POST できるので、壊れた署名は検証失敗として扱う必要がある
- [x] `KeyPairGenerator.getInstance("RSA")` / `Signature.getInstance("SHA256withRSA")` が
      native バイナリ上で通ることを確認する
      - CI の native テストジョブで JVM と同じ 17 件が native バイナリでも通った
      - JCA 向けの追加設定は不要だった。素で動く。
        `--enable-all-security-services` は現行の GraalVM では削除されているが、
        代替を探す必要も無かった
      - `MessageDigest.getInstance("SHA-256")` は 0-5 のチェックサム計算で確認済み

これで Phase 1 の鍵生成と Phase 2 の署名は、native-image 側の心配なく書ける。

ここで止めた線: 鍵をどこに保存するか（ファイルか環境変数か）と、起動時にどう読むかは
Phase 1 の話なので触っていない。この段階では JCA が native で動くことだけを確かめた。

### 0-7. native-image ビルドを通す — ここが Phase 0 の本体

0-4〜0-6 を積んだ状態で native バイナリが動くことが Phase 0 のゴール。

- [x] Gradle プラグイン（`org.graalvm.buildtools.native`）を導入し、Hello World の `nativeCompile` と起動を確認
- [x] sqlite-jdbc のネイティブライブラリ同梱を確認する
      - sqlite-jdbc 3.53.2.1 は `META-INF/native-image` に
        `--features=org.sqlite.nativeimage.SqliteJdbcFeature` を同梱していて、素で動いた
      - JNI 設定も `org.sqlite.tmpdir` の指定も不要だった
      - native バイナリを起動してマイグレーション適用と読み書きの往復を確認済み
- [x] JCA（RSA / SHA-256）が native-image 上で動くことを確認する → 0-6 で完了
- [x] リフレクション設定の増え方を止める → `ContentNegotiation` をやめて serializer を明示する
      - `call.respondJson(Foo.serializer(), value)` の形にした。実体は `json/JsonResponse.kt`
      - コンパイル時に serializer が決まるのでリフレクションが発生せず、`reflect-config.json` を削除できた。
        `@Serializable` 型が増えても設定は増えない
      - 失った `Accept` に応じた Content-Type の自動選択は `ActivityPubContentTypes.negotiate()` で代替した。
        `application/activity+json` と profile 付き `application/ld+json` を品質値順に見て選ぶ
      - 受信側も同じ方針。Phase 2 の inbox は HTTP Signature の Digest 検証に生のボディが要るので、
        どのみち `receive<T>()` は使えず `receiveText()` からの明示デコードになる
      - tracing agent のタスク化は採用しなかった。常用しないものをビルドに残す意味が薄いため、
        native で詰まったときの調査手順として native-image の README に手順だけ残した
- [x] `:backend:repository` にも `nativeTest` を広げるか決める → 広げない
      - native バイナリの起動確認でマイグレーション適用と読み書きは間接的に見えている
      - テストごと native にすると直接確認できるが、native ビルドが 1 つ増えて CI が延びる。
        SQLite が native で壊れるなら起動確認が先に落ちるので、二重に持つ価値が薄い
- [x] リフレクション/リソース設定はどこから来たものか分かるようコメントか README を添える
      - `backend/src/main/resources/META-INF/native-image/net.matsudamper/mastodon-rss-backend/README.md`
      - `:backend:repository` 側の `resource-config.json` は JSON 内の `_comment` に書いた

kotlinx.serialization について native-image で踏んだこと（0-3 の実装が原因で、
JVM のテストは全部通るのに native バイナリだけ 500 を返す状態になっていた）:

- Ktor の `ContentNegotiation` は `call.respond(value)` の際に `KType` から
  serializer をリフレクションで引く。native-image では解決できず
  `Serializer for class 'HealthResponse' is not found.` で 500 になる
- 最初は `@Serializable` な型ごとに `Foo` / `Foo$Companion` / `Foo$$serializer` を
  reflect-config.json に登録して回避した。0-7 で `ContentNegotiation` ごとやめ、
  リフレクションが発生しない形にしたので、この登録は全て消した

`--initialize-at-build-time=kotlin.DeprecationLevel` は残っている:

- reflect-config への登録が原因だと考えていたが、登録を全て消しても再現した。
  native-image は解析中に自分で `isAnnotationPresent` を呼び（`PodFeature.isPodClass`）、
  そこで Kotlin の `@Deprecated` のデフォルト値が読まれて
  `DeprecationLevel` enum がビルド時初期化される
- Kotlin のクラスが解析対象にあれば起きるので、リフレクションを使わなくなっても要る。
  `--trace-class-initialization` で確認した

### 0-8. CI の強化

- [x] native ジョブの起動確認を「`/healthz` が 200」＋「SQLite に書き込めて読み戻せる」まで広げる
      - 一時ディレクトリを `DB_PATH` に渡して起動し、DB ファイルができていることを確認する。
        起動時に必ずマイグレーションと `verifyWritable()` が走るので、
        `/healthz` が 200 を返した時点で書き込みまで通っていることになる
- [x] 起動確認スクリプトで、サーバーが立たなかった場合にログを出して失敗させる
      - プロセスの生死・HTTP レスポンス本体・サーバーログを出すようにした
      - `curl -sf` は 500 でも失敗するため、応答があったかどうかを分けて表示する。
        実際 0-3 の不具合はサーバーが起動したうえで 500 を返す形だったので、
        ログだけ見ても分からず、レスポンス本体が決め手になった
- [x] `kill` を `trap` で確実に行い、ジョブが残留プロセスで詰まらないようにする
- [x] `nativeTest` のジョブを足す（0-6）
      - `:backend:crypto` のテストを native バイナリとして実行する。JCA のように
        「JVM では通るが native では落ちる」たぐいの問題を CI で継続的に拾える
- [x] Kotlin のフォーマッタ（ktlint など）を入れるか決める → 入れた
      - `org.jlleitschuh.gradle.ktlint` をルートから全モジュールに配る。
        ktlint 本体のバージョンは version catalog で固定して Renovate に追従させる
      - スタイルは `ktlint_official`。設定は `.editorconfig` に置く
      - CI に `ktlintCheck` のジョブを足し、違反があれば落とす
      - `ktlint_official` から 3 点だけ外した。いずれも `.editorconfig` に理由を書いてある
        - 関数とクラスの宣言を、行長に収まっていても引数 2 個以上で必ず複数行に展開する挙動
        - `@Composable` の大文字始まりを関数名の違反として扱う挙動

### ✅ チェックポイント 0
ネイティブバイナリ 1 個を起動して `curl localhost:8080/healthz` が通り、SQLite に書き込める。
加えて、native バイナリ上で RSA 鍵ペア生成と SHA256withRSA 署名ができる。

達成済み。`/healthz` と SQLite への書き込みは native-image ジョブの起動確認で、
鍵と署名は `:backend:crypto:nativeTest` で確認している。

Phase 0 は完了。Phase 1 に入る前に「事前に決めておくこと」の本番ドメインを確定させること。

> ### Compose の位置づけ（決定済み: 案2）
> Compose Desktop（Skiko / JVM）は GraalVM native-image では現実的に動かない。
> 管理 UI はブラウザで動かす形にするため、案2 を採用する。
> - 案1: サーバー = native-image バイナリ、管理UI = Compose Desktop の別アプリ（通常のJVM）。両者は HTTP API で通信。
> - 案2（採用）: 管理UI を Kotlin/Wasm の Compose で書き、ブラウザで動かす。
> - 案3: サーバーも JVM で動かし、native-image をやめる。
>
> 実装は Compose Multiplatform for Web（canvas 描画）を使う。
> DOM ベースの Compose HTML ではなく、Compose Desktop と同じ `androidx.compose.*` の
> API がそのまま使える方。`ComposeViewport` に描画する。
>
> 型の共有が必要になったら `:shared`（KMP: `jvm` + `wasmJs`）を作る。
> 中身は GraphQL のスキーマと、スキーマに書けない定数だけにする。

> ### ビルドと配布の分け方（決定済み）
> `:frontend` と `:backend` は別々にビルドする。Gradle 上で互いに依存させない。
> 成果物を 1 つにまとめるのはビルドの仕事ではなく、デプロイの仕事にする。
>
> - `:frontend` をビルドすると `frontend/build/dist/wasmJs/productionExecutable/` に出る
> - それをどこに置くかはインフラ側の話。ビルドの後に、インフラに合わせた
>   デプロイスクリプトで配置する（このリポジトリの外で用意する）
> - 配信するのは `:backend`。置き場所は環境変数（`STATIC_SRC_DIR`）で渡し、
>   サーバーはそのディレクトリを読む。バイナリには埋め込まない。
>   管理画面の成果物に限らず、フォントなど配信するファイルはここに置く
> - `:backend` は `:frontend` を知らない。ビルドにもテストにも Kotlin/Wasm の
>   ツールチェインは要らない。読むのは実行時のディレクトリだけ
>
> 当初は「`:backend` の `processResources` で `resources/static/` に取り込み、
> サーバーが配信して単一バイナリを維持する」と書いていたが、これはやめた。
>
> - サーバーの実装とテストが UI のツールチェイン（Node.js と yarn）に引きずられる。
>   wasm 側が壊れているとサーバーのテストも回せなくなる。実際に繋いでみたところ、
>   npm の lock がずれただけで backend と native-image のジョブまで落ちた
> - 単一バイナリの利点が薄い。DB ファイルも秘密鍵も既に外のファイルで、
>   配布は ghcr.io の Docker イメージ。バイナリ 1 つで完結してはいない

---

## Phase 1: 固定アクターが Mastodon から「見つかる」

ここが最初のフェデレーション検証ポイント。 署名も DB もまだ不要。静的な JSON を2つ返すだけ。

ActivityPub のアカウント発見は WebFinger → Actor の 2 ホップで行われる。

- [x] RSA 2048bit の鍵ペアを 1 組生成し、PEM でファイル or 環境変数に保存（固定。ローテーションは考えない）
      - 秘密鍵: PKCS#8 (`BEGIN PRIVATE KEY`)
      - 公開鍵: X.509 SubjectPublicKeyInfo (`BEGIN PUBLIC KEY`) ← Actor JSON にはこちらを入れる
      - 生成と PEM の相互変換は 0-6 で `:backend:crypto` の `RsaKeys` に用意済み。
        ここで決めるのは保存先と、起動時にどう読むか（無ければ生成するのか、必ず与えるのか）
      - 保存するのは秘密鍵だけにした。公開鍵は起動のたびに `RsaKeys.derivePublicKey` で導く。
        2 つ保存して片方だけ差し替わる事故を避けるため
      - 入口は環境変数。`ACTOR_PRIVATE_KEY_PATH`（既定 `./data/actor-private-key.pem`）か
        `ACTOR_PRIVATE_KEY_PEM` のどちらか。両方指定されたら起動時に落とす
      - パス指定でファイルが無ければ生成して書き出す。所有者だけが読める権限で作る。
        既にあるファイルは書き換えない
      - 取得元は起動ログに出す。生成したときだけ警告にして、鍵を失ったことに気付けるようにした
      - docker compose ではボリュームの中（`/data/actor-private-key.pem`）に置く
- [x] 1-2: ドメインとユーザー名を決めて起動時に確定させる
      - ユーザー名は `admin`。`ACTOR_USERNAME` で変えられる（既定 `admin`）。
        Phase 6 でフィードごとのアカウントを作るようになっても、
        このアカウントは運用者のアカウントとして残す。記事は流さない
      - `DOMAIN` は必須にして、未設定なら起動を止める。既定値を用意すると
        `localhost` が焼き込まれたアクター ID を配ることになり、
        Mastodon が永続キャッシュするので相手側からは直せない
      - URL の組み立ては `actor/ActorUrls.kt` に集約する。`acct:` / `id` /
        `#main-key` / inbox のどれか 1 つだけ綴りが違う、という壊れ方を防ぐ
- [x] `GET /.well-known/webfinger?resource=acct:admin@example.com`
      - Content-Type: `application/jrd+json`
      - `subject` は正規化した `acct:` を返す。要求された綴りをそのまま返すと
        大文字小文字が混ざったまま相手側の突き合わせで揺れる
      - `links` に `rel: "self"`, `type: "application/activity+json"`, `href: <Actor の URL>`
      - 未知の resource は 404。`resource` 自体が無ければ 400
      - `acct:` 無しの `admin@example.com` と Actor の URL でも引けるようにした
- [x] `GET /users/admin`（Actor エンドポイント）
      - Content-Type: `application/activity+json`
      - `@context`: `["https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"]`
      - `id` / `type: "Service"` / `preferredUsername` / `name` / `summary`
      - `inbox` / `outbox` / `followers` / `following`
      - `publicKey`: `{ id: "<actor>#main-key", owner: "<actor>", publicKeyPem: "..." }`
      - `preferredUsername` は WebFinger の acct 名と一致させること
      - 公開鍵は起動時に秘密鍵から導いたものをそのまま入れる
- [x] `Accept` ヘッダで content negotiation（`application/activity+json` と `application/ld+json` を受ける）
      - 0-3 で用意した `ActivityPubContentTypes.negotiate()` を繋いだ
- [x] 動作確認用に `test-` で始まる名前を全部アクターとして応答させる
      - Mastodon は内容を間違えたまま一度取得すると相手側からは直せない。
        `admin` で試して失敗すると `admin` が使えなくなるので、
        名前を変えながらやり直せる口を用意した
      - 設定での切り替えにはしていない。検証したいときに限って無効なまま
        404 を見て悩むことになるため。中身は固定アクターと同じで鍵も共有する
      - 接頭辞は小文字ちょうど。`Test-1` を受けると `test-1` と別のアクターが生える
      - 引き当ては `actor/ActorDirectory.kt`。WebFinger とパスで判定がずれると
        「検索には出るが開けない」という分かりにくい壊れ方をするので 1 箇所に通す
      - **Phase 6 で消す**（下記）
- [x] CI の native 起動確認に WebFinger と Actor を足す
      - `@SerialName` とカスタム serializer は native-image で解決に失敗すると 500 になる。
        JVM のテストでは分からないので、native バイナリを実際に叩いて中身を見る
      - `DOMAIN` を渡さないと起動しないことも確認する
- [ ] HTTPS で外部公開する経路を用意（開発中は Cloudflare Tunnel / ngrok など）
      - ドメインは早めに固定する。 アクター ID にドメインが焼き込まれ、Mastodon 側にキャッシュされるため
- [ ] `GET /.well-known/nodeinfo` + `/nodeinfo/2.1`（任意だが実装しておくと調査が楽）

### ✅ チェックポイント 1
Mastodon の検索窓に `@admin@example.com` と入力して、プロフィールカードが表示される。
（この時点ではフォローボタンを押しても成立しない。それが Phase 2）

> テスト時の注意
> Mastodon はリモートアクターを永続キャッシュする。開発中にアクターの内容や鍵を変えても即座には反映されない。
> 試行錯誤のたびに `ACTOR_USERNAME` を `feed1`, `feed2`, ... と変えるのが最も手戻りが少ない。
> 検証相手は自分で立てた Mastodon（docker compose）か、テスト用途を許容する小規模インスタンスを使うこと。

---

## Phase 2: フォローが成立する（HTTP Signatures）

ActivityPub のサーバー間通信は HTTP Signatures (draft-cavage-http-signatures) で認証する。
「受信の検証」と「送信の署名」の両方が必要。ここが実装の山場。

- [ ] `POST /users/admin/inbox` を受ける（まずは中身をログに落とすだけ）
- [ ] 署名の検証（受信）
      - [ ] `Digest: SHA-256=<base64>` ヘッダとボディの SHA-256 を突き合わせる
      - [ ] `Signature` ヘッダをパース（`keyId`, `algorithm`, `headers`, `signature`）
      - [ ] `keyId`（例: `https://mastodon.social/users/foo#main-key`）のアクターを GET して公開鍵を取得
      - [ ] `headers` の並び順どおりに署名文字列を再構築
            - `(request-target): post /users/admin/inbox`
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

## jOOQ を採用するかの判断（Phase 3 に入る前に決める）

当初は Phase 0 の 0-6 で codegen を組む予定だったが、Phase 0 から外した。
理由は、この時点のスキーマが `health_check` と `schema_version` だけで生成しても使う場所が無く、
native-image のリフレクション設定という負債だけが先に増えるため。
スキーマが実際に必要になる Phase 3 の直前なら、テーブルの数と SQL の複雑さを見てから決められる。

判断の材料:

- Phase 3 で増えるのは `actors` / `remote_actors` / `followers` / `deliveries` の 4 テーブル。
  この規模なら素の JDBC で書ききれる可能性がある
- `:backend:repository` はすでに素の JDBC で完結していて、native バイナリでも動いている。
  jOOQ を入れると native-image の未知のリスクが 1 つ戻ってくる
- 一方で配信キューの状態遷移や、フォロワーのページングは SQL が込み入るので、
  型のある DSL の恩恵が効く場面ではある

採用する場合にやること:

- [ ] codegen のパイプラインを組む
      1. 一時 SQLite ファイルを作る（`build/jooq/schema.db`）
      2. `db/migration` の SQL を順に適用する
      3. その DB を入力に jOOQ codegen を実行する
      4. 出力を `build/generated/jooq` に置き、`:backend:repository` の sourceSet に加える
- [ ] `compileKotlin` が codegen タスクに依存するようにする（初回ビルドで生成物が無くて落ちないように）
- [ ] マイグレーション SQL が変わったら codegen が再実行されるよう入力を宣言する（up-to-date チェックを効かせる）
- [ ] 生成コードは git 管理しない（`build/` 配下なので `.gitignore` 済み）
- [ ] jOOQ の SQLite dialect を使う（OSS 版で対応している）
- [ ] `nu.studer.jooq` プラグインを使うか、素の `JavaExec` で回すかを決める
      - プラグインは楽だが Gradle との相性問題を踏むことがある。素の `JavaExec` + `configuration` の方が読める場合もある
- [ ] jOOQ のログ設定を入れる（何もしないと起動時にバナーと警告が出る）
- [ ] jOOQ のリフレクション設定（`reflect-config.json`）を用意する
- [ ] native バイナリで jOOQ 経由のクエリが動くことを確認する

採用しない場合にやること:

- [ ] 「使用技術」の表と README から jOOQ を落とす
- [ ] SQL を書く場所の決まりを `:backend:repository` の中で決める（文字列定数か、専用のファイルか）

---

## Phase 3: フォロワーを永続化する

Phase 2 まではオンメモリでよい。ここで初めて DB が要る。

- [ ] スキーマ設計とマイグレーション
      - `actors`（ローカルアクター: name, display_name, private_key, public_key, created_at）
      - `remote_actors`（inbox, shared_inbox, public_key, fetched_at）
      - `followers`（actor_id, remote_actor_id, follow_activity_id, state, created_at）
      - `deliveries`（配信キュー: target_inbox, payload, attempts, next_retry_at, state）
- [ ] follow を INSERT / UPDATE（jOOQ を採用したならその DSL で）
- [ ] `Undo{Follow}` を処理してフォロー解除
- [ ] `Delete{Actor}`（アカウント削除・引っ越し）を処理してフォロワーを掃除
      - 削除済みアクターは鍵を取得できないので、署名検証に失敗しても握り潰す例外パスが要る
- [ ] `GET /users/admin/followers`（OrderedCollection、ページング）
- [ ] 冪等性: 同じ `Follow` を二重に受けても重複行を作らない（activity id で一意制約）
- [ ] フォロワーがいるなら鍵の自動生成を拒否して起動を止める
      - 1-1 の鍵の生成条件は「ファイルが無い」だけなので、鍵を失った状態でも
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
- [ ] 投稿を発火させる管理用エンドポイント（開発用。あとで UI から叩く）

### ✅ チェックポイント 4
フォロワーのホームタイムラインに投稿が現れ、リンクをクリックできる。

---

## Phase 5: RSS を取り込んで自動投稿する

ここでようやく本来の機能。ActivityPub 側はもう触らない。

- [ ] RSS 2.0 / Atom 1.0 のパーサを自作（`javax.xml` の StAX か DOM）
      - 両フォーマットの差分吸収（`item`/`entry`, `pubDate`/`updated`, `description`/`summary`/`content`）
- [ ] `feeds` / `feed_items` テーブル
      - この時点ではアクターが 1 つしか無いので、検証はフィード 1 本で行う。
        `ACTOR_USERNAME` にそのフィード用の名前を入れて動かす
      - `admin` から記事を流さないこと。`admin` は運用者のアカウントで、
        ここで記事を流すとフォロワーが付き、Phase 6 で分割したときに
        その人たちには何も届かなくなる（1 アカウントから複数への分割は
        `Move` では表現できず、引っ越しを通知する手段が無い）
      - 複数フィードを同時に動かせるようになるのは Phase 6。
        `feeds.actor_id` を足してフィードごとのアクターに振り分ける
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

「全体像」の案A をここで実現する。 **RSS フィード 1 本につきアカウントを 1 つ**作り、
利用者は読みたいフィードのアカウントだけをフォローする。Phase 1 で固定していた部分を動的にする。

Phase 1〜5 で作った `admin` はフィード用ではなく、**運用者のアカウント**として残す。
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
      - `admin` のような予約名と、既存アクターとの重複を登録時に弾く
- [ ] アクターを DB 駆動に変更（起動時ハードコードをやめる）
      - `ActorUrls` はドメインとユーザー名から組み立てているので、
        ユーザー名の出どころを設定から DB に差し替える形になる
- [ ] WebFinger を動的解決（任意の `acct:` を DB 引きして応答）
- [ ] アクターごとに鍵ペアを生成して保存
      - Phase 1 の鍵はファイル 1 本。ここで `actors.private_key` に移すかを決める
        （Phase 3 の「フォロワーがいるなら鍵の自動生成を拒否する」と合わせて判断する）
- [ ] アクター作成 / 削除の API
      - 削除時は `Delete{Actor}` を配信してから消す。黙って消すと相手側に残り続ける
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
- [ ] Phase 1 で入れた `test-` の使い捨てアクターを消す
      - `ActorUsername.isTest` と `ActorDirectory` の該当分岐、README の
        「動作確認用のアカウント」、CI の起動確認、テストを一緒に落とす
      - アクターを DB から作れるようになれば、検証用のアカウントも
        普通に作って消せるので役目が終わる
      - 消し忘れると、誰でも `test-<任意>` を引けるアカウントが本番に残る。
        Phase 3 でフォロワーを永続化した後だと、使い捨てのつもりの
        アクターにフォロワーが付く

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
- [ ] `:shared` モジュール（KMP: `jvm` + `wasmJs`）を作る
      - 中身は GraphQL のスキーマ（`src/commonMain/resources/graphql/schema.graphqls`）と、
        スキーマに書けない定数（パスワードの長さ制限、環境変数名、画面のパス）だけ
      - 両モジュールから等距離にするため root に置く。`:backend` に置くと
        `:frontend` のビルドが `:backend` のディレクトリを見ることになる
- [ ] `:frontend` の成果物を配置するデプロイスクリプトを用意する
      （インフラ側で用意する。このリポジトリの範囲外。Phase 0 の「ビルドと配布の分け方」を参照）
- [ ] `:backend` が静的ファイルを配信する
      - 置き場所は環境変数 `STATIC_SRC_DIR` で渡す。バイナリには埋め込まない
      - 管理画面だけの口にしない。フォントなど配信するファイルはここにまとめて置く。
        名前も `ADMIN_` を付けず、置き場所を指す名前にする
      - 未設定またはディレクトリが無いときは 404 にして、起動ログに出す。
        黙って動くと「画面が出ない」原因が分からなくなる
      - `.wasm` は `application/wasm` で返す。`application/octet-stream` だと
        ブラウザが `WebAssembly.instantiateStreaming` に渡せず画面が真っ白になる
      - 画面の中のパス（`/admin/password-hash` など）はファイルとして存在しないので、
        不明なパスには `index.html` を返す。SPA の常の設定
      - パスの正規化に注意する。リクエストのパスをそのまま連結すると、
        `..` でディレクトリの外を読み出せてしまう
- [ ] 画面をどのパスに置かれても動くようにする
      - `index.html` の参照と webpack の `publicPath` を root 絶対（`/frontend.js`）にする。
        相対のままだと末尾スラッシュの有無で参照先が変わり、`/admin` へのリダイレクトが要る
- [ ] サーバー側に管理 API（フィード CRUD、アクター一覧、配信状況、手動再取得）
- [ ] 管理 API を GraphQL にする（[kake-bo](https://github.com/matsudamper/kake-bo) と揃える）

      スキーマ優先。サーバーは graphql-java、クライアントは Apollo Kotlin で、
      どちらも `:shared` の同じ `.graphqls` から作る。REST は作らない。

      エンドポイントは `/graphql` の 1 つで、管理用とそれ以外はフィールドで分ける
      （管理用は `Query.admin` / `Mutation.admin` の下）。認可はエンドポイントではなく
      フィールドごとに見る。ActivityPub 側は相手の実装が決まっている REST なので触らない。

      native-image への影響に注意する。kake-bo は JVM で動くので graphql-java-tools
      (kickstart) と kobylynskyi の codegen を使い、リゾルバをリフレクションで
      結線しているが、この構成は native-image では動かない。`RuntimeWiring` に
      `DataFetcher` を明示して結線し、フィールドの取り出しも値を `Map` で返して
      `PropertyDataFetcher` のリフレクション経路に入れない。データクラスを返すと、
      JVM では動いて native バイナリでだけ全フィールドが null になる形の不具合になる。

      - [ ] スキーマを `:shared` に置き、version catalog に graphql-java と Apollo を足す
      - [ ] `POST /graphql` を 1 つ作る。本文は `receiveText()` してから読み、
            変数は `JsonObject` で受けて実行の直前に素の値へ開く
      - [ ] 実行結果の `Map` を `JsonElement` に変換して返す。知らない型が来たら落とす
      - [ ] スキーマを `resource-config.json` に登録する（マイグレーション SQL と同じ扱い）
      - [ ] CI の native-image ジョブの起動確認で実際に叩く。
            graphql-java が native-image で動くかは JVM のテストでは分からない
- [ ] 管理 API に認証をかける（inbox と違って外に開けてはいけない）
      - パスワード 1 つ + セッション。ハッシュは `ADMIN_PASSWORD_HASH` に入れる
      - ハッシュは `:backend:crypto` の `PasswordHash`（PBKDF2-HMAC-SHA256）で作る。部品は用意済み
      - ハッシュ未設定でも起動できるようにする。最初の 1 つを作る手段が他に無いので、
        未設定の間だけハッシュ生成を認証なしで開ける。設定後はログインした人だけ
      - セッションの持ち方（メモリ上のトークン / 署名付き Cookie）は実装時に決める
      - 総当たり対策（試行回数の制限）は Phase 7 で入れる
- [ ] 画面遷移を Navigation Compose 3 にする
      - `org.jetbrains.androidx.navigation3:navigation3-ui`（JetBrains 版。wasmJs 向けの成果物がある）
      - 画面のキーを sealed interface で定義し、`NavDisplay` + バックスタックで切り替える
      - URL との同期は自前で持つ。Navigation3 はバックスタックを扱うだけで URL は見ない
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
      - 環境変数に寄せた。`ServerConfig` と `DatabaseConfig` が入口
      - ポーリング間隔は Phase 5 でフィードごとに持つので、ここには入れない
- [x] Dockerfile と docker-compose.yml
      - multi-stage build。GraalVM のステージで native バイナリを作り、
        実行用のステージ（debian:13-slim）には JDK を持ち込まない
      - DB は名前付きボリューム。`HEALTHCHECK` で `/healthz` を叩く
- [x] `main` へのマージで GitHub Packages（ghcr.io）にイメージを publish する
      - タグは `latest` と commit SHA。戻せるよう latest だけにはしない
      - コンテナまわりを触った PR ではビルドと起動確認だけ走らせる
- [ ] systemd unit（コンテナを使わない場合の起動方法）
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
| native-image で落ちる | リフレクション設定不足（`@Serializable` 型の登録漏れなど）・SQLite ネイティブライブラリ・jOOQ を入れた場合はその設定 |
| JVM のテストは通るのに native だけ落ちる | テストが native で実行されていない。`nativeTest` の対象に入れられないか検討する |
| native バイナリでマイグレーションが動かない | SQL がリソースとして同梱されていない（`resource-config.json` 未登録）/ jar 内ディレクトリ走査に頼っている |
| ログが 1 行も出ない | SLF4J の実装が classpath に無い。`No SLF4J providers were found` が出て以降すべて NOP になる |
| 外部キー制約が効かない | SQLite は `PRAGMA foreign_keys` が既定で OFF。接続ごとに ON にする必要がある |
| `SQLITE_BUSY` が出る | ライターを複数持っている / `busy_timeout` 未設定 |

## 参考仕様

- ActivityPub — https://www.w3.org/TR/activitypub/
- Activity Streams 2.0 / Vocabulary — https://www.w3.org/TR/activitystreams-core/
- WebFinger (RFC 7033) — https://datatracker.ietf.org/doc/html/rfc7033
- HTTP Signatures (draft-cavage-http-signatures-12) — Mastodon が実装しているのはこのドラフト版
- Mastodon 実装ドキュメント — https://docs.joinmastodon.org/spec/activitypub/
