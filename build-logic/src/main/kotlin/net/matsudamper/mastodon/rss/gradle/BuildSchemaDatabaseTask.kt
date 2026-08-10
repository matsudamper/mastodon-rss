package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.net.URLClassLoader
import java.sql.Driver
import java.util.Properties

/**
 * codegen の入力になる SQLite ファイルを、マイグレーション SQL を順に適用して作る。
 *
 * jOOQ には SQL スクリプトを直接読む `DDLDatabase` もあるが、そちらは jOOQ 自身の
 * パーサで DDL を解釈する。SQLite の型の扱い（型親和性）まで同じになる保証が無いので、
 * 実物の SQLite に流し込んで、そこから読ませる。生成される型が実行時の DB と
 * ずれないことの方が、パイプラインが 1 段減ることより大事。
 */
abstract class BuildSchemaDatabaseTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationSql: ConfigurableFileCollection

    /** JDBC ドライバを含む classpath。このビルドの classpath には載せない */
    @get:Classpath
    abstract val driverClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val databaseFile: RegularFileProperty

    @TaskAction
    fun build() {
        val target = databaseFile.get().asFile
        target.parentFile.mkdirs()
        // 作り直さないと、消したはずのテーブルが前回の DB に残って生成物に出続ける
        target.delete()

        val loader =
            URLClassLoader(
                driverClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
                // java.sql.* は JDK 側が持つので、ここで読んだドライバを Driver として扱える
                ClassLoader.getPlatformClassLoader(),
            )

        loader.use {
            val driver =
                loader
                    .loadClass("org.sqlite.JDBC")
                    .getDeclaredConstructor()
                    .newInstance() as Driver

            driver.connect("jdbc:sqlite:$target", Properties()).use { connection ->
                connection.createStatement().use { statement ->
                    migrationSql.files.migrationsInOrder().forEach { file ->
                        // sqlite-jdbc は 1 回の executeUpdate で複数の文を実行する。
                        // 実行時の MigrationRunner は自前で文に切ってから流すので経路が違うが、
                        // ここで欲しいのは codegen に読ませるスキーマだけなので合わせなくてよい
                        statement.executeUpdate(file.readText())
                    }
                }
            }
        }
    }
}
