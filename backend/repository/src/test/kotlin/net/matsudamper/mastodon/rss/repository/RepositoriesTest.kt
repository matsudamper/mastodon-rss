package net.matsudamper.mastodon.rss.repository

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

// 公開 API から見た振る舞いを確認する。
// 接続できること、DB ファイルと親ディレクトリが作られること、
// 開き直しても内容が残っていること。
class RepositoriesTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-repository-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `DBに書き込んで読み戻せる`() {
        val config = DatabaseConfig(path = tempDir.resolve("test.db"))

        createRepositories(config).use { repositories ->
            repositories.verifyWritable()
        }
    }

    @Test
    fun `親ディレクトリが無ければ作られる`() {
        val dbPath = tempDir.resolve("nested/dir/test.db")

        createRepositories(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.verifyWritable()
        }

        assertTrue(Files.exists(dbPath), "DB ファイルが作られていない: $dbPath")
    }

    @Test
    fun `閉じたあとに開き直しても内容が残っている`() {
        val config = DatabaseConfig(path = tempDir.resolve("test.db"))

        createRepositories(config).use { it.verifyWritable() }
        createRepositories(config).use { it.verifyWritable() }
    }
}
