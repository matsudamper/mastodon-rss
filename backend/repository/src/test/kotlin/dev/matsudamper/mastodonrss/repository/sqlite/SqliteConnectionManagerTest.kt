package dev.matsudamper.mastodonrss.repository.sqlite

import dev.matsudamper.mastodonrss.repository.DatabaseConfig
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

// 接続まわりの土台を確認する。
// PRAGMA が実際に効いていること、SQL が通ること、
// トランザクションが失敗時にロールバックされること。
class SqliteConnectionManagerTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-connection-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createManager(): SqliteConnectionManager =
        SqliteConnectionManager(DatabaseConfig(path = tempDir.resolve("test.db")))

    @Test
    fun `接続時にPRAGMAが適用されている`() {
        createManager().use { manager ->
            assertEquals("wal", manager.queryString("PRAGMA journal_mode").lowercase())
            assertEquals("1", manager.queryString("PRAGMA foreign_keys"))
            assertEquals("5000", manager.queryString("PRAGMA busy_timeout"))
            // synchronous は数値で返る。NORMAL は 1
            assertEquals("1", manager.queryString("PRAGMA synchronous"))
        }
    }

    @Test
    fun `テーブル作成からINSERT SELECTまで通る`() {
        createManager().use { manager ->
            manager.withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                    statement.execute("INSERT INTO sample (id, name) VALUES (1, 'テスト')")
                }
            }

            val name =
                manager.withConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT name FROM sample WHERE id = 1").use { resultSet ->
                            resultSet.next()
                            resultSet.getString(1)
                        }
                    }
                }

            assertEquals("テスト", name)
        }
    }

    @Test
    fun `外部キー制約が効く`() {
        createManager().use { manager ->
            manager.withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)")
                    statement.execute(
                        "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL " +
                            "REFERENCES parent (id))",
                    )
                }
            }

            assertFailsWith<java.sql.SQLException> {
                manager.withConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("INSERT INTO child (id, parent_id) VALUES (1, 999)")
                    }
                }
            }
        }
    }

    @Test
    fun `トランザクション内で例外が出たらロールバックされる`() {
        createManager().use { manager ->
            manager.withConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE sample (id INTEGER PRIMARY KEY)")
                }
            }

            assertFailsWith<IllegalStateException> {
                manager.transaction { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("INSERT INTO sample (id) VALUES (1)")
                    }
                    error("意図的に失敗させる")
                }
            }

            val exists =
                manager.withConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT COUNT(*) FROM sample").use { resultSet ->
                            resultSet.next()
                            resultSet.getInt(1) > 0
                        }
                    }
                }

            assertFalse(exists, "ロールバックされていない")
        }
    }

    @Test
    fun `トランザクションを抜けたらautocommitに戻る`() {
        createManager().use { manager ->
            manager.transaction { }

            val autoCommit = manager.withConnection(Connection::getAutoCommit)

            assertEquals(true, autoCommit)
        }
    }

    private fun SqliteConnectionManager.queryString(sql: String): String =
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    resultSet.next()
                    resultSet.getString(1)
                }
            }
        }
}
