plugins {
    alias(libs.plugins.kotlin.jvm)
    // nativeTest のためだけに入れている。このモジュールは実行可能バイナリを作らない
    id("mastodon-rss.native-image")
}

dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        // テストを native バイナリにして実行する。
        // JCA（RSA 鍵生成・SHA256withRSA）が native-image 上で動くことは、
        // JVM のテストでは分からないので実バイナリで確かめる
        named("test") {
            buildArgs.add("--no-fallback")
        }
    }
}
