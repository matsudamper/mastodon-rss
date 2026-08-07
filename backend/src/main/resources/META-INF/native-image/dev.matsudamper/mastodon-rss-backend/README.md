# native-image の設定

`reflect-config.json` は JSON にコメントを書けないので、経緯をここに残す。

## なぜ必要か

Ktor の `ContentNegotiation` は `call.respond(value)` の際、値の `KType` から
serializer をリフレクションで探す。具体的には `Foo$Companion` か `Foo$$serializer`
の `INSTANCE` を実行時に引く。

native-image はここに到達できないため、登録が無いとリクエスト時に
`Serializer for class 'Foo' is not found.` となり 500 が返る。
JVM では問題なく動くので、native バイナリを起動して初めて分かる。

## 登録するもの

`@Serializable` を付けた型を 1 つ増やすたびに、次の 3 つを足す。

- `Foo` 本体
- `Foo$Companion`
- `Foo$$serializer`

## 付随して必要になった設定

`backend/build.gradle.kts` の `--initialize-at-build-time=kotlin.DeprecationLevel`
はこのファイルとセット。

native-image は reflect-config に登録されたクラスのアノテーションを解析する。
Kotlin のクラスを登録すると `@Deprecated` のデフォルト値経由で
`kotlin.DeprecationLevel` enum がビルド時に初期化され、
GraalVM 21 の既定である実行時初期化と衝突してビルドが落ちる。

  Error: Classes that should be initialized at run time got initialized during image building:
  kotlin.DeprecationLevel was unintentionally initialized at build time

値を持たない enum なのでビルド時初期化にして問題ない。
原因の特定には `--trace-class-initialization=kotlin.DeprecationLevel` を使った。

## 今後

手で管理するのは型が増えると破綻するので、TODO.md の 0-7 で GraalVM の
tracing agent を Gradle タスク化し、収集した設定に置き換える予定。
それまでは新しい `@Serializable` 型を追加したらここも更新すること。
