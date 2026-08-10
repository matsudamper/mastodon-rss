package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * コミットされた `schema.sql` を入力に、jOOQ の生成コードとリフレクション設定を作る。
 * `schema.sql` 自体は `dumpSchema` が開発用 DB から書き出す。
 *
 * codegen 用の依存は使う側が `jooqCodegen` に入れる。バージョンをこのプラグインに
 * 持たせないのは、version catalog の外にバージョンが散ると Renovate の追従から
 * 外れるため。
 */
class DatabaseCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create<DatabaseCodegenExtension>(EXTENSION_NAME)
        extension.schemaFile.convention(
            target.layout.projectDirectory.file("src/main/resources/db/schema.sql"),
        )
        extension.excludes.convention("schema_version|sqlite_.*")
        extension.nativeImageMetadataPath.convention(
            target.provider { "${target.group}/${target.name}-jooq" },
        )

        val codegenConfiguration =
            target.configurations.create(CODEGEN_CONFIGURATION) {
                isCanBeConsumed = false
                isCanBeResolved = true
                description = "jOOQ の codegen を回すためだけの classpath"
            }

        target.pluginManager.withPlugin("java") {
            target.tasks.register<DumpSchemaTask>("dumpSchema") {
                description = "開発用 DB (-PdevDb=絶対パス) から schema.sql を書き出す"
                devDatabasePath.set(target.providers.gradleProperty("devDb"))
                driverClasspath.from(codegenConfiguration)
                excludes.set(extension.excludes)
                schemaFile.set(extension.schemaFile)
                workDatabase.set(target.layout.buildDirectory.file("dump/normalize.db"))
            }

            val buildJooqSchema =
                target.tasks.register<BuildSchemaDatabaseTask>("buildJooqSchema") {
                    description = "codegen の入力にする一時 SQLite を作る"
                    schemaSql.set(extension.schemaFile)
                    driverClasspath.from(codegenConfiguration)
                    databaseFile.set(target.layout.buildDirectory.file("jooq/schema.db"))
                }

            val javaPlugin = target.extensions.getByType<JavaPluginExtension>()
            val javaToolchains = target.extensions.getByType<JavaToolchainService>()

            val generateJooq =
                target.tasks.register<GenerateJooqSourcesTask>("generateJooq") {
                    description = "jOOQ の生成コードを作る"
                    schemaDatabase.set(buildJooqSchema.flatMap { it.databaseFile })
                    codegenClasspath.from(codegenConfiguration)
                    packageName.set(extension.packageName)
                    excludes.set(extension.excludes)
                    outputDirectory.set(target.layout.buildDirectory.dir("generated/jooq"))
                    configFile.set(target.layout.buildDirectory.file("jooq/codegen.xml"))
                    launcher.set(javaToolchains.launcherFor(javaPlugin.toolchain))
                }

            val generateJooqReflectConfig =
                target.tasks.register<GenerateJooqReflectConfigTask>("generateJooqReflectConfig") {
                    description = "生成コードの native-image 向けリフレクション設定を作る"
                    generatedSources.set(generateJooq.flatMap { it.outputDirectory })
                    metadataPath.set(extension.nativeImageMetadataPath)
                    outputDirectory.set(target.layout.buildDirectory.dir("generated/jooqNativeImage"))
                }

            target.extensions.getByType<SourceSetContainer>().named("main") {
                resources.srcDir(generateJooqReflectConfig.flatMap { it.outputDirectory })
                java.srcDir(generateJooq.flatMap { it.outputDirectory })
            }
        }
    }

    private companion object {
        const val EXTENSION_NAME = "databaseCodegen"
        const val CODEGEN_CONFIGURATION = "jooqCodegen"
    }
}
