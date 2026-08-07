package dev.matsudamper.mastodonrss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 環境変数の読み取りを確認する。
// ドメインはアクター ID に焼き込まれて後から変えられないので、
// 表記ゆれをここで吸収しておく。
class ServerConfigTest {
    private fun config(vararg env: Pair<String, String>): ServerConfig {
        val map = env.toMap()
        return ServerConfig.from { map[it] }
    }

    @Test
    fun `未設定なら既定値になる`() {
        val config = config()

        assertEquals(ServerConfig.DEFAULT_HOST, config.host)
        assertEquals(ServerConfig.DEFAULT_PORT, config.port)
        assertNull(config.domain)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val config = config(
            "HOST" to "127.0.0.1",
            "PORT" to "9000",
            "DOMAIN" to "example.com",
        )

        assertEquals("127.0.0.1", config.host)
        assertEquals(9000, config.port)
        assertEquals("example.com", config.domain)
    }

    @Test
    fun `数値でない PORT は既定値に落ちる`() {
        assertEquals(ServerConfig.DEFAULT_PORT, config("PORT" to "ポート").port)
    }

    @Test
    fun `空文字は未設定として扱う`() {
        val config = config("HOST" to "", "PORT" to "", "DOMAIN" to "   ")

        assertEquals(ServerConfig.DEFAULT_HOST, config.host)
        assertEquals(ServerConfig.DEFAULT_PORT, config.port)
        assertNull(config.domain)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        assertEquals("example.com", config("DOMAIN" to "https://example.com").domain)
        assertEquals("example.com", config("DOMAIN" to "http://example.com/").domain)
        assertEquals("example.com", config("DOMAIN" to " example.com/ ").domain)
    }
}
