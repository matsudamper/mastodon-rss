package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * スキーマのファイル名を並べた一覧をリソースとして作る。native バイナリでは
 * ディレクトリの列挙が効かないので、実行時に `graphql/` の中身を数え上げられない。
 */
@CacheableTask
abstract class GenerateSchemaListTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDirectory: DirectoryProperty

    /** リソースの root。この下の [SCHEMA_DIRECTORY] に一覧を置く */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val fileNames =
            schemaDirectory
                .get()
                .asFile
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension == SCHEMA_EXTENSION }
                .map { it.name }
                .sorted()

        check(fileNames.isNotEmpty()) {
            "${schemaDirectory.get().asFile} に .$SCHEMA_EXTENSION が 1 つも無い"
        }

        val listFile =
            outputDirectory
                .get()
                .asFile
                .resolve("$SCHEMA_DIRECTORY/$SCHEMA_LIST_NAME")
        listFile.parentFile.mkdirs()
        listFile.writeText(fileNames.joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        const val SCHEMA_DIRECTORY = "graphql"
        const val SCHEMA_LIST_NAME = "schema-list.txt"

        private const val SCHEMA_EXTENSION = "graphqls"
    }
}
