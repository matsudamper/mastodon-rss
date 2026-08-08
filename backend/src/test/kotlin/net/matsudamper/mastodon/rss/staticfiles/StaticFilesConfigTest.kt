package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaticFilesConfigTest {
    @Test
    fun `STATIC_SRC_DIRを指定するとそのディレクトリになる`() {
        val config =
            StaticFilesConfig.from { name ->
                if (name == StaticFilesConfig.ENV_STATIC_SRC_DIR) "/srv/static" else null
            }

        assertEquals(Path.of("/srv/static"), config.srcDir)
    }

    @Test
    fun `STATIC_SRC_DIRが未設定なら配信元を持たない`() {
        val config = StaticFilesConfig.from { null }

        assertNull(config.srcDir)
    }

    @Test
    fun `STATIC_SRC_DIRが空白だけなら未設定として扱う`() {
        val config = StaticFilesConfig.from { "   " }

        assertNull(config.srcDir)
    }

    @Test
    fun `STATIC_SRC_DIRの前後の空白は落とす`() {
        val config = StaticFilesConfig.from { "  /srv/static  " }

        assertEquals(Path.of("/srv/static"), config.srcDir)
    }
}
