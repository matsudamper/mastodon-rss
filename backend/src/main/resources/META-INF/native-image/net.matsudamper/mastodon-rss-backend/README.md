# native-image の設定

置いてあるのは `resource-config.json` だけ。リフレクションの登録が要るのは GraphQL の
結線だけで、そちらは設定ファイルではなくビルド時にクラスパスを走査して登録する
（`graalvm/GraphQlReflectionFeature`）。手で書く `reflect-config.json` を増やさない
ための経緯をここに残す。

## `resource-config.json` に入っているもの

native バイナリにはリソースが自動では入らない。明示していないものは実行時に
「無い」ものとして振る舞い、JVM では動くのでビルドまで気付けない。

- 管理 API のスキーマ (`graphql/*.graphqls`)。`GraphQlEngine.create` が起動時に読む
- 読むスキーマの一覧 (`graphql/schema-list.txt`)。`:backend:graphql` がビルド時に作る。
  native バイナリではディレクトリを列挙できないので、`graphql/` の中身を実行時に
  数え上げる手段が無い
- graphql-java のメッセージ (`i18n.*`)。エラー文用に見えるが `SchemaParser` が
  スキーマを読む時点で `i18n.Parsing` を引くので、無いと起動した瞬間に
  `MissingResourceException` で落ちる

どれも JVM のテストは通るので、CI の native-image ジョブの起動確認が唯一の検出手段になる。

## GraphQL の結線だけはリフレクションを使う

管理 API はスキーマ優先で、モデルとリゾルバのインタフェースを
kobylynskyi の graphql-java-codegen が作り、graphql-java-tools (kickstart) が
スキーマのフィールドとリゾルバのメソッドを対応付ける。この対応付けはリフレクションを
使うので、native-image 向けにクラスを登録する。

登録は `--features=net.matsudamper.mastodon.rss.graalvm.GraphQlReflectionFeature` で
渡す Feature が、イメージのビルド時に次の 2 つのパッケージを走査して行う。

- `net.matsudamper.mastodon.rss.graphql.model`（生成されたモデルとインタフェース）
- `net.matsudamper.mastodon.rss.graphql.resolver`（リゾルバの実装）

ここに入っていないクラスは登録されない。リゾルバの実装が
`graphql.resolver` から出ていないかは `GraphQlReflectionTargetsTest` が見ている。

手で並べないのは、スキーマにフィールドや型を足すたびに更新が要るため。
生成物とリゾルバをまとめて走査すれば忘れようがない。

以前は `RuntimeWiring` に `DataFetcher` を明示し、フィールドの値を `Map` で返して
リフレクションを一切使わない形にしていた。その理由として「kickstart や
kobylynskyi の codegen は native-image で動かない」と書いてあったが、
実際に試した記録は無く、根拠の無い決めつけだった。実際に動かしたところ、
下に並べた 3 つを足せば動く。

### kickstart を動かすのに足したもの

native バイナリを実際に動かして、落ちるたびに 1 つずつ足した。どれも JVM では
起きない。JVM のテストはリフレクションの経路を通るが、必要なクラスとリソースが
最初からクラスパスに居るので何も起きない。

1. リゾルバとモデルのリフレクション登録（上に書いた Feature）

2. `kotlin.reflect.jvm.internal.ReflectionFactoryImpl` の登録

   kickstart はリゾルバの引数の数を数えるのに `ReflectJvmMapping.getKotlinFunction`
   （kotlin-reflect）を使う。`kotlin.jvm.internal.Reflection` は実装を
   `Class.forName` + `newInstance` で探すので、登録が無いと実装が見つからない。

       KotlinReflectionNotSupportedError: Kotlin reflection implementation is not
       found at runtime. Make sure you have kotlin-reflect.jar in the classpath
         at graphql.kickstart.tools.resolver.FieldResolverScanner.getMethodParameterCount

   jar 自体はクラスパスに居る（kickstart の推移依存）。登録だけが足りていなかった。

3. `*.kotlin_builtins` と `*.kotlin_module` をリソースとして同梱する

   kotlin-reflect は Kotlin の組み込み宣言を kotlin-stdlib の中の
   `kotlin/kotlin.kotlin_builtins` などから読む。上の 2 を足すと実装は
   見つかるようになるが、その先で組み込み宣言が読めずに落ちる。

       java.lang.AssertionError: Built-in class kotlin.Any is not found
         at kotlin.reflect.jvm.internal.impl.builtins.KotlinBuiltIns.getBuiltInClassByName

### jOOQ と Jackson の組み合わせ

kickstart は jackson-databind を連れてくる。jOOQ は JSON 型の変換に Jackson を
使えるようになっていて、クラスパスに居ると `Convert$_JSON` が `ObjectMapper` を
作って static final に持つ。`--initialize-at-build-time=org.jooq` があるので、
その実体がイメージヒープに載ってビルドが止まる。

    An object of type 'com.fasterxml.jackson.databind.json.JsonMapper' was found
    in the image heap. This type, however, is marked for initialization at image
    run time

`--initialize-at-run-time=org.jooq.impl.Convert$_JSON` で holder クラスだけ外した。
Jackson をビルド時初期化にして通す手もあるが、`ObjectMapper` の設定とキャッシュを
イメージに焼き込むことになる。こちらは jOOQ で JSON 型を使っていないので、
holder ごと実行時に回す方を選んだ。

Jackson がクラスパスに無かった間はこの経路に入らなかったので、GraphQL の
ライブラリを足した副作用で出たことになる。依存を足したら native ビルドを
通すこと。

## かつて必要だった理由

Ktor の `ContentNegotiation` は `call.respond(value)` の際、値の `KType` から
serializer をリフレクションで探す。具体的には `Foo$Companion` か `Foo$$serializer`
の `INSTANCE` を実行時に引く。

native-image はここに到達できないため、登録が無いとリクエスト時に
`Serializer for class 'Foo' is not found.` となり 500 が返る。
JVM では問題なく動くので、native バイナリを起動して初めて分かる。

当初は `@Serializable` な型ごとに `Foo` / `Foo$Companion` / `Foo$$serializer` の
3 つを `reflect-config.json` に登録して回避していた。

## いまの方針（kotlinx.serialization）

`ContentNegotiation` を使わず、`call.respondJson(Foo.serializer(), value)` のように
serializer を明示する。コンパイル時に serializer が決まるのでリフレクションが発生せず、
`reflect-config.json` も `--initialize-at-build-time` も要らない。
実体は `backend/feature-mastodon/src/main/kotlin/net/matsudamper/mastodon/rss/json/JsonResponse.kt`。

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
