# 設計

コードの 1 か所に紐付かない、横断的な決めごとを置く。個別の判断はコードの KDoc と
ビルドスクリプトのコメントに書いてあるので、こちらには重複させない。

使い方は [README.md](../README.md)、これからやることは [TODO.md](../TODO.md) を参照。

## モジュールの分け方

`:backend` から見えるのは `:backend:repository` の公開 API だけ。実装は `internal` で、
sqlite-jdbc も `implementation` で入れているため、JDBC の型は `:backend` の
compile classpath にも現れない。

`:backend:crypto` は `:backend` がアクターの鍵を読むために使っている。HTTP Signatures の
署名と検証で使うのは Phase 2 から。別モジュールに切り出してあるのは、
テストを native バイナリとして実行するため。`:backend` のテストは
`ktor-server-test-host` 経由で ByteBuddy と JNA を引き込み、これらは実行時の
バイトコード書き換えに依存するので native-image では動かない。JCA の確認を
そこに同居させると確認できなくなる。

環境変数を読むのは `:backend` の入口（`AppConfig`）だけにする。`:backend:repository` の
ような下位のモジュールは、値を引数で受け取る。

`:frontend` と `:backend` のビルドを繋がないのは、繋ぐとサーバーのテストが
Kotlin/Wasm のツールチェイン（Node.js と yarn）に引きずられるため。wasm 側が
壊れているとサーバーのテストも回せなくなる。配信は実行時のディレクトリを読む形にして、
ビルドの依存を作らない。詳細は [TODO.md](TODO.md) の「ビルドと配布の分け方」を参照。

## 起動時の流れ

```mermaid
sequenceDiagram
    participant M as main
    participant A as ActorKeyLoader
    participant R as Repositories
    participant DB as SQLite
    participant K as Ktor CIO

    Note over M: 環境変数を読む（DOMAIN が無ければここで落ちる）
    M->>A: load
    A->>A: PEM を読む（ファイルが無ければ生成して書き出す）
    M->>R: createRepositories
    R->>DB: 接続して PRAGMA を適用
    R->>DB: 未適用のマイグレーションをバージョン昇順で適用
    M->>K: embeddedServer で起動
    K->>M: module を実行
    M->>R: verifyWritable
    R->>DB: health_check に書いて読み戻す
    Note over K: リクエスト受付開始
```

DB を開けなかった場合もマイグレーションに失敗した場合も、この時点で例外になって
起動が止まる。native バイナリでは SQLite のネイティブライブラリの展開に失敗しても
起動自体は通ってしまうことがあるため、書き込みの往復まで確かめている。

鍵は DB より先に読む。鍵を用意できないならサーバーを立てても意味が無いので、
先に落とすため。`DOMAIN` が無い場合も同じ理由でそれより前に落ちる。

ログは slf4j-simple で標準エラーに出る。SLF4J の実装を入れていないと Ktor 自身の
ログも含めて何も出ないため、実装を 1 つだけ入れている。logback にしないのは、
設定ファイルの読み込みに native-image 側の追加対応が要るため。
