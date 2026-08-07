package dev.matsudamper.mastodonrss.repository.sqlite

import dev.matsudamper.mastodonrss.repository.DatabaseConfig
import dev.matsudamper.mastodonrss.repository.Repositories
import java.time.Instant

internal class SqliteRepositories(config: DatabaseConfig) : Repositories {
    private val connectionManager = SqliteConnectionManager(config)

    override fun verifyWritable() {
        val writtenAt = Instant.now().toString()

        val readBack = connectionManager.transaction { connection ->
            // マイグレーションはまだ無いので、ここで自前でテーブルを用意する。
            // 0-5 でマイグレーションを入れたら V001 に移す
            connection.createStatement().use { statement ->
                statement.execute(CREATE_HEALTH_CHECK)
            }

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
        // 行は常に 1 件だけ。CHECK で id を固定しておくと UPSERT の対象が一意になる
        const val CREATE_HEALTH_CHECK = """
            CREATE TABLE IF NOT EXISTS health_check (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                checked_at TEXT NOT NULL
            )
        """

        const val UPSERT_HEALTH_CHECK = """
            INSERT INTO health_check (id, checked_at) VALUES (1, ?)
            ON CONFLICT (id) DO UPDATE SET checked_at = excluded.checked_at
        """

        const val SELECT_HEALTH_CHECK = "SELECT checked_at FROM health_check WHERE id = 1"
    }
}
