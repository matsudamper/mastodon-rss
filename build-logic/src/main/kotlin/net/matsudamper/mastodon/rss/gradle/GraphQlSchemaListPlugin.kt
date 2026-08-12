package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * [GenerateSchemaListTask] の出力をリソースに足す。置き場所は読む側と揃えるので固定
 */
class GraphQlSchemaListPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("java") {
            val generateSchemaList =
                target.tasks.register<GenerateSchemaListTask>("generateSchemaList") {
                    description = "スキーマのファイル名を並べた ${GenerateSchemaListTask.SCHEMA_LIST_NAME} を作る"
                    schemaDirectory.set(
                        target.layout.projectDirectory
                            .dir("src/main/resources/${GenerateSchemaListTask.SCHEMA_DIRECTORY}"),
                    )
                    outputDirectory.set(target.layout.buildDirectory.dir("generated/schemaList"))
                }

            target.extensions.getByType<SourceSetContainer>().named("main") {
                resources.srcDir(generateSchemaList.flatMap { it.outputDirectory })
            }
        }
    }
}
