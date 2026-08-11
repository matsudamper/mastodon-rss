# native-image の設定

置いてあるのは `resource-config.json` だけ。`:backend` はリフレクションに依存しない
作りにしてあり、`reflect-config.json` は要らない。その状態を保つための経緯をここに残す。

## `resource-config.json` に入っているもの

native バイナリにはリソースが自動では入らない。明示していないものは実行時に
「無い」ものとして振る舞い、JVM では動くのでビルドまで気付けない。

- 管理 API のスキーマ (`graphql/schema.graphqls`)。`GraphQlEngine.create` が起動時に読む
- graphql-java のメッセージ (`i18n.*`)。エラー文の組み立てにしか使わないように見えるが、
  `SchemaParser` はスキーマを読む時点で `i18n.Parsing` を引く。登録が無いと
  起動した瞬間に `MissingResourceException: Can't find bundle for base name i18n.Parsing`
  で落ちる。JVM のテストは全部通るので、CI の native-image ジョブの起動確認が唯一の検出手段になる

third-party ライブラリの設定を配る GraalVM Reachability Metadata Repository を
有効にすれば graphql-java のぶんは向こうが持っているかもしれないが、無効のままにしている。
理由は `backend/build.gradle.kts` のコメントを参照。5 行で済むものを取り込むために
仕組みを 1 つ増やす方が高くつく。

## かつて必要だった理由

Ktor の `ContentNegotiation` は `call.respond(value)` の際、値の `KType` から
serializer をリフレクションで探す。具体的には `Foo$Companion` か `Foo$$serializer`
の `INSTANCE` を実行時に引く。

native-image はここに到達できないため、登録が無いとリクエスト時に
`Serializer for class 'Foo' is not found.` となり 500 が返る。
JVM では問題なく動くので、native バイナリを起動して初めて分かる。

当初は `@Serializable` な型ごとに `Foo` / `Foo$Companion` / `Foo$$serializer` の
3 つを `reflect-config.json` に登録して回避していた。

## いまの方針

`ContentNegotiation` を使わず、`call.respondJson(Foo.serializer(), value)` のように
serializer を明示する。コンパイル時に serializer が決まるのでリフレクションが発生せず、
`reflect-config.json` も `--initialize-at-build-time` も要らない。
実体は `backend/src/main/kotlin/net/matsudamper/mastodon/rss/json/JsonResponse.kt`。

受信側も同じ理由で `call.receive<Foo>()` のように `@Serializable` な型を直接は受けない。
inbox は `call.receive<ByteArray>()` で生のバイト列を受け、
`AppJson.decodeFromString(Foo.serializer(), body.decodeToString())` で読む。

ボディを文字列ではなくバイト列で受けるのは、HTTP Signature の `Digest` が
バイト列に対して計算されているため。`receiveText()` を経由すると文字コードの
解釈が挟まり、送信側が署名した内容と一致しなくなる余地が残る。
`ByteArray` は Ktor が組み込みで変換する型なので、serializer のリフレクションは発生しない。

`Accept` に応じた Content-Type の選択は `ContentNegotiation` の代わりに
`ActivityPubContentTypes.negotiate()` で行う。

## `--initialize-at-build-time=kotlin.DeprecationLevel` は残っている

`backend/build.gradle.kts` にこの引数がある。以前は `reflect-config.json` への登録が
原因だと考えていたが、登録を全て消しても再現したので別の話だった。

native-image は解析中に自分で `isAnnotationPresent` を呼ぶ（`PodFeature.isPodClass`）。
そこで Kotlin の `@Deprecated` のデフォルト値が読まれ、`level` の型である
`DeprecationLevel` enum がビルド時に初期化される。GraalVM 21 の既定は実行時初期化なので
衝突してビルドが落ちる。

    Error: Classes that should be initialized at run time got initialized during image building:
    kotlin.DeprecationLevel was unintentionally initialized at build time

Kotlin のクラスが解析対象にあれば起きるため、リフレクションを使わなくなっても要る。
値を持たない enum なのでビルド時初期化にして問題ない。
原因の特定には `--trace-class-initialization=kotlin.DeprecationLevel` を使った。

## フィードの取り込みを繋ぐときに要る指定

`:backend:rss` を `:backend` から使うようになったら、`backend/build.gradle.kts` の
native-image の引数に `-H:+AddAllCharsets` を足すこと。

native バイナリには既定で一部の文字コードしか入らない。RSS の配信元には
Shift_JIS や EUC-JP がまだあり、XML 宣言のとおりに読もうとした時点で
`UnsupportedCharsetException: Shift_JIS` になる。JVM のテストでは通るので、
繋いだ後に本番のフィードで初めて分かることになる。

`:backend:rss` の `nativeTest` には同じ指定を入れてあり、これが無いと
Shift_JIS のテストが native でだけ落ちる（実際にそうやって見つけた）。

## それでもリフレクションで詰まったら

native バイナリだけが落ちる／500 を返す場合は、まず何がリフレクションを
必要としているかを特定する。GraalVM の tracing agent を使うのが早い。

    ./gradlew :backend:run -Pagent
    # アプリを一通り叩いてから停止する
    ./gradlew :backend:metadataCopy --task run \
      --dir backend/src/main/resources/META-INF/native-image/net.matsudamper/mastodon-rss-backend

出力は Ktor 内部のクラスまで拾うので、そのままコミットせず原因の特定に使う。
可能なら設定を足すのではなく、リフレクションを使わない書き方に直す。

third-party ライブラリ向けの設定を配る
[GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)
は `metadataRepository` で無効にしている。理由は `backend/build.gradle.kts` のコメントを参照。
