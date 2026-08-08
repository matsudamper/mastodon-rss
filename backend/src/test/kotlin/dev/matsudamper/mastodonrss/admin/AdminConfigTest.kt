package dev.matsudamper.mastodonrss.admin

import dev.matsudamper.mastodonrss.crypto.PasswordHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// 管理画面の設定。ハッシュは未設定でも起動できないといけない。
// 最初の 1 回はハッシュを作るために起動するので、必須にすると作る手段が無くなる。
class AdminConfigTest {
    private fun config(vararg env: Pair<String, String>): AdminConfig {
        val map = env.toMap()
        return AdminConfig.from { map[it] }
    }

    @Test
    fun `ハッシュが無くても起動できる`() {
        val config = config()

        assertNull(config.passwordHash)
        assertFalse(config.loginConfigured)
    }

    @Test
    fun `空文字は未設定として扱う`() {
        // .env に ADMIN_PASSWORD_HASH= とだけ書かれている状態
        val config = config(AdminConfig.ENV_PASSWORD_HASH to "   ")

        assertFalse(config.loginConfigured)
    }

    @Test
    fun `ハッシュを設定するとログインできる状態になる`() {
        val encoded = PasswordHash.create("correct horse battery", iterations = 1_000).encode()

        val config = config(AdminConfig.ENV_PASSWORD_HASH to encoded)

        assertTrue(config.loginConfigured)
        assertTrue(config.passwordHash!!.matches("correct horse battery"))
    }

    @Test
    fun `読めないハッシュは起動時に落とす`() {
        // ログインが必ず失敗するだけの状態で起動すると、パスワードを間違えたのか
        // 設定を間違えたのかが運用側から区別できない
        val failure =
            assertFailsWith<IllegalArgumentException> {
                config(AdminConfig.ENV_PASSWORD_HASH to "壊れたハッシュ")
            }

        assertTrue(failure.message!!.contains(AdminConfig.ENV_PASSWORD_HASH))
    }

    @Test
    fun `未設定なら既定値になる`() {
        val config = config()

        assertEquals(AdminConfig.DEFAULT_SESSION_TTL, config.sessionTtl)
        assertEquals(AdminConfig.DEFAULT_COOKIE_SECURE, config.cookieSecure)
    }

    @Test
    fun `セッションの長さと Cookie の Secure を環境変数で変えられる`() {
        val config =
            config(
                AdminConfig.ENV_SESSION_TTL_MINUTES to "30",
                AdminConfig.ENV_COOKIE_SECURE to "FALSE",
            )

        assertEquals(30.minutes, config.sessionTtl)
        assertFalse(config.cookieSecure)
    }

    @Test
    fun `数値でないセッションの長さは落とす`() {
        // ここを既定値に落とすと、指定したつもりの長さと実際がずれたまま気付けない
        assertFailsWith<IllegalArgumentException> {
            config(AdminConfig.ENV_SESSION_TTL_MINUTES to "30分")
        }
    }

    @Test
    fun `真偽値でない Secure の指定は落とす`() {
        // yes と書いて false になると、Cookie が平文で飛ぶことに気付けない
        assertFailsWith<IllegalArgumentException> {
            config(AdminConfig.ENV_COOKIE_SECURE to "yes")
        }
    }

    @Test
    fun `セッションの長さは 0 以下にできない`() {
        assertFailsWith<IllegalArgumentException> {
            config(AdminConfig.ENV_SESSION_TTL_MINUTES to "0")
        }
    }
}
