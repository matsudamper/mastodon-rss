package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.AccountProfileRepository
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.jooq.Tables.HEALTH_CHECK

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    // スキーマの適用はしない。実 DB へは sqlite3def で手適用する運用
    // （db/schema.sql と同じ場所の README を参照）。空の DB で起動した場合は
    // verifyWritable() が no such table で落ちるので、適用忘れはそこで分かる
    private val connectionManager = SqliteConnectionManager(config)
    private val jooq = SqliteJooq(connectionManager)

    override val accounts: AccountRepository = SqliteAccountRepository(jooq)

    override val accountProfiles: AccountProfileRepository = SqliteAccountProfileRepository(jooq)

    override val followers: FollowerRepository = SqliteFollowerRepository(jooq)

    override val notes: NoteRepository = SqliteNoteRepository(jooq)

    override fun verifyWritable() {
        val writtenAt = Instant.now().toString()

        val readBack =
            jooq.transaction { dsl ->
                dsl
                    .insertInto(HEALTH_CHECK)
                    .set(HEALTH_CHECK.ID, 1)
                    .set(HEALTH_CHECK.CHECKED_AT, writtenAt)
                    .onConflict(HEALTH_CHECK.ID)
                    .doUpdate()
                    .set(HEALTH_CHECK.CHECKED_AT, writtenAt)
                    .execute()

                dsl
                    .select(HEALTH_CHECK.CHECKED_AT)
                    .from(HEALTH_CHECK)
                    .where(HEALTH_CHECK.ID.eq(1))
                    .fetchOne(HEALTH_CHECK.CHECKED_AT)
            }

        check(readBack == writtenAt) {
            "DB に書き込んだ値を読み戻せなかった: 書き込み=$writtenAt 読み戻し=$readBack"
        }
    }

    override fun close() {
        connectionManager.close()
    }
}
