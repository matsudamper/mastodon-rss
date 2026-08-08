package net.matsudamper.mastodon.rss.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 管理画面のログインに使うハッシュ。環境変数に入れて渡す前提なので、
// 1 行に畳めることと、その 1 行から検証まで戻せることを確かめる。
// 反復回数はテストでは小さくする。既定値のまま何度も回すとテストが遅くなる。
class PasswordHashTest {
    private val iterations = 1_000

    @Test
    fun `作ったハッシュは元のパスワードで一致する`() {
        val hash = PasswordHash.create("correct horse battery", iterations)

        assertTrue(hash.matches("correct horse battery"))
    }

    @Test
    fun `違うパスワードでは一致しない`() {
        val hash = PasswordHash.create("correct horse battery", iterations)

        assertFalse(hash.matches("correct horse batterz"))
        assertFalse(hash.matches(""))
    }

    @Test
    fun `文字列にしてから読み直しても検証できる`() {
        val encoded = PasswordHash.create("correct horse battery", iterations).encode()

        val parsed = PasswordHash.parse(encoded)

        assertTrue(parsed.matches("correct horse battery"))
        assertFalse(parsed.matches("違うパスワード"))
    }

    @Test
    fun `同じパスワードでも毎回違う文字列になる`() {
        val first = PasswordHash.create("correct horse battery", iterations).encode()
        val second = PasswordHash.create("correct horse battery", iterations).encode()

        // salt が毎回変わるので、同じ値になったら salt を引き直していない
        assertTrue(first != second)
    }

    @Test
    fun `環境変数に貼れる形になっている`() {
        val encoded = PasswordHash.create("correct horse battery", iterations).encode()

        val parts = encoded.split(":")
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertEquals(iterations.toString(), parts[1])
        // URL-safe Base64。引用が要る文字が混ざっていたら貼り付けで壊れる
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(parts[2]), "salt: ${parts[2]}")
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(parts[3]), "hash: ${parts[3]}")
    }

    @Test
    fun `前後の空白は無視して読む`() {
        val encoded = PasswordHash.create("correct horse battery", iterations).encode()

        assertTrue(PasswordHash.parse("  $encoded\n").matches("correct horse battery"))
    }

    @Test
    fun `マルチバイトのパスワードも扱える`() {
        val hash = PasswordHash.create("パスワードは日本語でも良い", iterations)

        assertTrue(hash.matches("パスワードは日本語でも良い"))
        assertFalse(hash.matches("パスワードは日本語でも良"))
    }

    @Test
    fun `壊れた文字列は読めた振りをせず失敗する`() {
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("") }
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("pbkdf2-sha256:1000:c2FsdA") }
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("bcrypt:1000:c2FsdA:aGFzaA") }
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("pbkdf2-sha256:ゼロ:c2FsdA:aGFzaA") }
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("pbkdf2-sha256:0:c2FsdA:aGFzaA") }
        assertFailsWith<IllegalArgumentException> { PasswordHash.parse("pbkdf2-sha256:1000:*****:aGFzaA") }
    }

    @Test
    fun `空のパスワードでは作れない`() {
        assertFailsWith<IllegalArgumentException> { PasswordHash.create("", iterations) }
    }

    @Test
    fun `反復回数が違えば一致しない`() {
        val encoded = PasswordHash.create("correct horse battery", iterations).encode()
        val tampered = encoded.replaceFirst(":$iterations:", ":${iterations + 1}:")

        assertFalse(PasswordHash.parse(tampered).matches("correct horse battery"))
    }

    @Test
    fun `パスワードを toString に出さない`() {
        val hash = PasswordHash.create("correct horse battery", iterations)

        assertFalse(hash.toString().contains("correct horse battery"))
    }
}
