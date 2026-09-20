package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 相手のサーバーを引きに行く回数を抑える土台。
// 「1 つだけ通す」が壊れると、通らない署名を並べるだけで取得を増やせる。
class ExpiringCacheTest {
    @Test
    fun `期限内なら覚えている値を返す`() {
        val cache = createExpiringCache<String, String>()

        cache.put(key = "a", value = "覚えた値", ttlMillis = 60_000)

        assertEquals("覚えた値", cache.get("a"))
    }

    @Test
    fun `期限が切れていれば返さない`() {
        val cache = createExpiringCache<String, String>()

        cache.put(key = "a", value = "覚えた値", ttlMillis = 0)

        assertNull(cache.get("a"))
    }

    @Test
    fun `期限内の値があれば tryPut は通らない`() {
        val cache = createExpiringCache<String, String>()
        cache.put(key = "a", value = "先に入れた値", ttlMillis = 60_000)

        assertFalse(cache.tryPut(key = "a", value = "後から入れる値", ttlMillis = 60_000))
        assertEquals("先に入れた値", cache.get("a"))
    }

    @Test
    fun `期限が切れていれば tryPut は通る`() {
        val cache = createExpiringCache<String, String>()
        cache.put(key = "a", value = "先に入れた値", ttlMillis = 0)

        assertTrue(cache.tryPut(key = "a", value = "後から入れる値", ttlMillis = 60_000))
        assertEquals("後から入れる値", cache.get("a"))
    }

    @Test
    fun `覚えていないキーなら tryPut は通る`() {
        val cache = createExpiringCache<String, String>()

        assertTrue(cache.tryPut(key = "a", value = "入れる値", ttlMillis = 60_000))
    }
}
