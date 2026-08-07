package dev.matsudamper.mastodonrss.actor

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 鍵の取得元を環境変数から決める部分を確認する。
// 取り違えたまま起動すると相手側の署名検証が通らなくなるので、
// 曖昧な指定は落とすことまで見る。
class ActorKeyConfigTest {
    private fun config(vararg env: Pair<String, String>): ActorKeyConfig {
        val map = env.toMap()
        return ActorKeyConfig.from { map[it] }
    }

    @Test
    fun `未設定なら既定のパスから読む`() {
        val config = config()

        assertEquals(ActorKeyConfig.File(Path.of(ActorKeyConfig.DEFAULT_PRIVATE_KEY_PATH)), config)
    }

    @Test
    fun `パスを指定できる`() {
        val config = config("ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem")

        assertEquals(ActorKeyConfig.File(Path.of("/data/actor.pem")), config)
    }

    @Test
    fun `PEM を直接指定できる`() {
        val config = config("ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----")

        assertEquals(ActorKeyConfig.Pem("-----BEGIN PRIVATE KEY-----"), config)
    }

    @Test
    fun `空文字は未設定として扱う`() {
        val config = config("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "")

        assertEquals(ActorKeyConfig.File(Path.of(ActorKeyConfig.DEFAULT_PRIVATE_KEY_PATH)), config)
    }

    @Test
    fun `PEM とパスの同時指定は落とす`() {
        assertFailsWith<IllegalArgumentException> {
            config(
                "ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
            )
        }
    }
}
