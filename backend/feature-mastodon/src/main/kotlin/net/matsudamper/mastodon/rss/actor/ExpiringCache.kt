package net.matsudamper.mastodon.rss.actor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 有効期限つきのキーバリューキャッシュ。
 *
 * 相手のアクター文書を持ち回るためだけのもので、モジュールの外には出さない。
 * 元は `:backend:repository` に置いていたが、そちらは SQLite と jOOQ を抱える
 * アプリ側のモジュールで、ActivityPub の実装が依存するものではない。
 * このモジュールだけで完結させるために持ってきた。
 *
 * interface にしてあるのは差し替えのため。プロセスをまたいでキャッシュしたくなったら
 * [createExpiringCache] の戻りを変えれば済む。
 *
 * キーは外から来る値（相手が名乗った `keyId`）になりうる。使い捨てのキーを
 * いくらでも作れる以上、期限切れの行は読み直されなくても片付ける必要がある。
 */
internal interface ExpiringCache<K : Any, V : Any> {
    /** 有効期限内なら値を返す。無い場合と期限切れの場合はどちらも null */
    fun get(key: K): V?

    /** [ttlMillis] だけ経ったら自動的に無効になる値を保存する */
    fun put(
        key: K,
        value: V,
        ttlMillis: Long,
    )

    /**
     * 有効期限内の値が無いときだけ保存して true を返す。
     *
     * [get] で確かめてから [put] すると、その間に同じことをした別のスレッドと
     * 両方が通る。「1 つだけ通す」ことが要る側はこちらを使う
     */
    fun tryPut(
        key: K,
        value: V,
        ttlMillis: Long,
    ): Boolean
}

/**
 * [ExpiringCache] を作る。
 *
 * 実装はプロセスのメモリ上に持つだけで、再起動やプロセス間では共有しない。
 */
internal fun <K : Any, V : Any> createExpiringCache(): ExpiringCache<K, V> = InMemoryExpiringCache()

private class InMemoryExpiringCache<K : Any, V : Any> : ExpiringCache<K, V> {
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    /**
     * 保存した回数。期限切れを片付ける頻度を決めるのに使う
     */
    private val puts = AtomicInteger()

    override fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.isExpired()) {
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
        pruneIfNeeded()
    }

    override fun tryPut(
        key: K,
        value: V,
        ttlMillis: Long,
    ): Boolean {
        val entry = Entry(value = value, expiresAtMillis = System.currentTimeMillis() + ttlMillis)

        // 入れ替えるのは期限切れの行だけ。compute の中は同じキーについて 1 つずつ実行される
        val stored = entries.compute(key) { _, existing ->
            if (existing != null && !existing.isExpired()) existing else entry
        }

        pruneIfNeeded()
        return stored === entry
    }

    /**
     * 期限切れの行を片付ける。
     *
     * [get] だけに任せると、二度と読まれないキーの行が残り続ける。キーは外から
     * いくらでも作れるので、放っておくとリクエストを重ねるだけでメモリが増える。
     * 毎回全部を見ると保存のたびに行数ぶんの仕事になるので、間隔を空けて行う
     */
    private fun pruneIfNeeded() {
        if (puts.incrementAndGet() % PRUNE_INTERVAL != 0) return

        entries.entries.removeIf { it.value.isExpired() }
    }

    private class Entry<V>(
        val value: V,
        val expiresAtMillis: Long,
    ) {
        fun isExpired(): Boolean = expiresAtMillis <= System.currentTimeMillis()
    }

    private companion object {
        /**
         * 片付けるまでに受け付ける保存の回数。
         *
         * 残りうる期限切れの行数の上限がこれで決まる。小さくすると片付けが増え、
         * 大きくすると残る行が増える
         */
        const val PRUNE_INTERVAL = 256
    }
}
