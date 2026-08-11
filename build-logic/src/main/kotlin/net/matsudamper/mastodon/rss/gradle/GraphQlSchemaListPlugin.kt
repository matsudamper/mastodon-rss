package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * `src/main/resources/graphql/` に置いたスキーマの一覧を、同じ `graphql/` の下の
 * `schema-list.txt` としてリソースに足す。何のための一覧かは [GenerateSchemaListTask] を参照。
 *
 * 設定は持たない。置き場所を変えられるようにすると、読む側のパスと 2 か所で
 * 合わせることになる。
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
