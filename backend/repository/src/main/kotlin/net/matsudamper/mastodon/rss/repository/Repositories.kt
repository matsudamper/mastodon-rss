package net.matsudamper.mastodon.rss.repository

import net.matsudamper.mastodon.rss.repository.sqlite.SqliteRepositories

/**
 * DB アクセスの入口。
 *
 * モジュールの責務は「どこからデータを読むかを呼び出し側から隠す」ことで、
 * いまはその実装が DB だけなのでここが唯一の入口になっている。
 * DB を経由しない取得口を足す場合もこのモジュールに並べる。
 *
 * 実装は [SqliteRepositories] が持つが `internal` なので外からは見えない。
 * 呼び出し側が触れるのはこの interface と [DatabaseConfig] だけで、
 * JDBC も jOOQ もモジュールの外に漏れないようにする。
 *
 * フィードの repository（[FeedRepository] と [FeedItemRepository]）は interface だけ
 * 置いてある。テーブルが無く、jOOQ の生成物も無いので実装はまだ無く、ここからも取れない。
 * Phase 5 で実装を入れるときにプロパティを足す。
 */
interface Repositories : AutoCloseable {
    val accounts: AccountRepository

    val followers: FollowerRepository

    /**
     * DB に書き込んで読み戻せることを確認する。書けない場合は例外を投げる。
     *
     * native バイナリでは SQLite のネイティブライブラリの展開に失敗しても
     * 起動自体は通ってしまうことがあるため、実際に往復させて確かめる。
     */
    fun verifyWritable()

    /**
     * 抱えている接続を閉じ、書き込みを置き場所に確定させる。
     *
     * 閉じた後は、DB のファイルをそのままコピーしたり、別のツールで開いて
     * 編集したりしてよい状態になっていること。何を確定させれば良いかは
     * 保存先ごとに違うので、呼び出し側からは「終了処理はここを呼べば済む」
     * とだけ見えるようにする。
     */
    override fun close()
}

/**
 * [Repositories] を作り、DB への接続を開く。
 *
 * 接続に失敗した場合は例外を投げる。使い終わったら [Repositories.close] を呼ぶこと。
 */
fun createRepositories(config: DatabaseConfig): Repositories = SqliteRepositories(config)
