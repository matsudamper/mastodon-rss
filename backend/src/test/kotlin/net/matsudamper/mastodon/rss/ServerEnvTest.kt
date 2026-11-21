package net.matsudamper.mastodon.rss

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kotpub.actor.ActorPrivateKeySource
import net.matsudamper.mastodon.rss.crypto.PasswordHash

// 環境変数の読み取りを確認する。読むのはここ 1 か所だけなので、
// 変数の一覧としてもこのテストを見れば分かるようにしておく。
class ServerEnvTest {
    private fun env(vararg values: Pair<String, String>): ServerEnv = TestServerEnv.of(*values)

    private fun envWithout(name: String): ServerEnv = ServerEnv(TestServerEnv.REQUIRED - name)

    @Test
    fun `環境変数の値を使う`() {
        val env =
            env(
                "HOST" to "127.0.0.1",
                "PORT" to "9000",
                "DB_PATH" to "/data/rss.db",
                "ICON_CACHE_DIR" to "/data/icons",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
                "STATIC_SRC_DIR" to "/srv/static",
            )

        assertEquals("127.0.0.1", env.host)
        assertEquals(9000, env.port)
        assertEquals(Path.of("/data/rss.db"), env.dbPath)
        assertEquals(Path.of("/data/icons"), env.iconCacheDir)
        assertEquals(ActorPrivateKeySource.File(Path.of("/data/actor.pem")), env.actorPrivateKey)
        assertEquals(Path.of("/srv/static"), env.staticSrcDir)
    }

    @Test
    fun `必須の変数が無ければ落ちる`() {
        listOf("HOST", "PORT", "DB_PATH", "ICON_CACHE_DIR", "ADMIN_COOKIE_SECURE").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { envWithout(name) }
            assertFailsWith<IllegalArgumentException>(name) { env(name to "  ") }
        }
    }

    @Test
    fun `数値でない PORT は落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("PORT" to "ポート") }
    }

    @Test
    fun `STATIC_SRC_DIR は未設定でも起動する`() {
        assertNull(env().staticSrcDir)
        assertNull(env("STATIC_SRC_DIR" to "  ").staticSrcDir)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String = env("DOMAIN" to raw).domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { envWithout("DOMAIN") }
        assertFailsWith<IllegalArgumentException> { env("DOMAIN" to "   ") }
        assertFailsWith<IllegalArgumentException> { env("DOMAIN" to "https://") }
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(TestServerEnv.REQUIRED - "TRUST_CF_CONNECTING_IP") }
        assertFailsWith<IllegalArgumentException> { env("TRUST_CF_CONNECTING_IP" to " ") }
        assertFailsWith<IllegalArgumentException> { env("TRUST_CF_CONNECTING_IP" to "yes") }
    }

    @Test
    fun `鍵の PEM を直接指定できる`() {
        val env = ServerEnv(TestServerEnv.REQUIRED - "ACTOR_PRIVATE_KEY_PATH" + ("ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----"))

        assertEquals(ActorPrivateKeySource.Pem("-----BEGIN PRIVATE KEY-----"), env.actorPrivateKey)
    }

    @Test
    fun `鍵の PEM もパスも無ければ落ちる`() {
        assertFailsWith<IllegalArgumentException> { envWithout("ACTOR_PRIVATE_KEY_PATH") }
        assertFailsWith<IllegalArgumentException> { env("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "") }
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
    fun `Cookie の Secure を付けられる`() {
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
