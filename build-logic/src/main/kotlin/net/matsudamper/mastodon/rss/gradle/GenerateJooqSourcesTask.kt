package net.matsudamper.mastodon.rss.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations

/**
 * jOOQ の生成コードを作る。
 *
 * 生成するのは Kotlin ではなく Java。ktlint を全モジュールに掛けているので、
 * Kotlin で生成すると `build/` の中の生成物まで整形の対象になる。
 *
 * codegen は別プロセスで動かす。jOOQ の `GenerationTool` を Gradle のデーモンの中で
 * 呼ぶと、その classpath に jOOQ が居座ることになる。
 */
abstract class GenerateJooqSourcesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaDatabase: RegularFileProperty

    @get:Classpath
    abstract val codegenClasspath: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val excludes: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** codegen に渡す設定ファイル。中身は入力から決まるので出力として追跡しない */
    @get:Internal
    abstract val configFile: RegularFileProperty

    /** モジュールのツールチェーンに合わせる。デーモンの JVM とは限らない */
    @get:Nested
    abstract val launcher: Property<JavaLauncher>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        // テーブルや列を消したときに、古い型がそのまま残らないように作り直す
        output.deleteRecursively()

        val config = configFile.get().asFile
        config.parentFile.mkdirs()
        config.writeText(
            configXml(
                schemaDatabase = schemaDatabase.get().asFile,
                outputDirectory = output,
            ),
        )

        val executablePath = launcher.get().executablePath
        val javaExecutable = executablePath.asFile.absolutePath

        execOperations.javaexec {
            executable = javaExecutable
            classpath = codegenClasspath
            mainClass.set("org.jooq.codegen.GenerationTool")
            args = listOf(config.absolutePath)

            // 何もしないと実行のたびにロゴと「豆知識」が標準出力に出る
            systemProperty("org.jooq.no-logo", "true")
            systemProperty("org.jooq.no-tips", "true")
        }
    }

    private fun configXml(
        schemaDatabase: File,
        outputDirectory: File,
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <configuration>
          <jdbc>
            <driver>org.sqlite.JDBC</driver>
            <url>jdbc:sqlite:${schemaDatabase.absolutePath}</url>
          </jdbc>
          <generator>
            <database>
              <name>org.jooq.meta.sqlite.SQLiteDatabase</name>
              <includes>.*</includes>
              <excludes>${excludes.get()}</excludes>
              <forcedTypes>
                <!--
                  SQLite の INTEGER は最大 8 バイトで、rowid も int64 まで入る。
                  jOOQ は宣言名を SQL 標準の INTEGER（4 バイト）として読むので、
                  そのままだと 2^31 を超えた値が黙って別の値になる
                -->
                <forcedType>
                  <name>BIGINT</name>
                  <includeTypes>INTEGER</includeTypes>
                </forcedType>
              </forcedTypes>
            </database>
            <generate>
              <!-- TEXT に入れた ISO 8601 を java.time で受けたい -->
              <javaTimeTypes>true</javaTimeTypes>
              <!-- POJO と DAO はリフレクション経由になるので作らない。native-image で効いてくる -->
              <pojos>false</pojos>
              <daos>false</daos>
              <deprecated>false</deprecated>
            </generate>
            <target>
              <packageName>${packageName.get()}</packageName>
              <directory>${outputDirectory.absolutePath}</directory>
            </target>
          </generator>
        </configuration>
        """.trimIndent()
}
