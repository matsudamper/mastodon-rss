plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.graalvm.native)
}

dependencies {
    implementation(project(":backend:repository"))
    implementation(project(":backend:crypto"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)

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
        }
    }
}
