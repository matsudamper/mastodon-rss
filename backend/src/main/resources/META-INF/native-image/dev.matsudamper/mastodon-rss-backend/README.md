# native-image の設定

このディレクトリに設定ファイルは置いていない。`:backend` はリフレクションに依存しない
作りにしてあり、`reflect-config.json` を必要としないため。
その状態を保つための経緯をここに残す。

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
実体は `backend/src/main/kotlin/dev/matsudamper/mastodonrss/json/JsonResponse.kt`。

受信側も同じ理由で `call.receive<T>()` を使わない。加えて Phase 2 の inbox は
HTTP Signature の Digest 検証のために生のボディが必要なので、
`receiveText()` してから `AppJson.decodeFromString(Foo.serializer(), body)` で読む。

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

## それでもリフレクションで詰まったら

native バイナリだけが落ちる／500 を返す場合は、まず何がリフレクションを
必要としているかを特定する。GraalVM の tracing agent を使うのが早い。

    ./gradlew :backend:run -Pagent
    # アプリを一通り叩いてから停止する
    ./gradlew :backend:metadataCopy --task run \
      --dir backend/src/main/resources/META-INF/native-image/dev.matsudamper/mastodon-rss-backend

出力は Ktor 内部のクラスまで拾うので、そのままコミットせず原因の特定に使う。
可能なら設定を足すのではなく、リフレクションを使わない書き方に直す。

third-party ライブラリ向けの設定を配る
[GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)
は `metadataRepository` で無効にしている。理由は `backend/build.gradle.kts` のコメントを参照。
