package net.matsudamper.mastodon.rss.account

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH
import net.matsudamper.mastodon.rss.testDependencies

// アカウント画面が引く口。ログインが要らないので、管理画面のものとは別に見る。
class AccountGraphQlTest {
    @Test
    fun `保存されているアカウントは引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            val account = queryAccount(TestServerEnv.USERNAME).account()

            assertTrue(account.long("id") > 0)
            assertEquals(TestServerEnv.USERNAME, account.string("username"))
            assertEquals("@${TestServerEnv.USERNAME}@${TestServerEnv.DOMAIN}", account.string("acct"))
            assertEquals(
                "https://${TestServerEnv.DOMAIN}/users/${TestServerEnv.USERNAME}",
                account.string("actorUrl"),
            )
        }

    @Test
    fun `追加したアカウントは引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            assertEquals("feed1", queryAccount("feed1").account().string("username"))
        }

    @Test
    fun `無い名前は null になる`() =
        testApplication {
            application { module(testDependencies()) }

            val response = queryAccount("feed1")

            assertEquals(JsonNull, response.body().obj("data").getValue("account"))
            // 名前を間違えただけなので、引けなかった状態と混ぜない
            assertFalse(response.body().containsKey("errors"))
        }

    @Test
    fun `大文字小文字が違っても保存されている綴りが返る`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            // 綴りをそのまま返すと Feed1 と feed1 で別のアクター ID になる
            assertEquals("feed1", queryAccount("FEED1").account().string("username"))
        }

    @Test
    fun `アクターとして応答しない名前は null になる`() =
        testApplication {
            application { module(testDependencies()) }

            // ユーザー名として通らない文字が入っていても、エラーではなく存在しない扱い
            assertEquals(JsonNull, queryAccount("feed 1/あ").body().obj("data").getValue("account"))
        }

    @Test
    fun `公開アカウントの一覧がページングで引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            repositories.accounts.add(username = "feed2", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            val page1 = queryAccounts(limit = 2).accounts()
            val page1Nodes = page1.nodes()
            assertEquals(2, page1Nodes.size)
            assertTrue(page1Nodes[0].long("id") > 0)
            assertEquals("feed1", page1Nodes[0].string("username"))
            assertEquals("@feed1@${TestServerEnv.DOMAIN}", page1Nodes[0].string("acct"))
            assertEquals("https://${TestServerEnv.DOMAIN}/users/feed1", page1Nodes[0].string("actorUrl"))
            assertEquals("feed2", page1Nodes[1].string("username"))
            assertEquals(false, page1.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `1 件ずつでも追加した順に辿れる`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            repositories.accounts.add(username = "feed2", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            val page1 = queryAccounts(limit = 1).accounts()
            assertEquals(listOf("feed1"), page1.nodes().map { it.string("username") })
            assertEquals(true, page1.pageInfo().boolean("hasMore"))

            val page2 = queryAccounts(cursor = page1.pageInfo().string("nextCursor"), limit = 1).accounts()
            assertEquals(listOf("feed2"), page2.nodes().map { it.string("username") })
            assertEquals(false, page2.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `limit が上限を超えていても一覧が返る`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            val page = queryAccounts(limit = Int.MAX_VALUE).accounts()

            assertEquals(
                listOf("feed1"),
                page.nodes().map { it.string("username") },
            )
            assertEquals(false, page.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `配信した投稿はログインなしで引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
            val publishedAt = Instant.parse("2026-08-09T11:02:00Z")
            repositories.notes.add(
                NewNote(
                    username = TestServerEnv.USERNAME,
                    publicId = "abc123",
                    contentHtml = "<p>本文</p>",
                    publishedAt = publishedAt,
                ),
            )
            application { module(testDependencies(repositories = repositories)) }

            val notes = queryAccountNotes(TestServerEnv.USERNAME, limit = 10).accountNotes()
            val nodes = notes.nodes()

            assertEquals(1, nodes.size)
            assertEquals("abc123", nodes[0].string("id"))
            assertEquals("https://${TestServerEnv.DOMAIN}/notes/abc123", nodes[0].string("url"))
            assertEquals("<p>本文</p>", nodes[0].string("contentHtml"))
            assertEquals(publishedAt.epochSecond, nodes[0].long("publishedAt"))
            assertEquals(false, notes.pageInfo().boolean("hasMore"))
        }

    @Test
    fun `投稿は新しい順に返る`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
            repositories.notes.add(
                NewNote(
                    username = TestServerEnv.USERNAME,
                    publicId = "older",
                    contentHtml = "<p>古い</p>",
                    publishedAt = Instant.parse("2026-08-08T10:00:00Z"),
                ),
            )
            repositories.notes.add(
                NewNote(
                    username = TestServerEnv.USERNAME,
                    publicId = "newer",
                    contentHtml = "<p>新しい</p>",
                    publishedAt = Instant.parse("2026-08-09T11:00:00Z"),
                ),
            )
            application { module(testDependencies(repositories = repositories)) }

            val nodes = queryAccountNotes(TestServerEnv.USERNAME, limit = 10).accountNotes().nodes()

            assertEquals(listOf("newer", "older"), nodes.map { it.string("url").substringAfterLast('/') })
        }

    @Test
    fun `投稿の続きはカーソルで引ける`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = TestServerEnv.USERNAME, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
            repeat(3) { index ->
                repositories.notes.add(
                    NewNote(
                        username = TestServerEnv.USERNAME,
                        publicId = "note$index",
                        contentHtml = "<p>$index</p>",
                        publishedAt = Instant.parse("2026-08-09T1$index:00:00Z"),
                    ),
                )
            }
            application { module(testDependencies(repositories = repositories)) }

            val page1 = queryAccountNotes(TestServerEnv.USERNAME, limit = 2).accountNotes()
            assertEquals(2, page1.nodes().size)
            assertEquals(true, page1.pageInfo().boolean("hasMore"))

            val page2 = queryAccountNotes(
                TestServerEnv.USERNAME,
                cursor = page1.pageInfo().string("nextCursor"),
                limit = 2,
            ).accountNotes()
            assertEquals(1, page2.nodes().size)
            assertEquals(false, page2.pageInfo().boolean("hasMore"))
        }

    private suspend fun ApplicationTestBuilder.queryAccountNotes(
        username: String,
        cursor: String? = null,
        limit: Int = 20,
    ): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)

            val query =
                "query AccountNotesQuery(${'$'}username: String!, ${'$'}cursor: String, ${'$'}limit: Int!) { " +
                    "notes(query: { username: ${'$'}username, cursor: ${'$'}cursor, limit: ${'$'}limit }) { " +
                    "nodes { id url contentHtml publishedAt } pageInfo { hasMore nextCursor } } }"

            val variables = buildString {
                append("{")
                append(""""username":${JsonPrimitive(username)},""")
                if (cursor != null) {
                    append(""""cursor":${JsonPrimitive(cursor)},""")
                }
                append(""""limit":${JsonPrimitive(limit)}""")
                append("}")
            }

            setBody("""{"query":${JsonPrimitive(query)},"variables":$variables}""")
        }

    private suspend fun ApplicationTestBuilder.queryAccounts(cursor: String? = null, limit: Int = 20): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)

            val query =
                "query Accounts(${'$'}cursor: String, ${'$'}limit: Int!) { " +
                    "accounts(cursor: ${'$'}cursor, limit: ${'$'}limit) { " +
                    "nodes { id username acct actorUrl } pageInfo { hasMore nextCursor } } }"

            val variables = buildString {
                append("{")
                if (cursor != null) {
                    append(""""cursor":${JsonPrimitive(cursor)},""")
                }
                append(""""limit":${JsonPrimitive(limit)}""")
                append("}")
            }

            setBody("""{"query":${JsonPrimitive(query)},"variables":$variables}""")
        }

    private suspend fun ApplicationTestBuilder.queryAccount(username: String): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)

            val query =
                "query Account(${'$'}username: String!) { " +
                    "account(username: ${'$'}username) { id username acct actorUrl } }"

            setBody(
                """{"query":${JsonPrimitive(query)},"variables":{"username":${JsonPrimitive(username)}}}""",
            )
        }

    private companion object {
        suspend fun HttpResponse.body(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

        /**
         * `data.account` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.account(): JsonObject = body().obj("data").obj("account")

        suspend fun HttpResponse.accountNotes(): JsonObject = body().obj("data").obj("notes")

        /**
         * `data.accounts` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.accounts(): JsonObject = body().obj("data").obj("accounts")

        fun JsonObject.nodes(): List<JsonObject> = getValue("nodes").jsonArray.map { it.jsonObject }

        fun JsonObject.pageInfo(): JsonObject = obj("pageInfo")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

        fun JsonObject.long(name: String): Long = getValue(name).jsonPrimitive.content.toLong()
    }
}
