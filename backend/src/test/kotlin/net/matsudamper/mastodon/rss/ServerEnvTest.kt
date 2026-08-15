package net.matsudamper.mastodon.rss

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.actor.ActorPrivateKey
import net.matsudamper.mastodon.rss.crypto.PasswordHash

// 環境変数の読み取りを確認する。読むのはここ 1 か所だけなので、
// 変数と既定値の一覧としてもこのテストを見れば分かるようにしておく。
class ServerEnvTest {
    private fun env(vararg values: Pair<String, String>): ServerEnv =
        ServerEnv(values.toMap() + ("DOMAIN" to "example.com"))

    @Test
    fun `DOMAIN 以外は未設定なら既定値になる`() {
        val env = env()

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertEquals("admin", env.actorUsername)
        assertEquals(Path.of("./data/mastodon-rss.db"), env.dbPath)
        assertEquals(ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
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
        assertEquals(ActorPrivateKey.File(Path.of("/data/actor.pem")), env.actorPrivateKey)
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
        fun domain(raw: String): String = ServerEnv(mapOf("DOMAIN" to raw)).domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("DOMAIN" to "   ")) }
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("DOMAIN" to "https://")) }
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

        assertEquals(ActorPrivateKey.Pem("-----BEGIN PRIVATE KEY-----"), env.actorPrivateKey)
    }

    @Test
    fun `鍵の指定が空白だけなら未設定として扱う`() {
        val env = env("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "")

        assertEquals(ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
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

    @Test
    fun `パスワードハッシュは未設定でも起動する`() {
        assertNull(env().adminPasswordHash)
        assertNull(env("ADMIN_PASSWORD_HASH" to "  ").adminPasswordHash)
    }

    @Test
    fun `パスワードハッシュを読める`() {
        val encoded = PasswordHash.create("とても長いパスワード", iterations = 1_000).encode()

        val hash = assertNotNull(env("ADMIN_PASSWORD_HASH" to encoded).adminPasswordHash)

        assertTrue(hash.matches("とても長いパスワード"))
    }

    @Test
    fun `パスワードハッシュの形式が違えば落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("ADMIN_PASSWORD_HASH" to "パスワード") }
    }

    @Test
    fun `Cookie の Secure は既定で付ける`() {
        assertTrue(env().adminCookieSecure)
        assertTrue(env("ADMIN_COOKIE_SECURE" to " ").adminCookieSecure)
        assertTrue(env("ADMIN_COOKIE_SECURE" to "TRUE").adminCookieSecure)
    }

    @Test
    fun `Cookie の Secure を外せる`() {
        assertFalse(env("ADMIN_COOKIE_SECURE" to "false").adminCookieSecure)
        assertFalse(env("ADMIN_COOKIE_SECURE" to "False").adminCookieSecure)
    }

    @Test
    fun `Cookie の Secure が true でも false でもなければ落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("ADMIN_COOKIE_SECURE" to "no") }
    }
}
