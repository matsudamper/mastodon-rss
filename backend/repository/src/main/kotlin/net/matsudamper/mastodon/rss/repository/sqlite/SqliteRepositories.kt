package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.jooq.Tables.HEALTH_CHECK
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.time.Instant

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    // スキーマの適用はしない。実 DB へは sqlite3def で手適用する運用
    // （db/schema.sql と同じ場所の README を参照）。空の DB で起動した場合は
    // verifyWritable() が no such table で落ちるので、適用忘れはそこで分かる
    private val connectionManager = SqliteConnectionManager(config)

    override fun verifyWritable() {
        val writtenAt = Instant.now().toString()

        val readBack =
            transaction { dsl ->
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

    /**
     * 1 トランザクションを jOOQ で処理する。
     *
     * [DSLContext] は接続に紐付くので毎回作る。作るのは設定を包む薄いオブジェクトで、
     * 重いのは [SETTINGS] を持つ側なのでそちらだけ使い回す。
     */
    private fun <T> transaction(block: (DSLContext) -> T): T =
        connectionManager.transaction { connection ->
            block(DSL.using(connection, SQLDialect.SQLITE, SETTINGS))
        }

    private companion object {
        init {
            // 何もしないと最初のクエリでロゴと「豆知識」がログに出る。
            // jOOQ はこれを DefaultRenderContext の静的初期化子で見るので、
            // そこに到達する前に立てる必要がある。DB を触る経路はこのクラスしかないので、
            // 下の SETTINGS より先に、ここで立てておけば間に合う。
            //
            // これが効くのは JVM で動かしたときだけ。native バイナリでは jOOQ を
            // ビルド時初期化にしている都合で、この静的初期化子はイメージを作る時点で
            // 走り終わっている。そちらは :backend のビルド引数で渡している
            System.setProperty("org.jooq.no-logo", "true")
            System.setProperty("org.jooq.no-tips", "true")
        }

        val SETTINGS: Settings =
            Settings()
                // SQLite にスキーマもカタログも無い。付けて出すと構文にならない
                .withRenderSchema(false)
                .withRenderCatalog(false)
    }
}
