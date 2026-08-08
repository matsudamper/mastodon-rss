import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    // Compose Multiplatform for Web。canvas 上に描画するため DOM 操作は最小限で済む
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "frontend.js"
                // 既定は 8080 で backend と衝突するのでずらす
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).copy(
                        port = 8081,
                        // 開発サーバーには管理 API が無いので backend に転送する。
                        // 転送しないと同一オリジンでなくなり、セッションの Cookie も送られない
                        proxy =
                            mutableListOf(
                                KotlinWebpackConfig.DevServer.Proxy(
                                    context = mutableListOf("/admin/api"),
                                    target = "http://localhost:8080",
                                ),
                            ),
                    )
            }
        }
        // ブラウザで動かすだけなので実行可能バイナリを生成する
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                // compose.* は deprecated 警告が出るが、1.11.1 では
                // org.jetbrains.compose.* の直接座標がまだ公開されていない
                // （material3 は alpha 止まり）ため、こちらを使う。
                // 1.12 系が安定したら直接座標へ移行する。
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // document 等のブラウザ API。Kotlin/Wasm では stdlib から分離されている
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
                // 管理 API の呼び出し。ブラウザの fetch を使う実装
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                // 管理 API の DTO。backend と同じ型を使う
                implementation(project(":shared"))
            }
        }
    }
}
