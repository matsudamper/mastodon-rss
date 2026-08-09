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

graalvmNative {
    binaries {
        // テストを native バイナリにして実行する。
        // StAX (javax.xml) のファクトリは ServiceLoader で実装を探すため、
        // native-image で解決に失敗するとパーサの生成時点で落ちる。
        // これは JVM のテストでは分からないので :backend:crypto と同じやり方で確かめる
        named("test") {
            buildArgs.add("--no-fallback")

            // native バイナリには既定で一部の文字コードしか入らない。
            // Shift_JIS や EUC-JP の配信元はまだあるので、入れておかないと
            // XML 宣言のとおりに読もうとした時点で UnsupportedCharsetException になる。
            // JVM のテストでは分からず、実際にこのテストが native でだけ落ちて見つかった。
            // :backend の native-image にも同じ指定が要る（Phase 5 で繋ぐときに足す）
            buildArgs.add("-H:+AddAllCharsets")
        }
    }
}
