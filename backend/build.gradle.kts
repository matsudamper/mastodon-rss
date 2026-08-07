plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.graalvm.native)
}

dependencies {
    implementation(project(":repository"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

application {
    mainClass.set("dev.matsudamper.mastodonrss.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    // GraalVM reachability metadata リポジトリは使わない。
    //
    // これは third-party ライブラリ向けの設定を配る仕組みだが、このプロジェクトが
    // 必要とする設定は自分で持っている（reflect-config.json / resource-config.json、
    // sqlite-jdbc は jar に Feature を同梱している）。
    //
    // 一方でリポジトリのスキーマは GraalVM の版に追従しており、少し古い GraalVM だと
    //   provides a reachability-metadata schema, but your GraalVM installation does not
    // でビルドが落ちる。実際 Docker のビルドステージがこれで止まった。
    // 使っていない仕組みのために GraalVM のパッチ版に縛られる理由が無いので切る
    metadataRepository {
        enabled.set(false)
    }

    binaries {
        named("main") {
            imageName.set("mastodon-rss")
            mainClass.set("dev.matsudamper.mastodonrss.ApplicationKt")
            buildArgs.add("--no-fallback")

            // reflect-config.json に登録したクラスは、native-image がアノテーションを
            // 解析する。その際に Kotlin の @Deprecated のデフォルト値経由で
            // DeprecationLevel enum がビルド時に初期化され、既定の実行時初期化と
            // 衝突してビルドが落ちる。値を持たない enum なのでビルド時初期化を許可する
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")
        }
    }
}
