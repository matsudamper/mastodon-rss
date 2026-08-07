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
