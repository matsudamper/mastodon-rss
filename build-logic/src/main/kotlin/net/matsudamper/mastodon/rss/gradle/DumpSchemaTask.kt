package net.matsudamper.mastodon.rss.gradle

import java.io.File
import java.sql.Connection
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 開発用 DB から `schema.sql` を書き出す。スキーマを変えたときに手で叩く唯一のタスク。
 *
 *     ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
 *
 * 開発用 DB を直接いじって形を決め、このタスクで `schema.sql` に固定して commit する。
 * jOOQ の生成コードはコミットされた `schema.sql` から作られるので、
 * dump し忘れた変更がコードに紛れ込むことは無い。
 *
 * 書き出す前に、読んだ CREATE 文を使い捨ての DB に適用し直して、
 * `schema.sql` が単体で適用可能なことを確かめる。
 * 出力はオブジェクト名でソートするので、同じスキーマからは常に同じテキストが出る。
 *
 * 全プロパティを [Internal] にして up-to-date 判定に乗せない。
 * 出力先がソースツリーで、入力が Gradle の外にある開発用 DB なので、
 * `ktlintFormat` と同じ「叩いたら必ず実行される」タスクとして扱う。
 */
abstract class DumpSchemaTask : DefaultTask() {
    /** 開発用 DB の絶対パス。`-PdevDb=...` で渡す */
    @get:Internal
    abstract val devDatabasePath: Property<String>

    /** JDBC ドライバを含む classpath。このビルドの classpath には載せない */
    @get:Internal
    abstract val driverClasspath: ConfigurableFileCollection

    /** 書き出さないオブジェクトの正規表現。jOOQ codegen の excludes と同じものを渡す */
    @get:Internal
    abstract val excludes: Property<String>

    /** 書き出し先。コミット対象の `schema.sql` */
    @get:Internal
    abstract val schemaFile: RegularFileProperty

    /** 適用可能なことの確認に使う使い捨て DB。`build/` の下 */
    @get:Internal
    abstract val workDatabase: RegularFileProperty

    @TaskAction
    fun dump() {
        val devDb = resolveDevDatabase()
        val excludePattern = Regex(excludes.get())

        val statements =
            withSqliteConnection(driverClasspath.files, devDb) { connection ->
                readSchemaStatements(connection, excludePattern)
            }

        check(statements.isNotEmpty()) {
            "開発用 DB ($devDb) から書き出せるテーブルが 1 つも無い"
        }

        val verified = roundTrip(statements, excludePattern)

        val target = schemaFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(
            buildString {
                appendLine(HEADER)
                verified.forEach { statement ->
                    appendLine(statement.trimEnd().removeSuffix(";") + ";")
                    appendLine()
                }
            }.trimEnd() + "\n",
        )

        logger.lifecycle("schema.sql を書き出した: $target")
    }

    private fun resolveDevDatabase(): File {
        val path =
            devDatabasePath.orNull
                ?: error(
                    "開発用 DB のパスが指定されていない。" +
                        "./gradlew ${this.path} -PdevDb=/絶対パス/dev.db の形で渡すこと",
                )

        val file = File(path)
        // Gradle はカレントディレクトリの扱いが呼び出し場所で揺れるので、相対パスは受けない
        require(file.isAbsolute) { "devDb は絶対パスで渡すこと: $path" }
        require(file.isFile) { "開発用 DB が見つからない: $path" }
        return file
    }

    /**
     * `sqlite_master` から CREATE 文を読む。
     *
     * 保存されているのは実行された文のテキストそのままなので、そのまま書き出せば
     * 適用可能な SQL になる。自動生成のインデックス (`sqlite_autoindex_*`) は
     * `sql` が NULL なので、この条件で一緒に落ちる。
     */
    private fun readSchemaStatements(
        connection: Connection,
        excludePattern: Regex,
    ): List<String> =
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    // インデックスや trigger はテーブルより後に作る必要があるので type で並べる
                    """
                    SELECT type, name, tbl_name, sql FROM sqlite_master
                    WHERE sql IS NOT NULL
                    ORDER BY
                      CASE type
                        WHEN 'table' THEN 0
                        WHEN 'index' THEN 1
                        WHEN 'view' THEN 2
                        ELSE 3
                      END,
                      name
                    """.trimIndent(),
                ).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val name = resultSet.getString("name")
                            val tableName = resultSet.getString("tbl_name")
                            // 除外したテーブルに付いているインデックスや trigger も一緒に外す
                            if (excludePattern.matches(name) || excludePattern.matches(tableName)) continue
                            add(resultSet.getString("sql"))
                        }
                    }
                }
        }

    /**
     * 読んだ CREATE 文を使い捨ての DB に適用し直し、そこからもう一度読む。
     *
     * これで「`schema.sql` は単体で適用できる」ことが書き出す前に確かめられる。
     * テキストの整形が変わるわけではない。SQLite は実行された文をそのまま保存する
     */
    private fun roundTrip(
        statements: List<String>,
        excludePattern: Regex,
    ): List<String> {
        val work = workDatabase.get().asFile
        work.parentFile.mkdirs()
        work.delete()

        return withSqliteConnection(driverClasspath.files, work) { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql ->
                    try {
                        statement.executeUpdate(sql)
                    } catch (e: Exception) {
                        throw IllegalStateException("dump した CREATE 文が単体で適用できない:\n$sql", e)
                    }
                }
            }
            readSchemaStatements(connection, excludePattern)
        }
    }

    private companion object {
        val HEADER =
            """
            -- スキーマの唯一の定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
            --
            -- 手で編集しない。開発用 DB を直接いじって形を決めたら
            --   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
            -- で書き出して commit する。実 DB への適用は sqlite3def で手動。
            -- 詳細は同じディレクトリの README.md を参照。
            """.trimIndent()
    }
}
