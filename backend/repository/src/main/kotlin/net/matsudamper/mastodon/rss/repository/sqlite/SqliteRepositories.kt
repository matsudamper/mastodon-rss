package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import java.time.Instant

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config)

    init {
        // 未適用のマイグレーションがある状態でリクエストを受けても失敗するだけなので、
        // 接続を開いた直後に適用しきる
        try {
            MigrationRunner(connectionManager).migrate(MigrationLoader.load())
        } catch (e: Throwable) {
            connectionManager.close()
            throw e
        }
    }

    override fun verifyWritable() {
        val writtenAt = Instant.now().toString()

        val readBack =
            connectionManager.transaction { connection ->
                connection.prepareStatement(UPSERT_HEALTH_CHECK).use { statement ->
                    statement.setString(1, writtenAt)
                    statement.executeUpdate()
                }

                connection.prepareStatement(SELECT_HEALTH_CHECK).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.getString(1) else null
                    }
                }
            }

        check(readBack == writtenAt) {
            "DB に書き込んだ値を読み戻せなかった: 書き込み=$writtenAt 読み戻し=$readBack"
        }
    }

    override fun close() {
        connectionManager.close()
    }

    private companion object {
        const val UPSERT_HEALTH_CHECK = """
            INSERT INTO health_check (id, checked_at) VALUES (1, ?)
            ON CONFLICT (id) DO UPDATE SET checked_at = excluded.checked_at
        """

        const val SELECT_HEALTH_CHECK = "SELECT checked_at FROM health_check WHERE id = 1"
    }
}
