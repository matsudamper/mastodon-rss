import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
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

tasks.register("compileAll") {
    group = "build"
    description = "frontend・backendを含む全モジュールのプロダクションコードおよびテストコードをコンパイルする"
    dependsOn(
        allprojects.map { project ->
            project.tasks.matching { task ->
                task.name.startsWith("compileKotlin") ||
                    task.name.startsWith("compileTestKotlin") ||
                    task.name == "compileAndroidMain" ||
                    task.name in listOf("compileJava", "compileTestJava")
            }
        },
    )
}
