import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.graalvm.native) apply false
    alias(libs.plugins.ktlint) apply false
}

// version catalog のアクセサは allprojects の中からは引けないので、ここで取り出しておく
val ktlintVersion = libs.versions.ktlint.get()

allprojects {
    group = "net.matsudamper"
    version = "0.1.0"

    // フォーマットはモジュールごとに設定せず、全体で 1 つに揃える。
    // ルートの build.gradle.kts / settings.gradle.kts も対象に含める
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        // プラグイン既定のバージョンに引きずられないよう version catalog で固定する。
        // Renovate に追従させるためでもある
        version.set(ktlintVersion)

        // 生成されたコードは対象にしない。Apollo が出す GraphQL のクライアントは
        // ktlint の規則どおりには出力されず、直す手段が無いのに落ちる。
        //
        // これが効くのは ktlintCheck（CI が叩くもの）まで。ktlintFormat は
        // ソースのルートごと走査していてファイル単位のフィルタを見ないため、
        // 手元で流すと生成コードの違反（どれも auto-correct 不可）が出る。
        // 実ソースの整形自体は済むので、そのまま ktlintCheck で確かめること
        filter {
            exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
        }
    }
}

// CI が叩くのは root の ktlintCheck だけなので、別ビルドの build-logic を繋いでおく
listOf("ktlintCheck", "ktlintFormat").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(gradle.includedBuild("build-logic").task(":$taskName"))
    }
}
