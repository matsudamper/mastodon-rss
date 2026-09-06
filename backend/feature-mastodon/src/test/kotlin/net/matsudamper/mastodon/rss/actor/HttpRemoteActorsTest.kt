package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * アクター文書の読み取り。相手が書いてきたものをどこまで信じるかを確かめる。
 */
class HttpRemoteActorsTest {
    @Test
    fun `プロフィールの URL と名前を読む`() {
        val actor = findActor(
            """
            {
              "id": "$ACTOR_ID",
              "inbox": "$ACTOR_ID/inbox",
              "url": "https://remote.example/@alice",
              "preferredUsername": "alice",
              "publicKey": { "publicKeyPem": "pem" }
            }
            """.trimIndent(),
        )

        assertNotNull(actor)
        assertEquals("https://remote.example/@alice", actor.profileUrl)
        assertEquals("alice", actor.preferredUsername)
    }

    @Test
    fun `他所のホストを指すプロフィールの URL は落とす`() {
        val actor = findActor(
            """
            {
              "id": "$ACTOR_ID",
              "inbox": "$ACTOR_ID/inbox",
              "url": "https://phishing.example/@alice",
              "preferredUsername": "alice",
              "publicKey": { "publicKeyPem": "pem" }
            }
            """.trimIndent(),
        )

        assertNotNull(actor)
        assertNull(actor.profileUrl)
        assertEquals("alice", actor.preferredUsername)
    }

    @Test
    fun `プロフィールの URL も名前も無い相手は null で返る`() {
        val actor = findActor(
            """
            {
              "id": "$ACTOR_ID",
              "inbox": "$ACTOR_ID/inbox",
              "publicKey": { "publicKeyPem": "pem" }
            }
            """.trimIndent(),
        )

        assertNotNull(actor)
        assertNull(actor.profileUrl)
        assertNull(actor.preferredUsername)
    }

    private fun findActor(document: String): RemoteActor? {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = document,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/activity+json"),
                )
            },
        )

        return HttpRemoteActors(client = client).use { runBlocking { it.findActor(ACTOR_ID) } }
    }

    private companion object {
        const val ACTOR_ID = "https://remote.example/users/alice"
    }
}
