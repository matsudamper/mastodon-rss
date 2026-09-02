import javax.xml.parsers.DocumentBuilderFactory
import dev.detekt.gradle.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.graalvm.native) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
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

val detektTargetProjects =
    setOf(
        ":frontend",
        ":frontend:common-component",
    )

configurations {
    create("composeRulesDetekt")
}

dependencies {
    add("composeRulesDetekt", libs.compose.rules.detekt)
}

fun composeRulesDetektConfigFile(): java.io.File {
    val configFile = layout.buildDirectory.file("compose-rules-detekt.yml").get().asFile
    val versionMarker = layout.buildDirectory.file("compose-rules-detekt.version").get().asFile
    val composeRulesVersion = libs.versions.composeRules.get()
    if (!configFile.exists() || !versionMarker.exists() || versionMarker.readText() != composeRulesVersion) {
        configFile.parentFile.mkdirs()
        val composeRulesJar =
            configurations
                .named("composeRulesDetekt")
                .get()
                .files
                .single { it.name == "detekt-$composeRulesVersion.jar" }
        zipTree(composeRulesJar)
            .matching { include("config/config.yml") }
            .singleFile
            .copyTo(configFile, overwrite = true)
        versionMarker.parentFile.mkdirs()
        versionMarker.writeText(composeRulesVersion)
    }
    return configFile
}

fun parseDetektViolations(xml: java.io.File): List<String> {
    if (!xml.exists()) {
        return emptyList()
    }

    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
    val violations = mutableListOf<String>()
    val files = document.getElementsByTagName("file")

    for (fileIndex in 0 until files.length) {
        val fileElement = files.item(fileIndex) as Element
        val path = fileElement.getAttribute("name")
        val errors = fileElement.getElementsByTagName("error")

        for (errorIndex in 0 until errors.length) {
            val error = errors.item(errorIndex) as Element
            val line = error.getAttribute("line")
            val column = error.getAttribute("column")
            val rule = error.getAttribute("source").removePrefix("detekt.")
            val message = error.getAttribute("message").lineSequence().first().trim()
            violations.add("$path:$line:$column [$rule] $message")
        }
    }

    return violations
}

configure(subprojects.filter { it.path in detektTargetProjects }) {
    apply(plugin = "dev.detekt")

    dependencies {
        add("detektPlugins", rootProject.libs.compose.rules.detekt)
    }

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        toolVersion = rootProject.libs.versions.detekt.get()
        config.setFrom(composeRulesDetektConfigFile(), rootProject.files("detekt.yml"))
        buildUponDefaultConfig = false
        disableDefaultRuleSets = true
        parallel = true
        basePath = rootDir
    }

    tasks.withType<Detekt>().configureEach {
        ignoreFailures.set(true)
        baseline.set(null as java.io.File?)
        exclude { fileTreeElement ->
            fileTreeElement.file.invariantSeparatorsPath.contains("/build/generated/")
        }
    }

    tasks.matching { task -> task.name.startsWith("detektBaseline") }.configureEach {
        enabled = false
    }
}

tasks.register("detektCheck") {
    group = "verification"
    description = "frontend の Compose ルールを detekt で検査する"

    val detektTasks =
        detektTargetProjects.flatMap { projectPath ->
            project(projectPath).tasks.matching { task ->
                task.name.startsWith("detekt") && task.name.endsWith("MainSourceSet")
            }
        }

    dependsOn(detektTasks)

    doLast {
        val violations =
            detektTargetProjects.flatMap { projectPath ->
                val reportsDir =
                    project(projectPath)
                        .layout
                        .buildDirectory
                        .dir("reports/detekt")
                        .get()
                        .asFile

                reportsDir
                    .listFiles()
                    .orEmpty()
                    .filter { file -> file.extension == "xml" && file.name.endsWith("MainSourceSet.xml") }
                    .flatMap(::parseDetektViolations)
            }.sorted()

        if (violations.isEmpty()) {
            return@doLast
        }

        logger.lifecycle("")
        logger.lifecycle("detekt violations (${violations.size}):")
        violations.forEach { violation ->
            logger.lifecycle(violation)
        }
        logger.lifecycle("")

        throw GradleException("detekt で ${violations.size} 件の違反を検出しました")
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
    dependsOn(compileTasks(includeAndroid = true))
}

tasks.register("compileCloudAgent") {
    group = "build"
    description = "Cloud Agent 向け。Android SDK を要さず backend と Wasm frontend をコンパイルする"
    dependsOn(compileTasks(includeAndroid = false))
}

fun compileTasks(includeAndroid: Boolean) =
    allprojects.map { project ->
        project.tasks.matching { task ->
            (task.name.startsWith("compileKotlin") ||
                task.name.startsWith("compileTestKotlin") ||
                (includeAndroid && task.name == "compileAndroidMain") ||
                task.name in listOf("compileJava", "compileTestJava"))
        }
    }
