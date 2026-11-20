package net.matsudamper.mastodon.rss.webpage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kotpub.FakeNoteStore
import net.matsudamper.kotpub.TestLocalActor
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.StoredNote

class PageOgpServiceTest {
    private val notes = FakeNoteStore()
    private val profiles = object : StoredActorProfiles {
        override fun find(username: String): ActorProfile =
            ActorProfile(displayName = "フィード名", summary = "R&amp;D <b>説明</b>\n補足", likedPublic = false)
    }
    private val appearances = object : StoredActorAppearances {
        override fun find(username: String): ActorAppearance = ActorAppearance(links = listOf(), iconVersion = "v1", headerVersion = "h1")
    }
    private val service = PageOgpService(
        directory = TestLocalActor.directory,
        profiles = profiles,
        appearances = appearances,
        notes = notes,
    )
    private val username = TestLocalActor.USERNAME
    private val domain = TestLocalActor.DOMAIN

    @Test
    fun `アカウントは表示名とアイコンを出し、説明は平文のまま出す`() {
        val ogp = service.account(username)

        assertEquals(PageOgpType.Profile, ogp?.type)
        assertEquals("フィード名 (@$username@$domain)", ogp?.title)
        assertEquals("R&amp;D <b>説明</b> 補足", ogp?.description)
        assertEquals("https://$domain/@$username", ogp?.url)
        assertEquals(TestLocalActor.urls.icon("v1"), ogp?.imageUrl)
    }

    @Test
    fun `無いアカウントは出さない`() {
        assertNull(service.account("missing"))
    }

    @Test
    fun `投稿は本文を出し、画像はアイコンだけにする`() {
        notes.add(note(publicId = "n1", contentHtml = "<p>記事のタイトル</p><p><a href=\"https://example.org/a\">https://example.org/a</a></p>"))

        val ogp = service.note(username = username, publicId = "n1")

        assertEquals(PageOgpType.Article, ogp?.type)
        assertEquals("記事のタイトル https://example.org/a", ogp?.description)
        assertEquals("https://$domain/@$username/n1", ogp?.url)
        assertEquals(TestLocalActor.urls.icon("v1"), ogp?.imageUrl)
    }

    @Test
    fun `別のアカウントのパスで開いた投稿は出さない`() {
        notes.add(note(publicId = "n1", contentHtml = "<p>本文</p>"))

        assertNull(service.note(username = "other", publicId = "n1"))
    }

    private fun note(
        publicId: String,
        contentHtml: String,
    ): StoredNote = StoredNote(
        publicId = PublicNoteId(publicId),
        username = username,
        contentHtml = contentHtml,
        summary = null,
        publishedAt = Instant.EPOCH,
        updatedAt = null,
        attachments = listOf(),
        inReplyTo = null,
        quote = null,
        mentions = listOf(),
        emojis = listOf(),
        sensitive = false,
        poll = null,
    )
}
