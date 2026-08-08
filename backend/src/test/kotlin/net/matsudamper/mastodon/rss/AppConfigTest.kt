package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorKeyConfig
import net.matsudamper.mastodon.rss.staticfiles.StaticFilesConfig
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// 環境変数の読み取りを確認する。読むのはここ 1 か所だけなので、
// 変数と既定値の一覧としてもこのテストを見れば分かるようにしておく。
class AppConfigTest {
    private fun config(vararg env: Pair<String, String>): AppConfig {
        val map = env.toMap() + ("DOMAIN" to "example.com")
        return AppConfig.from { map[it] }
    }

    @Test
    fun `DOMAIN 以外は未設定なら既定値になる`() {
        val config = config()

        assertEquals(AppConfig.DEFAULT_HOST, config.server.host)
        assertEquals(AppConfig.DEFAULT_PORT, config.server.port)
        assertEquals(AppConfig.DEFAULT_ACTOR_USERNAME, config.server.actorUsername)
        assertEquals(Path.of(AppConfig.DEFAULT_DB_PATH), config.database.path)
        assertEquals(ActorKeyConfig.File(Path.of(AppConfig.DEFAULT_ACTOR_PRIVATE_KEY_PATH)), config.actorKey)
        assertNull(config.staticFiles.srcDir)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val config =
            config(
                "HOST" to "127.0.0.1",
                "PORT" to "9000",
                "ACTOR_USERNAME" to "feed1",
                "DB_PATH" to "/data/rss.db",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
                "STATIC_SRC_DIR" to "/srv/static",
            )

        assertEquals("127.0.0.1", config.server.host)
        assertEquals(9000, config.server.port)
        assertEquals("feed1", config.server.actorUsername)
        assertEquals(Path.of("/data/rss.db"), config.database.path)
        assertEquals(ActorKeyConfig.File(Path.of("/data/actor.pem")), config.actorKey)
        assertEquals(StaticFilesConfig(Path.of("/srv/static")), config.staticFiles)
    }

    @Test
    fun `数値でない PORT は既定値に落ちる`() {
        assertEquals(AppConfig.DEFAULT_PORT, config("PORT" to "ポート").server.port)
    }

    @Test
    fun `空文字と空白だけの指定は未設定として扱う`() {
        val config =
            config(
                "HOST" to "",
                "PORT" to "",
                "ACTOR_USERNAME" to "   ",
                "DB_PATH" to " ",
                "STATIC_SRC_DIR" to "  ",
            )

        assertEquals(AppConfig.DEFAULT_HOST, config.server.host)
        assertEquals(AppConfig.DEFAULT_PORT, config.server.port)
        assertEquals(AppConfig.DEFAULT_ACTOR_USERNAME, config.server.actorUsername)
        assertEquals(Path.of(AppConfig.DEFAULT_DB_PATH), config.database.path)
        assertNull(config.staticFiles.srcDir)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String = AppConfig.from { if (it == "DOMAIN") raw else null }.server.domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.from { null } }
        assertFailsWith<IllegalArgumentException> { AppConfig.from { "   " } }
        assertFailsWith<IllegalArgumentException> { AppConfig.from { "https://" } }
    }

    @Test
    fun `使えないユーザー名なら落ちる`() {
        // URL のパスと acct の両方に入るので、区切り文字が混ざると別のものを指してしまう
        assertFailsWith<IllegalArgumentException> { config("ACTOR_USERNAME" to "ad/min") }
    }

    @Test
    fun `鍵の PEM を直接指定できる`() {
        val config = config("ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----")

        assertEquals(ActorKeyConfig.Pem("-----BEGIN PRIVATE KEY-----"), config.actorKey)
    }

    @Test
    fun `鍵の指定が空白だけなら未設定として扱う`() {
        val config = config("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "")

        assertEquals(ActorKeyConfig.File(Path.of(AppConfig.DEFAULT_ACTOR_PRIVATE_KEY_PATH)), config.actorKey)
    }

    // 片方を黙って無視すると、意図していない鍵で起動したことに気付けない
    @Test
    fun `鍵の PEM とパスの同時指定は落とす`() {
        assertFailsWith<IllegalArgumentException> {
            config(
                "ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
            )
        }
    }
}
