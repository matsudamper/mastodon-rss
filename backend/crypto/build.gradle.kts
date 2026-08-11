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

//   ./gradlew --quiet :backend:crypto:passwordHash
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
