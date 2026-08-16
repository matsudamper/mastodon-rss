package net.matsudamper.mastodon.rss.repository.sqlite

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 * jOOQ で 1 トランザクションを処理する。
 *
 * 接続の一生（PRAGMA の適用・アクセスの直列化・close）は [SqliteConnectionManager] が持ち、
 * こちらは借りた接続に jOOQ の設定を被せるだけ。接続の扱いに jOOQ を混ぜないための分け方で、
 * SQL を組み立てる側は接続の都合を知らずに済む。
 *
 * SQL を組み立てる経路は全てここを通す。設定と下の静的初期化子が散らないようにするため。
 */
internal class SqliteJooq(
    private val connectionManager: SqliteConnectionManager,
) {
    /**
     * [DSLContext] は接続に紐付くので毎回作る。作るのは設定を包む薄いオブジェクトで、
     * 重いのは [SETTINGS] を持つ側なのでそちらだけ使い回す。
     */
    fun <T> transaction(block: (DSLContext) -> T): T = connectionManager.transaction { connection ->
        block(DSL.using(connection, SQLDialect.SQLITE, SETTINGS))
    }

    private companion object {
        init {
            // 何もしないと最初のクエリでロゴと「豆知識」がログに出る。
            // jOOQ はこれを DefaultRenderContext の静的初期化子で見るので、
            // そこに到達する前に立てる必要がある。下の SETTINGS より先に、
            // ここで立てておけば間に合う。
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
