package net.matsudamper.mastodon.rss.actor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * アクター文書と WebFinger の読み取り。相手が書いてきたものをどこまで信じるかを確かめる。
 */
class HttpRemoteActorsTest {
    @Test
    fun `acct は WebFinger の subject で確定させる`() {
        val fetched = findActor(
            document = actorDocument(
                """
                "url": "https://remote.example/@alice",
                "preferredUsername": "alice",
                """.trimIndent(),
            ),
            webFinger = webFinger(subject = "acct:alice@example.com", selfHref = ACTOR_ID),
        )

        val actor = assertNotNull(fetched.actor)
        // アクターのホストと acct のホストが違う運用がある。WebFinger の言う方を出す
        assertEquals("@alice@example.com", actor.acct)
        assertEquals("https://remote.example/@alice", actor.profileUrl)
    }

    @Test
    fun `self がアクターを指さない WebFinger は信じない`() {
        val fetched = findActor(
            document = actorDocument(""""preferredUsername": "alice","""),
            webFinger = webFinger(
                subject = "acct:admin@example.com",
                selfHref = "https://remote.example/users/admin",
            ),
        )

        // 確かめずに信じると、同じホストの別のアカウントの名乗りを出せる
        val actor = assertNotNull(fetched.actor)
        assertNull(actor.acct)
    }

    @Test
    fun `WebFinger が引けない相手は acct を出さない`() {
        val fetched = findActor(
            document = actorDocument(""""preferredUsername": "alice","""),
            webFinger = null,
        )

        val actor = assertNotNull(fetched.actor)
        assertNull(actor.acct)
        assertEquals("$ACTOR_ID/inbox", actor.inbox)
    }

    @Test
    fun `名前として通らない preferredUsername では WebFinger を引かない`() {
        val fetched = findActor(
            document = actorDocument(""""preferredUsername": "alice@mastodon.social","""),
            webFinger = webFinger(subject = "acct:alice@example.com", selfHref = ACTOR_ID),
        )

        val actor = assertNotNull(fetched.actor)
        assertNull(actor.acct)
        assertFalse(fetched.webFingerRequested, "問い合わせる名前が決まらないのに引いている")
    }

    @Test
    fun `他所のホストを指すプロフィールの URL は落とす`() {
        val fetched = findActor(
            document = actorDocument(
                """
                "url": "https://phishing.example/@alice",
                "preferredUsername": "alice",
                """.trimIndent(),
            ),
            webFinger = webFinger(subject = "acct:alice@remote.example", selfHref = ACTOR_ID),
        )

        val actor = assertNotNull(fetched.actor)
        assertNull(actor.profileUrl)
        assertEquals("@alice@remote.example", actor.acct)
    }

    @Test
    fun `url がオブジェクトや配列でも鍵と inbox は読める`() {
        val fetched = findActor(
            document = actorDocument(""""url": [{ "type": "Link", "href": "https://remote.example/@alice" }],"""),
            webFinger = null,
        )

        // 型が違うだけで文書ごと読めなくなると、この相手の署名が検証できなくなる
        val actor = assertNotNull(fetched.actor)
        assertEquals("$ACTOR_ID/inbox", actor.inbox)
        assertEquals("pem", actor.publicKeyPem)
        assertNull(actor.profileUrl)
    }

    @Test
    fun `プロフィールの URL も名前も無い相手は null で返る`() {
        val fetched = findActor(document = actorDocument(""), webFinger = null)

        val actor = assertNotNull(fetched.actor)
        assertNull(actor.profileUrl)
        assertNull(actor.acct)
    }

    @Test
    fun `既定でないポートの相手にはポートまで含めて問い合わせる`() {
        val actorId = "https://remote.example:8443/users/alice"
        val fetched = findActor(
            actorId = actorId,
            document = """
            {
              "id": "$actorId",
              "inbox": "$actorId/inbox",
              "preferredUsername": "alice",
              "publicKey": { "publicKeyPem": "pem" }
            }
            """.trimIndent(),
            webFinger = webFinger(subject = "acct:alice@remote.example:8443", selfHref = actorId),
        )

        // ポートを落とすと別の接続先の WebFinger を引くことになる
        assertEquals(8443, fetched.webFingerRequestPort)
        assertEquals("acct:alice@remote.example:8443", fetched.webFingerResource)
        assertEquals("@alice@remote.example:8443", assertNotNull(fetched.actor).acct)
    }

    @Test
    fun `WebFinger には JRD の media type で問い合わせる`() {
        val fetched = findActor(
            document = actorDocument(""""preferredUsername": "alice","""),
            webFinger = webFinger(subject = "acct:alice@remote.example", selfHref = ACTOR_ID),
        )

        // JRD だけを返すサーバーは application/json だけの Accept に 406 を返す
        val accept = assertNotNull(fetched.webFingerAccept)
        assertTrue(accept.contains("application/jrd+json"), "Accept: $accept")
        assertEquals("@alice@remote.example", assertNotNull(fetched.actor).acct)
    }

    @Test
    fun `WebFinger が別のポートへリダイレクトしたら信じない`() {
        val actorId = "https://remote.example:8443/users/alice"
        val client = HttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath != "/.well-known/webfinger" ->
                        respondJson(
                            """
                            {
                              "id": "$actorId",
                              "inbox": "$actorId/inbox",
                              "preferredUsername": "alice",
                              "publicKey": { "publicKeyPem": "pem" }
                            }
                            """.trimIndent(),
                        )

                    // 8443 で始めた問い合わせが 443 に移る。別の接続先が返した subject になる
                    request.url.port == 8443 -> respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(
                            HttpHeaders.Location,
                            "https://remote.example/.well-known/webfinger?resource=acct:alice@remote.example:8443",
                        ),
                    )

                    else -> respondJson(webFinger(subject = "acct:alice@remote.example", selfHref = actorId))
                }
            },
        )

        val actor = HttpRemoteActors(client = client).use { runBlocking { it.findActor(actorId) } }

        assertNull(assertNotNull(actor).acct)
    }

    private fun actorDocument(extraFields: String): String =
        """
        {
          "id": "$ACTOR_ID",
          "inbox": "$ACTOR_ID/inbox",
          $extraFields
          "publicKey": { "publicKeyPem": "pem" }
        }
        """.trimIndent()

    private fun webFinger(
        subject: String,
        selfHref: String,
    ): String =
        """
        {
          "subject": "$subject",
          "links": [
            { "rel": "self", "type": "application/activity+json", "href": "$selfHref" }
          ]
        }
        """.trimIndent()

    /**
     * @param webFinger null なら WebFinger を返さないサーバーにする
     */
    private fun findActor(
        document: String,
        webFinger: String?,
        actorId: String = ACTOR_ID,
    ): FetchResult {
        var webFingerRequested = false
        var webFingerRequestPort: Int? = null
        var webFingerResource: String? = null
        var webFingerAccept: String? = null

        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/.well-known/webfinger") {
                    webFingerRequested = true
                    webFingerRequestPort = request.url.port
                    webFingerResource = request.url.parameters["resource"]
                    webFingerAccept = request.headers[HttpHeaders.Accept]
                    if (webFinger == null) {
                        respondError(HttpStatusCode.NotFound)
                    } else {
                        respondJson(webFinger)
                    }
                } else {
                    respondJson(document)
                }
            },
        )

        val actor = HttpRemoteActors(client = client).use { runBlocking { it.findActor(actorId) } }

        return FetchResult(
            actor = actor,
            webFingerRequested = webFingerRequested,
            webFingerRequestPort = webFingerRequestPort,
            webFingerResource = webFingerResource,
            webFingerAccept = webFingerAccept,
        )
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/activity+json"),
    )

    private data class FetchResult(
        val actor: RemoteActor?,
        val webFingerRequested: Boolean,
        val webFingerRequestPort: Int?,
        val webFingerResource: String?,
        val webFingerAccept: String?,
    )

    private companion object {
        const val ACTOR_ID = "https://remote.example/users/alice"
    }
}
