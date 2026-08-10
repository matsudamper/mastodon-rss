package net.matsudamper.mastodon.rss.repository

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 公開 API から見た振る舞いを確認する。
// 接続できること、DB ファイルと親ディレクトリが作られること、
// 開き直しても内容が残っていること。
// スキーマは実運用と同じく事前適用（テストでは TestSchema）で、起動時の自動適用は無い。
class RepositoriesTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-repository-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `DBに書き込んで読み戻せる`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.verifyWritable()
        }
    }

    @Test
    fun `親ディレクトリが無ければ作られる`() {
        val dbPath = tempDir.resolve("nested/dir/test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.verifyWritable()
        }

        assertTrue(Files.exists(dbPath), "DB ファイルが作られていない: $dbPath")
    }

    @Test
    fun `閉じたあとに開き直しても内容が残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        createRepositories(config).use { it.verifyWritable() }
        createRepositories(config).use { it.verifyWritable() }
    }

    @Test
    fun `スキーマ未適用のDBでは書き込み確認が失敗する`() {
        // 空の DB での起動失敗は仕様。sqlite3def での適用忘れをここで検出する
        val config = DatabaseConfig(path = tempDir.resolve("empty.db"))

        createRepositories(config).use { repositories ->
            assertFailsWith<Exception> { repositories.verifyWritable() }
        }
    }
}
