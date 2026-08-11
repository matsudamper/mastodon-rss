import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.apollo)
}

kotlin {
    // Compose Multiplatform for Web。canvas 上に描画するため DOM 操作は最小限で済む
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "frontend.js"
                // 既定は 8080 で backend と衝突するのでずらす
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 8081)
            }
        }
        // ブラウザで動かすだけなので実行可能バイナリを生成する
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // 管理 API のクライアント。Apollo が生成するコードは commonMain に入るので、
                // 依存も wasmJsMain ではなくここに置く
                implementation(libs.apollo.runtime)
                // GraphQL の口のパス。スキーマと一緒に :backend と共有している
                implementation(project(":shared:graphql"))
                // 画面の状態を持つところまでは commonMain に置ける。
                // 描画に使う compose.foundation などはブラウザ側の ui/ から参照するので wasmJsMain に置く
                implementation(compose.runtime)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // compose.* は deprecated 警告が出るが、1.11.1 では
                // org.jetbrains.compose.* の直接座標がまだ公開されていない
                // （material3 は alpha 止まり）ため、こちらを使う。
                // 1.12 系が安定したら直接座標へ移行する。
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // 画面遷移。JetBrains 版の Navigation 3（wasmJs 向けの成果物がある）。
                // runtime は推移的に androidx.navigation3 から入る
                implementation(libs.navigation3.ui)
                // 日本語フォントを配信元から取ってくるのに使う。詳細は ui/Font.kt を参照
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                // document 等のブラウザ API。Kotlin/Wasm では stdlib から分離されている
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

// 管理 API のクライアントをスキーマから生成する。
// スキーマは :shared:graphql:schema のものをそのまま読む。写しを置くと、増やしたフィールドが
// 片方にだけ入っている状態を作れてしまう
apollo {
    service("admin") {
        packageName.set("net.matsudamper.mastodon.rss.frontend.graphql")
        schemaFiles.from(rootProject.file("shared/graphql/schema/src/commonMain/resources/graphql/schema.graphqls"))
    }
}
