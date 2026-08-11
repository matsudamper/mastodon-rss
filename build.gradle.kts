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

        // 生成されたコードは対象にしない。Apollo の出力は ktlint の規則どおりにならない。
        // 効くのは ktlintCheck まで。ktlintFormat はソースのルートごと走査していて
        // これを見ないので、手元で流すと生成コードの違反（auto-correct 不可）が出る
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
