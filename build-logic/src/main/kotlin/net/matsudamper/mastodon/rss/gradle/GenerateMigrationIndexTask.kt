package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * マイグレーション SQL のファイル名一覧を書いた `db/migration/index` を作る。
 *
 * jar 内のディレクトリ走査は native-image では動かないことがあるため、
 * 実行時はこの一覧を読んでファイルを開く。手で管理すると SQL を足したときに
 * 更新を忘れて「JVM では動くが native では動かない」状態になるので生成する。
 */
@CacheableTask
abstract class GenerateMigrationIndexTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationSql: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val fileNames = migrationSql.files.migrationsInOrder().map { it.name }

        val indexFile = outputDirectory.get().asFile.resolve("db/migration/index")
        indexFile.parentFile.mkdirs()
        indexFile.writeText(fileNames.joinToString(separator = "\n", postfix = "\n"))
    }
}
