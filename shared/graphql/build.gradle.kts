import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// スキーマに書けないものを置く。スキーマそのものは :shared:graphql:schema にある。
// いまあるのは口のパスだけで、増えるとしてもスキーマに表せない定数に限る。
// ロジックは入れない。入れると JVM と Kotlin/Wasm の両方で動くことを常に気にすることになる。
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // :backend が JVM 25 なので合わせる。低いと backend から読めないクラスファイルになる
    jvmToolchain(25)
}
