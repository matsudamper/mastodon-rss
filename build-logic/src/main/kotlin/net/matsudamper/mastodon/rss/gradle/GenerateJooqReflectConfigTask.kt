package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成コードの native-image 向けリフレクション設定を作る。
 *
 * jOOQ はクエリを組み立てるときに、テーブルに対応する `Record` を
 * `getDeclaredConstructor().newInstance()` で作る。native-image は到達可能性を
 * 静的に解析するので、この経路は登録しないと実行時に落ちる。JVM では動いて
 * native バイナリでだけ失敗する形になり、テストでは掴めない。
 *
 * 手で書かないのは、テーブルを足したときに更新を忘れるため。
 * 生成物から作れば忘れようがない。
 */
@CacheableTask
abstract class GenerateJooqReflectConfigTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSources: DirectoryProperty

    /**
     * 設定ファイルの置き場所。`META-INF/native-image/<group>/<artifact>/` の
     * `<group>/<artifact>` にあたる部分。手で書いた方の設定と混ざらないよう分ける
     */
    @get:Input
    abstract val metadataPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceRoot = generatedSources.get().asFile

        val recordClasses =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith("Record.java") }
                .map { file ->
                    file
                        .relativeTo(sourceRoot)
                        .path
                        .removeSuffix(".java")
                        .replace(File.separatorChar, '.')
                }.sorted()
                .toList()

        check(recordClasses.isNotEmpty()) {
            "jOOQ の生成物に Record が 1 つも無い。codegen が空振りしている可能性がある"
        }

        val entries =
            recordClasses.joinToString(separator = ",\n") { className ->
                """  { "name": "$className", "allDeclaredConstructors": true }"""
            }

        val configFile =
            outputDirectory
                .get()
                .asFile
                .resolve("META-INF/native-image/${metadataPath.get()}/reflect-config.json")
        configFile.parentFile.mkdirs()
        configFile.writeText("[\n$entries\n]\n")
    }
}
