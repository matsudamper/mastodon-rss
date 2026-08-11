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
 * スキーマのファイル名を並べた一覧をリソースとして作る。
 *
 * native バイナリではディレクトリの列挙が効かない。クラスパス上のリソースは
 * 明示したものだけがイメージに入り、`graphql/` の下に何があるかを実行時に
 * 数え上げる手段が無くなる。
 *
 * 一覧をビルド時に作れば、実行時は 1 ファイル読んでから中身のファイルを引くだけで済む。
 * 手で並べていたときはスキーマを分けるたびに更新が要り、忘れるとその型だけ
 * 「スキーマに無い」ことになって起動後に初めて分かる形だった。
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
        /** スキーマと一覧を置くディレクトリ。リソースとして読むときのパスの先頭でもある */
        const val SCHEMA_DIRECTORY = "graphql"

        /** 読む側は `graphql/schema-list.txt` を引く */
        const val SCHEMA_LIST_NAME = "schema-list.txt"

        private const val SCHEMA_EXTENSION = "graphqls"
    }
}
