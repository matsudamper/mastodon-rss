package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.matsudamper.mastodon.rss.FakeRepositories

class RepositoryActorProfilesTest {
    private val repositories = FakeRepositories()

    private val profiles = RepositoryActorProfiles(repositories.accounts)

    @Test
    fun `説明文が未設定ならフィードを配信するアカウントだと説明する`() {
        repositories.accounts.add(username = USERNAME, createdAt = NOW)

        assertEquals("RSS/Atom フィードを ActivityPub で配信するアカウント", profiles.find(USERNAME).summary)
    }

    @Test
    fun `説明文を設定していればそれを返す`() {
        val account = assertNotNull(repositories.accounts.add(username = USERNAME, createdAt = NOW))
        repositories.accounts.updateProfile(id = account.id, displayName = null, summary = "説明")

        assertEquals("説明", profiles.find(USERNAME).summary)
    }

    private companion object {
        const val USERNAME = "feed1"

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
