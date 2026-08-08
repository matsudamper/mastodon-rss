package dev.matsudamper.mastodonrss.repository.sqlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// SQL を文単位に分割する処理を確認する。
// 文字列リテラルやコメントの中の ; で誤って切らないことが要点。
class SqlStatementSplitterTest {
    @Test
    fun `セミコロンで文に分割される`() {
        val statements = splitSqlStatements("CREATE TABLE a (id INTEGER); CREATE TABLE b (id INTEGER);")

        assertEquals(listOf("CREATE TABLE a (id INTEGER)", "CREATE TABLE b (id INTEGER)"), statements)
    }

    @Test
    fun `末尾のセミコロンが無くても最後の文を拾う`() {
        val statements = splitSqlStatements("CREATE TABLE a (id INTEGER)")

        assertEquals(listOf("CREATE TABLE a (id INTEGER)"), statements)
    }

    @Test
    fun `文字列リテラル内のセミコロンでは分割しない`() {
        val statements = splitSqlStatements("INSERT INTO a (v) VALUES ('x;y'); SELECT 1")

        assertEquals(listOf("INSERT INTO a (v) VALUES ('x;y')", "SELECT 1"), statements)
    }

    @Test
    fun `エスケープされた引用符を含む文字列を扱える`() {
        val statements = splitSqlStatements("INSERT INTO a (v) VALUES ('it''s; ok')")

        assertEquals(listOf("INSERT INTO a (v) VALUES ('it''s; ok')"), statements)
    }

    @Test
    fun `引用符で囲んだ識別子内のセミコロンでは分割しない`() {
        val statements = splitSqlStatements("""CREATE TABLE "a;b" (id INTEGER)""")

        assertEquals(listOf("""CREATE TABLE "a;b" (id INTEGER)"""), statements)
    }

    @Test
    fun `行コメント内のセミコロンでは分割しない`() {
        val sql =
            """
            -- コメント; ここでは切らない
            CREATE TABLE a (id INTEGER);
            """.trimIndent()

        assertEquals(listOf("CREATE TABLE a (id INTEGER)"), splitSqlStatements(sql))
    }

    @Test
    fun `ブロックコメント内のセミコロンでは分割しない`() {
        val statements = splitSqlStatements("/* コメント; */ CREATE TABLE a (id INTEGER);")

        assertEquals(listOf("CREATE TABLE a (id INTEGER)"), statements)
    }

    @Test
    fun `空文は無視される`() {
        val statements = splitSqlStatements(";;SELECT 1;;")

        assertEquals(listOf("SELECT 1"), statements)
    }

    @Test
    fun `引用符が閉じられていなければ例外になる`() {
        assertFailsWith<IllegalArgumentException> {
            splitSqlStatements("INSERT INTO a (v) VALUES ('閉じていない)")
        }
    }

    @Test
    fun `ブロックコメントが閉じられていなければ例外になる`() {
        assertFailsWith<IllegalArgumentException> {
            splitSqlStatements("/* 閉じていない SELECT 1")
        }
    }
}
