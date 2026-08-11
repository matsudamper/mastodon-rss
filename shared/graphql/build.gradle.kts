plugins {
    alias(libs.plugins.kotlin.jvm)
}

// GraphQL のスキーマだけを持つ。:backend と :frontend が見る唯一の定義で、
// どちらかの下に置くと相手のビルドがそのディレクトリを見ることになるので root に置く。
// 成果物を使うのは :backend だけなので JVM のモジュールにしてある。
kotlin {
    jvmToolchain(25)
}
