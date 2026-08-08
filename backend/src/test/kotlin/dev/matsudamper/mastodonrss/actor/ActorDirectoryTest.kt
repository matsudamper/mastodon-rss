package dev.matsudamper.mastodonrss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// WebFinger とパスで判定がずれると「検索には出るが開けない」という
// 分かりにくい壊れ方をするので、両方の入口を同じだけ確かめる。
class ActorDirectoryTest {
    private val directory = ActorDirectory(ActorUrls(domain = "example.com", username = "admin"))

    @Test
    fun `固定アクターを引ける`() {
        assertEquals("https://example.com/users/admin", directory.resolve("admin")?.actorId)
        // Mastodon 側の扱いに合わせて大文字小文字は区別しない
        assertEquals("https://example.com/users/admin", directory.resolve("ADMIN")?.actorId)
    }

    @Test
    fun `test で始まる名前は何でも引ける`() {
        assertEquals("https://example.com/users/test-1", directory.resolve("test-1")?.actorId)
        assertEquals("https://example.com/users/test-2", directory.resolve("test-2")?.actorId)
        assertEquals(
            "https://example.com/users/test-20260808_a",
            directory.resolve("test-20260808_a")?.actorId,
        )
    }

    @Test
    fun `test の接頭辞は小文字ちょうどで一致させる`() {
        // 受けてしまうと test-1 と Test-1 が別のアクターとして生える
        assertNull(directory.resolve("Test-1"))
        assertNull(directory.resolve("TEST-1"))
    }

    @Test
    fun `test だけや使えない文字を含む名前は引けない`() {
        assertNull(directory.resolve("test-"))
        assertNull(directory.resolve("test"))
        assertNull(directory.resolve("test-1/inbox"))
        assertNull(directory.resolve("test-1@example.com"))
        assertNull(directory.resolve("test-あ"))
        assertNull(directory.resolve(null))
        assertNull(directory.resolve(""))
    }

    @Test
    fun `関係ない名前は引けない`() {
        assertNull(directory.resolve("other"))
        assertNull(directory.resolve("gihyo"))
    }

    @Test
    fun `resource は acct でも Actor の URL でも引ける`() {
        assertEquals("acct:admin@example.com", directory.resolveResource("acct:admin@example.com")?.acct)
        assertEquals("acct:admin@example.com", directory.resolveResource("admin@example.com")?.acct)
        assertEquals(
            "acct:admin@example.com",
            directory.resolveResource("https://example.com/users/admin")?.acct,
        )
        assertEquals("acct:test-1@example.com", directory.resolveResource("acct:test-1@example.com")?.acct)
        assertEquals(
            "acct:test-1@example.com",
            directory.resolveResource("https://example.com/users/test-1")?.acct,
        )
    }

    @Test
    fun `resource の大文字小文字と前後の空白は無視する`() {
        assertEquals("acct:admin@example.com", directory.resolveResource("ACCT:Admin@Example.com")?.acct)
        assertEquals("acct:admin@example.com", directory.resolveResource(" acct:admin@example.com ")?.acct)
    }

    @Test
    fun `別ドメインや壊れた resource は引けない`() {
        assertNull(directory.resolveResource("acct:admin@example.org"))
        assertNull(directory.resolveResource("https://example.org/users/admin"))
        assertNull(directory.resolveResource("acct:admin"))
        assertNull(directory.resolveResource("acct:@example.com"))
        assertNull(directory.resolveResource("https://example.com/users/test-1/inbox"))
        assertNull(directory.resolveResource("https://example.com/users/"))
        assertNull(directory.resolveResource(""))
        assertNull(directory.resolveResource("   "))
    }
}
