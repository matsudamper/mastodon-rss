package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActorUsernameTest {
    @Test
    fun `英数字と記号で組み立てた名前は使える`() {
        assertTrue(ActorUsername.isValid("admin"))
        assertTrue(ActorUsername.isValid("feed-1"))
        assertTrue(ActorUsername.isValid("feed.1_2"))
        assertTrue(ActorUsername.isValid("a"))
    }

    @Test
    fun `空と長すぎる名前は使えない`() {
        assertFalse(ActorUsername.isValid(""))
        assertTrue(ActorUsername.isValid("a".repeat(ActorUsername.MAX_LENGTH)))
        assertFalse(ActorUsername.isValid("a".repeat(ActorUsername.MAX_LENGTH + 1)))
    }

    @Test
    fun `使えない文字を重複なく返す`() {
        assertEquals(listOf(' ', '/', 'あ'), ActorUsername.unusableCharacters("feed 1/あ/あ"))
        assertEquals(emptyList(), ActorUsername.unusableCharacters("feed-1"))
    }

    @Test
    fun `間には置ける文字でも先頭と末尾なら使えない文字として返る`() {
        assertEquals(listOf('-'), ActorUsername.unusableCharacters("feed-"))
        assertEquals(listOf('.'), ActorUsername.unusableCharacters(".feed"))
        // 1 文字の名前は先頭であり末尾でもある
        assertEquals(listOf('-'), ActorUsername.unusableCharacters("-"))
    }
}
