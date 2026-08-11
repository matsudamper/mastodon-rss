plugins {
    alias(libs.plugins.kotlin.jvm)
    // nativeTest のためだけに入れている。このモジュールは実行可能バイナリを作らない
    alias(libs.plugins.graalvm.native)
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

// 管理画面のパスワードハッシュを作る。ADMIN_PASSWORD_HASH に入れる値がこれ。
// パスワードは標準入力から渡す（引数にするとシェルの履歴と ps に平文で残る）。
//
//   ./gradlew --quiet :backend:crypto:passwordHash
//
// application プラグインは入れていない。このモジュールは配布物を作らず、
// 実行したいのはこの 1 つだけなので、タスクを 1 つ足すだけにする
tasks.register<JavaExec>("passwordHash") {
    group = "application"
    description = "標準入力のパスワードから ADMIN_PASSWORD_HASH に入れる値を作る"
    mainClass.set("net.matsudamper.mastodon.rss.crypto.PasswordHashCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
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
