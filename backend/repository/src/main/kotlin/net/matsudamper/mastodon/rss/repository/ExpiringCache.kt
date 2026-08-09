package net.matsudamper.mastodon.rss.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * 有効期限つきのキーバリューキャッシュ。
 *
 * DB そのものではないが、「どこからデータを読むか」を呼び出し側から隠すという
 * repository の役割としてはここに置く。実装は [InMemoryExpiringCache] が持つが
 * `private` なので外からは見えない。呼び出し側が触れるのはこの interface と
 * [createExpiringCache] だけで、テスト用のフェイクへの差し替えや、将来 DB / Redis
 * 等の永続キャッシュへの置き換えをここだけ見れば済むようにする。
 */
interface ExpiringCache<K : Any, V : Any> {
    /** 有効期限内なら値を返す。無い場合と期限切れの場合はどちらも null */
    fun get(key: K): V?

    /** [ttlMillis] だけ経ったら自動的に無効になる値を保存する */
    fun put(
        key: K,
        value: V,
        ttlMillis: Long,
    )
}

/**
 * [ExpiringCache] を作る。
 *
 * 実装はプロセスのメモリ上に持つだけで、再起動やプロセス間では共有しない。
 */
fun <K : Any, V : Any> createExpiringCache(): ExpiringCache<K, V> = InMemoryExpiringCache()

private class InMemoryExpiringCache<K : Any, V : Any> : ExpiringCache<K, V> {
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    override fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= System.currentTimeMillis()) {
            // 期限切れ。remove(key, value) にしておくと、他スレッドが先に
            // 新しい値を put していた場合にそれを消してしまわない
            entries.remove(key, entry)
            return null
        }
        return entry.value
    }

    override fun put(
        key: K,
        value: V,
        ttlMillis: Long,
    ) {
        entries[key] = Entry(value = value, expiresAtMillis = System.currentTimeMillis() + ttlMillis)
    }

    private class Entry<V>(
        val value: V,
        val expiresAtMillis: Long,
    )
}
