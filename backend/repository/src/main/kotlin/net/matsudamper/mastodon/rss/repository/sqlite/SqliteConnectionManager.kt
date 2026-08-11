package net.matsudamper.mastodon.rss.repository.sqlite

import java.nio.file.Files
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import org.sqlite.SQLiteDataSource

/**
 * SQLite への接続を持ち、PRAGMA の適用とアクセスの直列化を担う。
 *
 * SQLite はライターを 1 本しか取れないため、汎用のコネクションプールは過剰になる。
 * まずは接続 1 本 + ロックで直列化する構成で始める。読み取りが詰まるようなら、
 * 読み取り用の接続を複数持つ形に広げる。HikariCP は依存と native-image 設定を
 * 増やしたくないので入れない。
 */
internal class SqliteConnectionManager(
    config: DatabaseConfig,
) : AutoCloseable {
    private val lock = ReentrantLock()
    private val connection: Connection

    init {
        val path = config.path.toAbsolutePath().normalize()
        // 初回起動時に data/ が無いことは普通なので、無ければ作る
        Files.createDirectories(path.parent)

        // DriverManager 経由だと ServiceLoader でドライバを探すことになる。
        // native-image では解決に追加設定が要ることがあるため、直接 DataSource を使う
        val dataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:$path"
            }
        connection = dataSource.connection
        try {
            applyPragmas(connection)
        } catch (e: Throwable) {
            connection.close()
            throw e
        }
    }

    /** 接続を借りて処理する。autocommit のまま実行される */
    fun <T> withConnection(block: (Connection) -> T): T = lock.withLock { block(connection) }

    /** 接続を借りて 1 トランザクションで処理する。例外が出たらロールバックする */
    fun <T> transaction(block: (Connection) -> T): T =
        lock.withLock {
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

    override fun close() {
        lock.withLock { connection.close() }
    }

    private companion object {
        /**
         * 接続時に必ず入れる PRAGMA。
         *
         * 接続を増やしたときに入れ忘れないよう、ここ 1 箇所にまとめる。
         */
        fun applyPragmas(connection: Connection) {
            connection.createStatement().use { statement ->
                // 読み書きの並行性を上げる。ファイル DB のみ有効で、設定は DB ファイルに残る
                statement.execute("PRAGMA journal_mode = WAL")
                // SQLite は既定で OFF。接続ごとに ON にしないと外部キーが効かない
                statement.execute("PRAGMA foreign_keys = ON")
                // ロック待ちで即座に SQLITE_BUSY を返さないようにする（ミリ秒）
                statement.execute("PRAGMA busy_timeout = 5000")
                // WAL 前提での耐久性と速度の折衷
                statement.execute("PRAGMA synchronous = NORMAL")
            }
        }
    }
}
