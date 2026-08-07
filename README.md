# mastodon-rss
RSS/Atom フィードを ActivityPub アクターとして配信し、Mastodon からフォローできるようにする自作サーバー。

開発の進め方とロードマップは [TODO.md](TODO.md) を参照。

## モジュール構成

| モジュール | 内容 |
| --- | --- |
| `:backend` | Ktor (CIO) のサーバー。GraalVM native-image でビルドする |
| `:repository` | SQLite への DB アクセス。公開するのは interface だけで、JDBC や SQL は外に出さない |
| `:frontend` | Compose Multiplatform for Web (Kotlin/Wasm) の管理画面 |

## 環境変数

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | バインドするアドレス |
| `PORT` | `8080` | 待ち受けポート |
| `DB_PATH` | `./data/mastodon-rss.db` | SQLite の DB ファイル。親ディレクトリは起動時に作られる |

## 必要なもの

- JDK 21
- native-image をビルドする場合は GraalVM 21

Gradle は wrapper が入っているので個別のインストールは不要。

## ビルド

### 全体

```sh
./gradlew build
```

### backend

```sh
# ビルドとテスト
./gradlew :backend:build

# テストのみ
./gradlew :backend:test

# repository のビルドとテスト
./gradlew :repository:build

# JVM で起動する（http://localhost:8080）
./gradlew :backend:run
```

### backend の native-image

GraalVM 21 が必要。

```sh
# ネイティブバイナリを生成する
./gradlew :backend:nativeCompile

# 生成されたバイナリを起動する
./backend/build/native/nativeCompile/mastodon-rss
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

## マイグレーション

スキーマ変更は `repository/src/main/resources/db/migration/` に
`V002__説明.sql` のような連番のファイルを足す。起動時に未適用のものが
バージョン昇順で適用され、適用済みのバージョンは `schema_version` テーブルに記録される。

ファイル名の一覧 (`db/migration/index`) は Gradle が生成するので、手で書く必要はない。
これは jar 内のディレクトリ走査が native-image で動かないことがあるため。

適用済みのファイルは書き換えないこと。チェックサムを記録しているので、
変更すると次の起動時にエラーになる。修正は新しい連番のファイルで行う。

## その他

```sh
# 生成物を消す
./gradlew clean

# Gradle デーモンを止める
./gradlew --stop
```
