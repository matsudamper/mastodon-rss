package net.matsudamper.mastodon.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 環境変数の読み取りを確認する。
// ドメインとユーザー名はアクター ID に焼き込まれて後から変えられないので、
// 表記ゆれをここで吸収し、決められない場合は起動させない。
class ServerConfigTest {
    private fun config(vararg env: Pair<String, String>): ServerConfig {
        val map = env.toMap() + ("DOMAIN" to "example.com")
        return ServerConfig.from { map[it] }
    }

    @Test
    fun `DOMAIN 以外は未設定なら既定値になる`() {
        val config = config()

        assertEquals(ServerConfig.DEFAULT_HOST, config.host)
        assertEquals(ServerConfig.DEFAULT_PORT, config.port)
        assertEquals("admin", config.actorUsername)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val config =
            config(
                "HOST" to "127.0.0.1",
                "PORT" to "9000",
                "ACTOR_USERNAME" to "feed1",
            )

        assertEquals("127.0.0.1", config.host)
        assertEquals(9000, config.port)
        assertEquals("feed1", config.actorUsername)
    }

    @Test
    fun `数値でない PORT は既定値に落ちる`() {
        assertEquals(ServerConfig.DEFAULT_PORT, config("PORT" to "ポート").port)
    }

    @Test
    fun `空文字は未設定として扱う`() {
        val config = config("HOST" to "", "PORT" to "", "ACTOR_USERNAME" to "   ")

        assertEquals(ServerConfig.DEFAULT_HOST, config.host)
        assertEquals(ServerConfig.DEFAULT_PORT, config.port)
        assertEquals(ServerConfig.DEFAULT_ACTOR_USERNAME, config.actorUsername)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String = ServerConfig.from { if (it == "DOMAIN") raw else null }.domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { ServerConfig.from { null } }
        assertFailsWith<IllegalArgumentException> { ServerConfig.from { "   " } }
    }

    @Test
    fun `ユーザー名に使えない文字があれば落ちる`() {
        // URL のパスと acct の両方に入るので、区切り文字が混ざると別のものを指してしまう
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to "ad/min") }
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to "ad min") }
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to "admin@example.com") }
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to "管理者") }
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to ".admin") }
    }

    @Test
    fun `ユーザー名に使える文字は通る`() {
        assertEquals("feed_1", config("ACTOR_USERNAME" to "feed_1").actorUsername)
        assertEquals("feed.1", config("ACTOR_USERNAME" to "feed.1").actorUsername)
        assertEquals("feed-1", config("ACTOR_USERNAME" to "feed-1").actorUsername)
    }
}
