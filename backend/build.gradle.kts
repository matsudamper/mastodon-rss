plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("mastodon-rss.native-image")
}

dependencies {
    implementation(project(":backend:repository"))
    implementation(project(":backend:crypto"))

    implementation(project(":backend:graphql"))
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    // inbox の署名検証で、相手の keyId を GET して公開鍵を取りに行く。
    // engine はサーバーと揃えて CIO にする
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.kotlinx.serialization.json)

    // GraphQlReflectionFeature を書くための API
    compileOnly(libs.graalvm.nativeimage)

    // InboxService のように Ktor のルーティングから切り離したクラスは
    // Application.log を持たないので、SLF4J のロガーを直接引く。
    // ktor-server-core の推移依存でも見えるが、直接 import するなら明示する
    implementation(libs.slf4j.api)

    // SLF4J の実装が無いと Ktor もこちらのログも NOP になって何も出ない。
    // 起動時の DOMAIN と鍵の取得元は運用で必ず見たいので実装を入れる。
    // logback は native-image で設定ファイルの読み込みに追加対応が要るため、
    // 標準エラーに出すだけの slf4j-simple にする
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass.set("net.matsudamper.mastodon.rss.ApplicationKt")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    // GraalVM reachability metadata リポジトリは使わない。
    //
    // これは third-party ライブラリ向けの設定を配る仕組みで、収録範囲は各ライブラリ
    // 自身のパッケージに限られる（index.json の allowed-packages）。アプリ側の
    // @Serializable 型は構造上入らないので、こちらの都合は何も解決しない。
    //
    // このプロジェクトの依存について実際に収録されているものを見ると、
    // ktor-server-cio / ktor-server-content-negotiation / kotlinx-serialization-json は
    // 中身が {} （設定不要と検証済みの印）で、実データがあるのは ktor-server-core の
    // 一部だけ。sqlite-jdbc は jar が SqliteJdbcFeature を同梱していて素で動く。
    //
    // 当初は GraalVM for JDK 21 を使っていて、統合形式の reachability-metadata.json を
    // 読めずビルドが落ちるという理由もあった（provides a reachability-metadata schema,
    // but your GraalVM installation does not）。JDK 25 に上げてこの制約は無くなったが、
    // 上に書いた「収録範囲がアプリ側に届かない」という理由はそのままなので切ったままにする
    metadataRepository {
        enabled.set(false)
    }

    binaries {
        named("main") {
            imageName.set("mastodon-rss")
            mainClass.set("net.matsudamper.mastodon.rss.ApplicationKt")
            buildArgs.add("--no-fallback")

            buildArgs.add("--features=net.matsudamper.mastodon.rss.graalvm.GraphQlReflectionFeature")

            // native-image は解析中に自分で isAnnotationPresent を呼ぶ（PodFeature.isPodClass）。
            // そこで Kotlin の @Deprecated のデフォルト値が読まれ、level の型である
            // DeprecationLevel enum がビルド時に初期化される。native-image の既定は
            // 実行時初期化なので衝突してビルドが落ちる。
            //
            //   Error: Classes that should be initialized at run time got initialized during image building:
            //   kotlin.DeprecationLevel was unintentionally initialized at build time
            //
            // 当初は reflect-config.json への登録が原因だと考えていたが、登録を全て消しても
            // 再現した。Kotlin のクラスが解析対象にあれば起きるので、このまま許可する。
            // 値を持たない enum なのでビルド時初期化にして問題ない。
            // 原因の特定には --trace-class-initialization=kotlin.DeprecationLevel を使った
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")

            // jOOQ は組み込みの型を静的初期化子で登録し、その中で配列型を
            // `Class.arrayType()` から作る（BuiltInDataType → ArrayDataType）。
            // native-image の `Class.arrayType()` は、その配列型がイメージに
            // 入っていなければ null を返す。結果、型の登録先に null が渡って
            // 起動時に落ちる。
            //
            //   Caused by: java.lang.NullPointerException
            //     at java.util.concurrent.ConcurrentHashMap.putVal
            //     at org.jooq.impl.DefaultDataType.<init>
            //     at org.jooq.impl.SQLDataType.<clinit>
            //
            // ビルド時に初期化すると、静的初期化子は普通の JVM 上で走るので
            // `Class.arrayType()` が本物を返し、出来上がった型と配列クラスが
            // そのままイメージヒープに載る。実行時には初期化し直さない。
            //
            // JVM のテストでは再現しない。ここを外すと native バイナリだけが
            // 起動時に落ちるので、CI の native-image ジョブが唯一の検出手段になる
            buildArgs.add("--initialize-at-build-time=org.jooq")

            // jOOQ の型登録の入口 (DefaultDataType) は static な JooqLogger を持つので、
            // 上の指定は SLF4J のロガー実体をイメージヒープに載せる。SLF4J 側が
            // 実行時初期化のままだと、その組み合わせがビルドを止める。
            //
            //   An object of type 'org.slf4j.simple.SimpleLogger' was found in the image heap.
            //   This type, however, is marked for initialization at image run time
            //
            // slf4j-simple の既定の出力先は SYS_ERR で、これは書き込みのたびに
            // System.err を引き直す（ストリームを掴んだまま焼き込まれることはない）ので
            // ビルド時初期化にしてよい。ログの水準をシステムプロパティで変える場合は、
            // 読まれるのがビルド時になる点に注意する
            buildArgs.add("--initialize-at-build-time=org.slf4j")

            // 上の org.jooq から 1 クラスだけ外す。kickstart が連れてくる Jackson を
            // jOOQ が拾い、Convert$_JSON が持つ ObjectMapper の実体が
            // イメージヒープに載ってビルドが止まる。JSON 型は使っていない。
            //
            //   An object of type 'com.fasterxml.jackson.databind.json.JsonMapper'
            //   was found in the image heap
            buildArgs.add("--initialize-at-run-time=org.jooq.impl.Convert${'$'}_JSON")

            // 上の初期化に伴って、jOOQ のロゴと「豆知識」を出す静的初期化子も
            // ビルド時に走る。実行時にシステムプロパティを立てても間に合わないので、
            // イメージを作る JVM に渡す
            buildArgs.add("-Dorg.jooq.no-logo=true")
            buildArgs.add("-Dorg.jooq.no-tips=true")
        }
    }
}
