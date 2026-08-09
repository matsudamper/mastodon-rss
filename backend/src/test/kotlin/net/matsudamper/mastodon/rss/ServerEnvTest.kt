package net.matsudamper.mastodon.rss

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// 環境変数の読み取りを確認する。読むのはここ 1 か所だけなので、
// 変数と既定値の一覧としてもこのテストを見れば分かるようにしておく。
class ServerEnvTest {
    private fun env(vararg values: Pair<String, String>): ServerEnv {
        val map = values.toMap() + ("DOMAIN" to "example.com")
        return ServerEnv.from { map[it] }
    }

    @Test
    fun `DOMAIN 以外は未設定なら既定値になる`() {
        val env = env()

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertEquals("admin", env.actorUsername)
        assertEquals(Path.of("./data/mastodon-rss.db"), env.dbPath)
        assertEquals(ServerEnv.ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env =
            env(
                "HOST" to "127.0.0.1",
                "PORT" to "9000",
                "ACTOR_USERNAME" to "feed1",
                "DB_PATH" to "/data/rss.db",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
                "STATIC_SRC_DIR" to "/srv/static",
            )

        assertEquals("127.0.0.1", env.host)
        assertEquals(9000, env.port)
        assertEquals("feed1", env.actorUsername)
        assertEquals(Path.of("/data/rss.db"), env.dbPath)
        assertEquals(ServerEnv.ActorPrivateKey.File(Path.of("/data/actor.pem")), env.actorPrivateKey)
        assertEquals(Path.of("/srv/static"), env.staticSrcDir)
    }

    @Test
    fun `数値でない PORT は既定値に落ちる`() {
        assertEquals(8080, env("PORT" to "ポート").port)
    }

    @Test
    fun `空文字と空白だけの指定は未設定として扱う`() {
        val env =
            env(
                "HOST" to "",
                "PORT" to "",
                "ACTOR_USERNAME" to "   ",
                "DB_PATH" to " ",
                "STATIC_SRC_DIR" to "  ",
            )

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertEquals("admin", env.actorUsername)
        assertEquals(Path.of("./data/mastodon-rss.db"), env.dbPath)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String = ServerEnv.from { if (it == "DOMAIN") raw else null }.domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv.from { null } }
        assertFailsWith<IllegalArgumentException> { ServerEnv.from { "   " } }
        assertFailsWith<IllegalArgumentException> { ServerEnv.from { "https://" } }
    }

    // URL のパスと acct の両方に入るので、区切り文字が混ざると別のものを指してしまう
    @Test
    fun `ユーザー名に使えない文字があれば落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("ACTOR_USERNAME" to "ad/min") }
        assertFailsWith<IllegalArgumentException> { env("ACTOR_USERNAME" to "ad min") }
        assertFailsWith<IllegalArgumentException> { env("ACTOR_USERNAME" to "admin@example.com") }
        assertFailsWith<IllegalArgumentException> { env("ACTOR_USERNAME" to "管理者") }
        assertFailsWith<IllegalArgumentException> { env("ACTOR_USERNAME" to ".admin") }
    }

    @Test
    fun `ユーザー名に使える文字は通る`() {
        assertEquals("feed_1", env("ACTOR_USERNAME" to "feed_1").actorUsername)
        assertEquals("feed.1", env("ACTOR_USERNAME" to "feed.1").actorUsername)
        assertEquals("feed-1", env("ACTOR_USERNAME" to "feed-1").actorUsername)
    }

    @Test
    fun `鍵の PEM を直接指定できる`() {
        val env = env("ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----")

        assertEquals(ServerEnv.ActorPrivateKey.Pem("-----BEGIN PRIVATE KEY-----"), env.actorPrivateKey)
    }

    @Test
    fun `鍵の指定が空白だけなら未設定として扱う`() {
        val env = env("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "")

        assertEquals(ServerEnv.ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
    }

    // 片方を黙って無視すると、意図していない鍵で起動したことに気付けない
    @Test
    fun `鍵の PEM とパスの同時指定は落とす`() {
        assertFailsWith<IllegalArgumentException> {
            env(
                "ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
            )
        }
    }
}
