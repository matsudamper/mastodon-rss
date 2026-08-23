import net.matsudamper.mastodon.rss.gradle.WebpackBundleHashPlugin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.apollo)
    id("mastodon-rss.webpack-bundle-hash")
}

kotlin {
    // Compose Multiplatform for Web。canvas 上に描画するため DOM 操作は最小限で済む
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = WebpackBundleHashPlugin.BUNDLE_FILE_NAME
                // 既定は 8080 で backend と衝突するのでずらす
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 8081)
            }
        }
        // ブラウザで動かすだけなので実行可能バイナリを生成する
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.apollo.runtime)
                implementation(libs.apollo.normalized.cache)
                implementation(compose.runtime)
            }
        }

        wasmJsMain {
            dependencies {
                // compose.* は deprecated 警告が出るが、1.11.1 では
                // org.jetbrains.compose.* の直接座標がまだ公開されていない
                // （material3 は alpha 止まり）ため、こちらを使う。
                // 1.12 系が安定したら直接座標へ移行する。
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
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

// 問い合わせ（src/commonMain/graphql/*.graphql）はこのモジュールが持つ。
// スキーマは :backend:graphql のものをファイルとして読むだけで、依存はしない。
// 写しを持たないので、片方にだけフィールドがある状態にはならない
apollo {
    service("app") {
        packageName.set("net.matsudamper.mastodon.rss.frontend.graphql")
        schemaFiles.from(
            rootProject.fileTree("backend/graphql/src/main/resources/graphql") { include("*.graphqls") },
        )

        mapScalarToKotlinLong("UnixTime")

        plugin("com.apollographql.cache:normalized-cache-apollo-compiler-plugin:${libs.versions.apollo.cache.get()}")
        pluginArgument("com.apollographql.cache.packageName", packageName.get())
    }
}
