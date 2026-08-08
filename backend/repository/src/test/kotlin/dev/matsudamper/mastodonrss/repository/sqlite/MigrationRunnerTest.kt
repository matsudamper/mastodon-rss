package dev.matsudamper.mastodonrss.repository.sqlite

import dev.matsudamper.mastodonrss.repository.DatabaseConfig
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// マイグレーションの適用を確認する。
// 空の DB から最新まで適用できること、二重に適用しても壊れないこと、
// 適用済みのファイルが書き換えられていたら検出できること、
// 途中で失敗したファイルが丸ごとロールバックされること。
class MigrationRunnerTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-migration-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withManager(block: (SqliteConnectionManager) -> T): T =
        SqliteConnectionManager(DatabaseConfig(path = dbPath)).use(block)

    private fun SqliteConnectionManager.appliedVersions(): List<Int> =
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM schema_version ORDER BY version").use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.getInt(1))
                        }
                    }
                }
            }
        }

    private fun SqliteConnectionManager.tableExists(name: String): Boolean =
        withConnection { connection ->
            connection
                .prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")
                .use { statement ->
                    statement.setString(1, name)
                    statement.executeQuery().use { it.next() }
                }
        }

    private fun migration(
        version: Int,
        name: String,
        sql: String,
    ) = Migration(
        version = version,
        name = name,
        fileName = "V%03d__%s.sql".format(version, name),
        sql = sql,
    )

    @Test
    fun `空のDBに最新まで適用できる`() {
        withManager { manager ->
            MigrationRunner(manager).migrate(MigrationLoader.load())

            assertTrue(manager.tableExists("health_check"), "health_check が作られていない")
            assertEquals(MigrationLoader.load().map { it.version }, manager.appliedVersions())
        }
    }

    @Test
    fun `2回続けて適用しても壊れない`() {
        withManager { manager ->
            val runner = MigrationRunner(manager)

            runner.migrate(MigrationLoader.load())
            // 2 回目は未適用が無いので何もしない。CREATE TABLE が再実行されると落ちる
            runner.migrate(MigrationLoader.load())

            assertEquals(MigrationLoader.load().map { it.version }, manager.appliedVersions())
        }
    }

    @Test
    fun `プロセスを分けて適用し直しても壊れない`() {
        withManager { manager -> MigrationRunner(manager).migrate(MigrationLoader.load()) }
        withManager { manager -> MigrationRunner(manager).migrate(MigrationLoader.load()) }

        withManager { manager ->
            assertEquals(MigrationLoader.load().map { it.version }, manager.appliedVersions())
        }
    }

    @Test
    fun `未適用のバージョンだけが追加で適用される`() {
        val first = migration(1, "first", "CREATE TABLE first (id INTEGER PRIMARY KEY);")
        val second = migration(2, "second", "CREATE TABLE second (id INTEGER PRIMARY KEY);")

        withManager { manager ->
            MigrationRunner(manager).migrate(listOf(first))
            assertEquals(listOf(1), manager.appliedVersions())

            MigrationRunner(manager).migrate(listOf(first, second))

            assertEquals(listOf(1, 2), manager.appliedVersions())
            assertTrue(manager.tableExists("second"))
        }
    }

    @Test
    fun `適用済みのファイルが書き換えられていたら例外になる`() {
        val original = migration(1, "first", "CREATE TABLE first (id INTEGER PRIMARY KEY);")
        val modified = migration(1, "first", "CREATE TABLE first (id INTEGER PRIMARY KEY, name TEXT);")

        withManager { manager ->
            MigrationRunner(manager).migrate(listOf(original))

            val error =
                assertFailsWith<IllegalStateException> {
                    MigrationRunner(manager).migrate(listOf(modified))
                }

            assertTrue(
                error.message.orEmpty().contains("内容が変わっている"),
                "想定と違うエラー: ${error.message}",
            )
        }
    }

    @Test
    fun `DBにあるバージョンのファイルが無ければ例外になる`() {
        val first = migration(1, "first", "CREATE TABLE first (id INTEGER PRIMARY KEY);")
        val second = migration(2, "second", "CREATE TABLE second (id INTEGER PRIMARY KEY);")

        withManager { manager ->
            MigrationRunner(manager).migrate(listOf(first, second))

            val error =
                assertFailsWith<IllegalStateException> {
                    MigrationRunner(manager).migrate(listOf(first))
                }

            assertTrue(
                error.message.orEmpty().contains("対応するファイルが無い"),
                "想定と違うエラー: ${error.message}",
            )
        }
    }

    @Test
    fun `失敗したマイグレーションは丸ごとロールバックされる`() {
        val broken =
            migration(
                version = 1,
                name = "broken",
                sql =
                    """
                    CREATE TABLE ok (id INTEGER PRIMARY KEY);
                    CREATE TABLE ng (
                    """.trimIndent(),
            )

        withManager { manager ->
            assertFailsWith<java.sql.SQLException> {
                MigrationRunner(manager).migrate(listOf(broken))
            }

            assertTrue(!manager.tableExists("ok"), "前半の CREATE TABLE がロールバックされていない")
            assertEquals(emptyList(), manager.appliedVersions())
        }
    }

    @Test
    fun `複数ファイルはバージョンの昇順で適用される`() {
        // 依存関係のあるスキーマ: 2 が 1 のテーブルを参照する
        val first = migration(1, "parent", "CREATE TABLE parent (id INTEGER PRIMARY KEY);")
        val second =
            migration(
                version = 2,
                name = "child",
                sql = "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent (id));",
            )

        withManager { manager ->
            // 渡す順を逆にしても、バージョン順に適用されるので成功する
            MigrationRunner(manager).migrate(listOf(first, second).sortedBy { it.version })

            assertEquals(listOf(1, 2), manager.appliedVersions())
        }
    }
}
