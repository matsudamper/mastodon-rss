package net.matsudamper.mastodon.rss.repository

import net.matsudamper.mastodon.rss.repository.sqlite.SqliteRepositories

/**
 * DB アクセスの入口。
 *
 * 実装は [SqliteRepositories] が持つが `internal` なので外からは見えない。
 * 呼び出し側が触れるのはこの interface と [DatabaseConfig] だけで、
 * JDBC や SQL がモジュールの外に漏れないようにする。
 *
 * フィードやフォロワーの repository は、スキーマが決まる Phase 3 でここに生やす。
 */
interface Repositories : AutoCloseable {
    /**
     * DB に書き込んで読み戻せることを確認する。書けない場合は例外を投げる。
     *
     * native バイナリでは SQLite のネイティブライブラリの展開に失敗しても
     * 起動自体は通ってしまうことがあるため、実際に往復させて確かめる。
     */
    fun verifyWritable()
}

/**
 * [Repositories] を作り、DB への接続を開く。
 *
 * 接続に失敗した場合は例外を投げる。使い終わったら [Repositories.close] を呼ぶこと。
 */
fun createRepositories(config: DatabaseConfig): Repositories = SqliteRepositories(config)
