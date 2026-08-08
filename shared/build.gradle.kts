import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// 管理 API の DTO を :backend と :frontend の両方から使う。
// 片方だけ直してもう片方を直し忘れる、という形の不具合を型で防ぐのが目的なので、
// ここに置くのは通信する形だけにして、サーバー側のロジックは持ち込まない。
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    jvmToolchain(25)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // DTO の @Serializable を使う側でそのまま encode / decode できるよう api にする
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}
