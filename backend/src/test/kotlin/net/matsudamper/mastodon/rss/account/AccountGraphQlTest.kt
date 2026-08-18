package net.matsudamper.mastodon.rss.account

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH
import net.matsudamper.mastodon.rss.testDependencies

// アカウント画面が引く口。ログインが要らないので、管理画面のものとは別に見る。
class AccountGraphQlTest {
    @Test
    fun `設定で決まるアカウントは引ける`() =
        testApplication {
            application { module(testDependencies()) }

            val account = queryAccount(TestServerEnv.USERNAME).account()

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
            assertEquals(TestServerEnv.USERNAME, page1Nodes[0].string("username"))
            assertEquals("@${TestServerEnv.USERNAME}@${TestServerEnv.DOMAIN}", page1Nodes[0].string("acct"))
            assertEquals("https://${TestServerEnv.DOMAIN}/users/${TestServerEnv.USERNAME}", page1Nodes[0].string("actorUrl"))
            assertEquals("feed1", page1Nodes[1].string("username"))
            assertEquals(true, page1.pageInfo().boolean("hasMore"))
            assertEquals("feed1", page1.pageInfo().string("nextCursor"))

            val page2 = queryAccounts(cursor = page1.pageInfo().string("nextCursor"), limit = 2).accounts()
            val page2Nodes = page2.nodes()
            assertEquals(1, page2Nodes.size)
            assertEquals("feed2", page2Nodes[0].string("username"))
            assertEquals(false, page2.pageInfo().boolean("hasMore"))
            assertEquals(JsonNull, page2.pageInfo().getValue("nextCursor"))
        }

    @Test
    fun `limit が上限を超えていても一覧が返る`() =
        testApplication {
            val repositories = FakeRepositories()
            repositories.accounts.add(username = "feed1", createdAt = Instant.now())
            application { module(testDependencies(repositories = repositories)) }

            val page = queryAccounts(limit = Int.MAX_VALUE).accounts()

            assertEquals(
                listOf(TestServerEnv.USERNAME, "feed1"),
                page.nodes().map { it.string("username") },
            )
            assertEquals(false, page.pageInfo().boolean("hasMore"))
        }

    private suspend fun ApplicationTestBuilder.queryAccounts(cursor: String? = null, limit: Int = 20): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)

            val query =
                "query Accounts(${'$'}cursor: String, ${'$'}limit: Int!) { " +
                    "accounts(cursor: ${'$'}cursor, limit: ${'$'}limit) { " +
                    "nodes { username acct actorUrl } pageInfo { hasMore nextCursor } } }"

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
                    "account(username: ${'$'}username) { username acct actorUrl } }"

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

        /**
         * `data.accounts` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.accounts(): JsonObject = body().obj("data").obj("accounts")

        fun JsonObject.nodes(): List<JsonObject> = getValue("nodes").jsonArray.map { it.jsonObject }

        fun JsonObject.pageInfo(): JsonObject = obj("pageInfo")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean
    }
}
