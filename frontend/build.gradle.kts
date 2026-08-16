import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.apollo)
}

/** webpack が出す JS の名前。dev server はこの名前のまま返す */
val bundleFileName = "frontend.js"

/** 配布物の入口。中の JS の名前をここから差し替える */
val indexFileName = "index.html"

kotlin {
    // Compose Multiplatform for Web。canvas 上に描画するため DOM 操作は最小限で済む
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = bundleFileName
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

// 配布物では JS の名前に中身のハッシュを入れ、index.html の参照もそれに合わせる。
// webpack が出す .wasm の名前にはハッシュが入るため、JS だけが古いまま使われると、
// 既に無い .wasm を取りに行って画面が出ない。名前が中身で決まれば、
// index.html を取り直した時点で JS と .wasm の組み合わせが揃う。
//
// dev server は webpack の出力をそのまま返すのでここは通らない。
// resources の index.html がハッシュの無い名前を指しているのはそのため
listOf(
    "wasmJsBrowserDistribution",
    "wasmJsBrowserDevelopmentExecutableDistribution",
).forEach { taskName ->
    tasks.named<Sync>(taskName) {
        doLast {
            renameBundleWithContentHash(destinationDir)
        }
    }
}

/**
 * [distDir] の JS を中身のハッシュ入りの名前に変え、index.html の参照も差し替える。
 */
fun renameBundleWithContentHash(distDir: File) {
    val bundle = distDir.resolve(bundleFileName)
    check(bundle.isFile) { "$bundle が無い" }

    val hash =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bundle.readBytes())
            .joinToString(separator = "") { "%02x".format(it) }
            .take(16)
    val hashedName = "${bundle.nameWithoutExtension}.$hash.${bundle.extension}"
    check(bundle.renameTo(distDir.resolve(hashedName))) { "$bundle の名前を $hashedName に変えられない" }

    // 読み込みは root 絶対。index.html はどのパスでも同じものが返るため
    val reference = "/$bundleFileName"
    val index = distDir.resolve(indexFileName)
    val html = index.readText()
    check(html.contains(reference)) { "$index が $reference を読み込んでいない" }
    index.writeText(html.replace(reference, "/$hashedName"))
}

// 問い合わせ（src/commonMain/graphql/*.graphql）はこのモジュールが持つ。
// スキーマは :backend:graphql のものをファイルとして読むだけで、依存はしない。
// 写しを持たないので、片方にだけフィールドがある状態にはならない
apollo {
    service("admin") {
        packageName.set("net.matsudamper.mastodon.rss.frontend.graphql")
        schemaFiles.from(
            rootProject.fileTree("backend/graphql/src/main/resources/graphql") { include("*.graphqls") },
        )

        mapScalarToKotlinLong("UnixTime")
    }
}
