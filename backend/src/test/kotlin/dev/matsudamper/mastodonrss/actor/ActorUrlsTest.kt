package dev.matsudamper.mastodonrss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// URL の綴りは一度 Mastodon 側にキャッシュされると直せないので、
// 組み立ての結果を文字列として固定しておく。
class ActorUrlsTest {
    private val urls = ActorUrls(domain = "example.com", username = "admin")

    @Test
    fun `ドメインとユーザー名から URL を組み立てる`() {
        assertEquals("acct:admin@example.com", urls.acct)
        assertEquals("https://example.com/users/admin", urls.actorId)
        assertEquals("https://example.com/users/admin/inbox", urls.inbox)
        assertEquals("https://example.com/users/admin/outbox", urls.outbox)
        assertEquals("https://example.com/users/admin/followers", urls.followers)
        assertEquals("https://example.com/users/admin/following", urls.following)
        assertEquals("https://example.com/users/admin#main-key", urls.publicKeyId)
    }

    @Test
    fun `acct でも Actor の URL でも一致する`() {
        assertTrue(urls.matches("acct:admin@example.com"))
        assertTrue(urls.matches("admin@example.com"))
        assertTrue(urls.matches("https://example.com/users/admin"))
    }

    @Test
    fun `大文字小文字と前後の空白は無視する`() {
        assertTrue(urls.matches("acct:Admin@Example.com"))
        assertTrue(urls.matches(" acct:admin@example.com "))
    }

    @Test
    fun `別のユーザーや別のドメインには一致しない`() {
        assertFalse(urls.matches("acct:other@example.com"))
        assertFalse(urls.matches("acct:admin@example.org"))
        assertFalse(urls.matches("acct:admin"))
        assertFalse(urls.matches(""))
    }
}
