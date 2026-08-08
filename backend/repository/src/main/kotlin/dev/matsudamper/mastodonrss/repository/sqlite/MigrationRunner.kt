package dev.matsudamper.mastodonrss.repository.sqlite

import java.sql.Connection
import java.time.Instant

/**
 * 未適用のマイグレーションをバージョン昇順で適用する。
 *
 * 1 ファイル = 1 トランザクション。途中で失敗したファイルは丸ごとロールバックされるので、
 * 「半分だけ適用された」状態にはならない。
 */
internal class MigrationRunner(
    private val connectionManager: SqliteConnectionManager,
) {
    fun migrate(migrations: List<Migration>) {
        connectionManager.withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(CREATE_SCHEMA_VERSION)
            }
        }

        val applied = connectionManager.withConnection { connection -> readApplied(connection) }

        verifyApplied(applied = applied, migrations = migrations)

        migrations
            .filter { it.version !in applied }
            .forEach { migration -> apply(migration) }
    }

    /**
     * 適用済みとして記録されている内容が、いま手元にあるファイルと食い違っていないか調べる。
     *
     * ここで弾かないと、スキーマが期待と違う DB のまま動き続けることになる。
     */
    private fun verifyApplied(applied: Map<Int, AppliedMigration>, migrations: List<Migration>) {
        val byVersion = migrations.associateBy { it.version }

        applied.forEach { (version, appliedMigration) ->
            val migration =
                byVersion[version]
                    ?: error(
                        "DB には V$version (${appliedMigration.name}) が適用済みだが、対応するファイルが無い。" +
                            "DB より古いバイナリで起動している可能性がある",
                    )

            check(migration.checksum == appliedMigration.checksum) {
                "適用済みのマイグレーション ${migration.fileName} の内容が変わっている。" +
                    "適用時=${appliedMigration.checksum} 現在=${migration.checksum}"
            }
        }
    }

    private fun apply(migration: Migration) {
        connectionManager.transaction { connection ->
            connection.createStatement().use { statement ->
                splitSqlStatements(migration.sql).forEach { sql ->
                    statement.execute(sql)
                }
            }

            connection.prepareStatement(INSERT_SCHEMA_VERSION).use { statement ->
                statement.setInt(1, migration.version)
                statement.setString(2, migration.name)
                statement.setString(3, migration.checksum)
                statement.setString(4, Instant.now().toString())
                statement.executeUpdate()
            }
        }
    }

    private fun readApplied(connection: Connection): Map<Int, AppliedMigration> =
        connection.createStatement().use { statement ->
            statement.executeQuery(SELECT_SCHEMA_VERSION).use { resultSet ->
                buildMap {
                    while (resultSet.next()) {
                        val version = resultSet.getInt(1)
                        put(
                            version,
                            AppliedMigration(
                                version = version,
                                name = resultSet.getString(2),
                                checksum = resultSet.getString(3),
                            ),
                        )
                    }
                }
            }
        }

    private data class AppliedMigration(
        val version: Int,
        val name: String,
        val checksum: String,
    )

    private companion object {
        const val CREATE_SCHEMA_VERSION = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                checksum TEXT NOT NULL,
                applied_at TEXT NOT NULL
            )
        """

        const val SELECT_SCHEMA_VERSION =
            "SELECT version, name, checksum FROM schema_version ORDER BY version"

        const val INSERT_SCHEMA_VERSION =
            "INSERT INTO schema_version (version, name, checksum, applied_at) VALUES (?, ?, ?, ?)"
    }
}
