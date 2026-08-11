import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// GraphQL のスキーマだけを持つ。Kotlin のコードは 1 行も置かない。
//
// スキーマは :backend（実行時にリソースとして読む）と :frontend（Apollo の
// コード生成の入力）の両方が見る唯一の定義で、これが分かれると片方にだけ
// フィールドがある状態を作れてしまう。他のものと同居させないのは、
// 「ここを直せばスキーマが変わる」を 1 ディレクトリに閉じておくため。
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    jvmToolchain(25)
}
