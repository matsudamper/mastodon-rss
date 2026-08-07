package dev.matsudamper.mastodonrss.repository.sqlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ビルド時に生成した index からマイグレーションを読めることを確認する。
// ここが壊れると native バイナリで SQL を読めなくなるが、
// 起動するまで気付けないので、テストで先に落とす。
class MigrationLoaderTest {
    @Test
    fun `indexからマイグレーションを読み込める`() {
        val migrations = MigrationLoader.load()

        assertTrue(migrations.isNotEmpty(), "マイグレーションが 1 件も読み込めていない")
        assertTrue(
            migrations.any { it.fileName == "V001__init.sql" },
            "V001__init.sql が読み込まれていない: ${migrations.map { it.fileName }}",
        )
    }

    @Test
    fun `バージョンの昇順に並んでいる`() {
        val versions = MigrationLoader.load().map { it.version }

        assertEquals(versions.sorted(), versions)
    }

    @Test
    fun `ファイル名からバージョンと名前を読み取る`() {
        val migration = MigrationLoader.load().first { it.fileName == "V001__init.sql" }

        // 先頭の 0 を落として数値として扱う
        assertEquals(1, migration.version)
        assertEquals("init", migration.name)
        assertTrue(migration.sql.isNotBlank(), "SQL が空")
    }

    @Test
    fun `同じ内容なら同じチェックサムになる`() {
        val first = MigrationLoader.load().first()
        val second = MigrationLoader.load().first()

        assertEquals(first.checksum, second.checksum)
        // SHA-256 の 16 進表現
        assertEquals(64, first.checksum.length)
    }
}
