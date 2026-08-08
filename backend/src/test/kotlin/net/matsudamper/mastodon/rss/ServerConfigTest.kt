package net.matsudamper.mastodon.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// ユーザー名の検証を確認する。環境変数からの組み立ては AppConfigTest 側で見る。
// ユーザー名は URL のパスと acct の両方に入るので、
// 区切り文字が混ざると別のものを指してしまう。
class ServerConfigTest {
    private fun config(actorUsername: String): ServerConfig =
        ServerConfig(
            host = AppConfig.DEFAULT_HOST,
            port = AppConfig.DEFAULT_PORT,
            domain = "example.com",
            actorUsername = actorUsername,
        )

    @Test
    fun `ユーザー名に使えない文字があれば落ちる`() {
        assertFailsWith<IllegalArgumentException> { config("ad/min") }
        assertFailsWith<IllegalArgumentException> { config("ad min") }
        assertFailsWith<IllegalArgumentException> { config("admin@example.com") }
        assertFailsWith<IllegalArgumentException> { config("管理者") }
        assertFailsWith<IllegalArgumentException> { config(".admin") }
    }

    @Test
    fun `ユーザー名に使える文字は通る`() {
        assertEquals("feed_1", config("feed_1").actorUsername)
        assertEquals("feed.1", config("feed.1").actorUsername)
        assertEquals("feed-1", config("feed-1").actorUsername)
    }
}
