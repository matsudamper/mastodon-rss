package net.matsudamper.mastodon.rss.repository

import java.nio.file.Files
import java.nio.file.Path
import org.sqlite.SQLiteDataSource

/**
 * テスト用に `schema.sql` を適用する。
 *
 * 実運用では sqlite3def で手適用する（`db/schema.sql` と同じ場所の README を参照）ので、
 * 起動時の自動適用は無い。テストも同じ前提で、DB を作るときにこれを呼ぶ。
 */
internal object TestSchema {
    fun applyTo(dbPath: Path) {
        val schema =
            checkNotNull(TestSchema::class.java.classLoader.getResourceAsStream("db/schema.sql")) {
                "schema.sql がクラスパスに無い"
            }.use { it.readBytes().toString(Charsets.UTF_8) }

        Files.createDirectories(dbPath.toAbsolutePath().normalize().parent)

        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                // sqlite-jdbc は 1 回の executeUpdate で複数の文を実行する
                statement.executeUpdate(schema)
            }
        }
    }
}
