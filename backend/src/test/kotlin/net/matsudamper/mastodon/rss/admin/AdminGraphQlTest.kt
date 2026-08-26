package net.matsudamper.mastodon.rss.admin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.setCookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH
import net.matsudamper.mastodon.rss.testDependencies

// 管理画面のログインを GraphQL の口から確認する。
// Cookie の保存はクライアント側の実装に寄るので、テストでは Set-Cookie を自分で読んで付け直す。
class AdminGraphQlTest {
    @Test
    fun `ログインしていなければ loggedIn は false`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = querySession()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val session = response.session()
            assertFalse(session.boolean("loggedIn"))
            assertTrue(session.boolean("passwordConfigured"))
        }

    @Test
    fun `パスワードハッシュが未設定なら passwordConfigured は false`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            assertFalse(querySession().session().boolean("passwordConfigured"))
        }

    @Test
    fun `パスワードハッシュが未設定ならログインできない`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            val response = mutateLogin(PASSWORD)

            val result = response.loginResult()
            assertEquals("NOT_CONFIGURED", result.string("failure"))
            assertFalse(result.obj("session").boolean("loggedIn"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `正しいパスワードでログインするとセッション Cookie が返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = mutateLogin(PASSWORD)

            assertEquals(HttpStatusCode.OK, response.status)

            val result = response.loginResult()
            assertTrue(result.obj("session").boolean("loggedIn"))
            assertEquals(JsonNull, result.getValue("failure"))
            assertNotNull(response.sessionCookieValue())
        }

    @Test
    fun `セッション Cookie は HttpOnly と SameSite と Secure が付く`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val setCookie = assertNotNull(mutateLogin(PASSWORD).headers[HttpHeaders.SetCookie])

            assertContains(setCookie, "HttpOnly")
            assertContains(setCookie, "SameSite=Strict")
            assertContains(setCookie, "Secure")
            assertContains(setCookie, "Path=/")
        }

    @Test
    fun `ADMIN_COOKIE_SECURE が false なら Secure を付けない`() =
        testApplication {
            applicationWith(passwordConfigured = true, cookieSecure = false)

            val setCookie = assertNotNull(mutateLogin(PASSWORD).headers[HttpHeaders.SetCookie])

            assertFalse(setCookie.contains("Secure"))
            assertContains(setCookie, "HttpOnly")
        }

    @Test
    fun `パスワードが違えば WRONG_PASSWORD で Cookie も返らない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = mutateLogin("ちがうパスワード")

            val result = response.loginResult()
            assertEquals("WRONG_PASSWORD", result.string("failure"))
            assertFalse(result.obj("session").boolean("loggedIn"))
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `ログインで得た Cookie を付ければログイン済みになる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertTrue(querySession(token).session().boolean("loggedIn"))
        }

    @Test
    fun `知らない Cookie ではログイン済みにならない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            assertFalse(querySession("知らないトークン").session().boolean("loggedIn"))
        }

    @Test
    fun `ログアウトすると同じ Cookie では通らなくなる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertFalse(mutateLogout(token).admin().obj("logout").boolean("loggedIn"))

            // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
            assertFalse(querySession(token).session().boolean("loggedIn"))
        }

    @Test
    fun `ログアウトは Cookie を消す指示を返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertEquals("", mutateLogout(token).sessionCookieValue())
        }

    @Test
    fun `本文が GraphQL のリクエストとして読めなければ 400`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response =
                client.post(GRAPHQL_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("パスワード")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `ログインしていなければアカウントを列挙できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            assertTrue(queryAccounts().body().containsKey("errors"))
        }

    @Test
    fun `アカウントが無ければ列挙は空`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertEquals(emptyList(), queryAccounts(token).accounts())
        }

    @Test
    fun `追加したアカウントが列挙に入る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val added = assertNotNull(mutateAddAccount("feed1", token).addAccountResult().obj("adminAccount"))
            val account = added.obj("account")

            assertEquals("feed1", account.string("username"))
            assertEquals("@feed1@${TestServerEnv.DOMAIN}", account.string("acct"))
            // 時刻は文字列にせずエポックからの秒数で返す。書式の解釈を受け取る側に委ねない
            assertTrue(added.getValue("createdAt").jsonPrimitive.long > 0)

            assertEquals(
                listOf("feed1"),
                queryAccounts(token).accounts().map { it.jsonObject.obj("account").string("username") },
            )
        }

    @Test
    fun `追加したアカウントには id が入る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            mutateAddAccount("feed1", token)

            val account = queryAccount("feed1", token).admin().obj("adminAccount").obj("account")
            assertNotNull(account.getValue("id").jsonPrimitive.long)
        }

    @Test
    fun `ログインしていなければ previewFeed は拒否される`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val errors = queryPreviewFeed("https://example.com/feed.xml").body().getValue("errors").jsonArray
            assertTrue(errors.isNotEmpty())
        }

    @Test
    fun `ログインしていなければ saveFeed は拒否される`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val errors = mutateSaveFeed(accountId = 1, url = "https://example.com/feed.xml")
                .body()
                .getValue("errors")
                .jsonArray
            assertTrue(errors.isNotEmpty())
        }

    @Test
    fun `小数の accountId は saveFeed に渡せない`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val response = graphQl(
                query =
                "mutation Save(${'$'}accountId: AccountId!, ${'$'}url: String!) { admin { " +
                    "saveFeed(saveFeedQuery: { accountId: ${'$'}accountId, url: ${'$'}url }) { failure { reason } } } }",
                token = token,
                variables = """{"accountId":1.9,"url":"https://example.com/feed.xml"}""",
            )

            assertTrue(response.body().containsKey("errors"))
        }

    @Test
    fun `saveFeed は既存記事を投稿しない`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(
                passwordConfigured = true,
                repositories = repositories,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long

            val result = mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token).admin().obj("saveFeed")

            assertEquals(JsonNull, result.getValue("failure"))
            assertEquals(0, repositories.notes.list(username = "feed1", after = null, limit = 10).size)
        }

    @Test
    fun `unpublishedFeedItems は未投稿の記事を返す`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(
                passwordConfigured = true,
                repositories = repositories,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)

            val result = queryUnpublishedFeedItems(accountId = accountId, token = token)
                .admin()
                .obj("unpublishedFeedItems")

            assertEquals(JsonNull, result.getValue("failure"))
            assertEquals(
                listOf("1 本目", "2 本目"),
                result.getValue("items").jsonArray.map { it.jsonObject.string("title") },
            )
        }

    @Test
    fun `postFeedItems は未投稿の記事を投稿する`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(
                passwordConfigured = true,
                repositories = repositories,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)

            val result = mutatePostFeedItems(accountId = accountId, token = token).admin().obj("postFeedItems")

            assertEquals(JsonNull, result.getValue("failure"))
            assertEquals(
                listOf("1 本目", "2 本目"),
                result.getValue("items").jsonArray.map { it.jsonObject.string("title") },
            )
            assertEquals(2, repositories.notes.list(username = "feed1", after = null, limit = 10).size)
        }

    @Test
    fun `feedItems は取り込んだ記事を新しい順に返す`() =
        testApplication {
            applicationWith(
                passwordConfigured = true,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)

            val result = queryFeedItems(accountId = accountId, token = token).admin().obj("feedItems")

            assertEquals(JsonNull, result.getValue("failure"))
            val connection = result.obj("connection")
            assertEquals(
                listOf("2 本目", "1 本目"),
                connection.getValue("nodes").jsonArray.map { it.jsonObject.string("title") },
            )
            assertEquals(
                listOf("PENDING", "PENDING"),
                connection.getValue("nodes").jsonArray.map { it.jsonObject.string("state") },
            )
        }

    @Test
    fun `deleteFeedItem は記事だけ消し配信した投稿は残る`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(
                passwordConfigured = true,
                repositories = repositories,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)
            mutatePostFeedItems(accountId = accountId, token = token)
            val feedItemId = queryFeedItems(accountId = accountId, token = token)
                .admin()
                .obj("feedItems")
                .obj("connection")
                .getValue("nodes")
                .jsonArray
                .first()
                .jsonObject
                .getValue("id")
                .jsonPrimitive
                .long

            val result = mutateDeleteFeedItem(
                accountId = accountId,
                feedItemId = feedItemId,
                token = token,
            ).admin().obj("deleteFeedItem")

            assertEquals(JsonNull, result.getValue("failure"))
            assertEquals(feedItemId, result.getValue("deletedId").jsonPrimitive.long)
            assertEquals(
                listOf("1 本目"),
                queryFeedItems(accountId = accountId, token = token)
                    .admin()
                    .obj("feedItems")
                    .obj("connection")
                    .getValue("nodes")
                    .jsonArray
                    .map { it.jsonObject.string("title") },
            )
            assertEquals(2, repositories.notes.list(username = "feed1", after = null, limit = 10).size)
        }

    @Test
    fun `消した記事は最新情報の投稿で投稿し直される`() =
        testApplication {
            val repositories = FakeRepositories()
            applicationWith(
                passwordConfigured = true,
                repositories = repositories,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)
            mutatePostFeedItems(accountId = accountId, token = token)
            val feedItemId = queryFeedItems(accountId = accountId, token = token)
                .admin()
                .obj("feedItems")
                .obj("connection")
                .getValue("nodes")
                .jsonArray
                .first()
                .jsonObject
                .getValue("id")
                .jsonPrimitive
                .long
            mutateDeleteFeedItem(accountId = accountId, feedItemId = feedItemId, token = token)

            val result = mutatePostFeedItems(accountId = accountId, token = token).admin().obj("postFeedItems")

            assertEquals(
                listOf("2 本目"),
                result.getValue("items").jsonArray.map { it.jsonObject.string("title") },
            )
            assertEquals(3, repositories.notes.list(username = "feed1", after = null, limit = 10).size)
        }

    @Test
    fun `フィードが無いアカウントの記事は消せない`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long

            val result = mutateDeleteFeedItem(
                accountId = accountId,
                feedItemId = 1,
                token = token,
            ).admin().obj("deleteFeedItem")

            assertEquals(JsonNull, result.getValue("deletedId"))
            assertEquals("NO_FEED", result.obj("failure").string("reason"))
        }

    @Test
    fun `フィード未登録なら feed は null`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            mutateAddAccount("feed1", token)

            val adminAccount = queryAccount("feed1", token).admin().obj("adminAccount")
            assertEquals(JsonNull, adminAccount.getValue("feed"))
        }

    @Test
    fun `saveFeed のあと adminAccount の feed が返る`() =
        testApplication {
            applicationWith(
                passwordConfigured = true,
                feedFetcher = feedFetcherOf(FEED_XML),
            )
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)
            val accountId = queryAccount("feed1", token)
                .admin()
                .obj("adminAccount")
                .obj("account")
                .getValue("id")
                .jsonPrimitive
                .long
            mutateSaveFeed(accountId = accountId, url = FEED_URL, token = token)

            val feed = queryAccount("feed1", token).admin().obj("adminAccount").obj("feed")

            assertEquals(FEED_URL, feed.string("url"))
            assertEquals("サンプル", feed.string("title"))
        }

    @Test
    fun `アカウント 1 つを名前で引ける`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)

            val adminAccount = assertNotNull(queryAccount("feed1", token).admin().getValue("adminAccount").jsonObject)

            assertEquals("feed1", adminAccount.obj("account").string("username"))
            // フォロワーがいなくても 0 が返る。画面が「取れていない」と区別できるようにする
            assertEquals(0, adminAccount.getValue("followerCount").jsonPrimitive.int)
        }

    @Test
    fun `応答しない名前を引くと null`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            assertEquals(JsonNull, queryAccount("nobody", token).admin().getValue("adminAccount"))
        }

    @Test
    fun `ログインしていなければアカウントを引けない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val body = queryAccount(TestServerEnv.USERNAME).body()

            assertNull(body["data"]?.jsonObject?.get("admin")?.jsonObject?.get("adminAccount")?.takeIf { it != JsonNull })
            assertContains(body.getValue("errors").jsonArray.toString(), GraphQlEngine.GENERIC_ERROR_MESSAGE)
        }

    @Test
    fun `使えない文字は入力にあったものを返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val result = mutateAddAccount("feed 1/あ", token).addAccountResult()

            assertEquals(JsonNull, result.getValue("adminAccount"))
            // どの文字が駄目なのかを画面が自分で決めなくて済むようにする
            assertEquals(
                listOf(" ", "/", "あ"),
                result.failure().getValue("unusableCharacters").jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun `末尾に置けない文字は使えない文字として返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            // `-` は名前の間には置けるが、末尾には置けない
            val result = mutateAddAccount("feed-", token).addAccountResult()

            assertEquals(
                listOf("-"),
                result.failure().getValue("unusableCharacters").jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun `長すぎる名前は上限と一緒に返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val failure = mutateAddAccount("a".repeat(31), token).addAccountResult().failure()

            assertEquals(30, failure.getValue("maxLength").jsonPrimitive.int)
            assertEquals(JsonNull, failure.getValue("minLength"))
        }

    @Test
    fun `名前が空なら下限が返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val failure = mutateAddAccount("   ", token).addAccountResult().failure()

            assertEquals(1, failure.getValue("minLength").jsonPrimitive.int)
        }

    @Test
    fun `当てはまる理由は同時に返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            // 1 つ直しても次で弾かれるのが分からないと直しようがない
            val failure = mutateAddAccount("あ".repeat(31), token).addAccountResult().failure()

            assertEquals(listOf("あ"), failure.getValue("unusableCharacters").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(30, failure.getValue("maxLength").jsonPrimitive.int)
        }

    @Test
    fun `同じ名前は追加できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())
            mutateAddAccount("feed1", token)

            val result = mutateAddAccount("FEED1", token).addAccountResult()

            assertTrue(result.failure().boolean("isDuplicated"))
        }

    @Test
    fun `ログインしていなければアカウントを追加できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val errors = mutateAddAccount("feed1").body().getValue("errors").jsonArray

            assertEquals(1, errors.size)
            assertEquals(
                GraphQlEngine.GENERIC_ERROR_MESSAGE,
                errors.single().jsonObject.getValue("message").jsonPrimitive.content,
            )
            assertEquals(setOf("message"), errors.single().jsonObject.keys)
        }

    // スキーマに無いものが通ってしまうと、結線の漏れに気付けない
    @Test
    fun `スキーマに無いフィールドは errors になる`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = graphQl("query { admin { 知らないフィールド } }")

            // 問い合わせのエラーは HTTP 200 で errors に入る
            assertEquals(HttpStatusCode.OK, response.status)
            val errors = response.body().getValue("errors").jsonArray
            assertEquals(1, errors.size)
            assertEquals(
                GraphQlEngine.GENERIC_ERROR_MESSAGE,
                errors.single().jsonObject.getValue("message").jsonPrimitive.content,
            )
            assertEquals(setOf("message"), errors.single().jsonObject.keys)
        }

    private fun ApplicationTestBuilder.applicationWith(
        passwordConfigured: Boolean,
        cookieSecure: Boolean = true,
        repositories: FakeRepositories = FakeRepositories(),
        feedFetcher: FeedFetchService = FeedFetchService(),
    ) {
        val values =
            buildList {
                add("ADMIN_COOKIE_SECURE" to cookieSecure.toString())
                if (passwordConfigured) add("ADMIN_PASSWORD_HASH" to PASSWORD_HASH)
            }

        application {
            module(
                testDependencies(
                    repositories = repositories,
                    env = TestServerEnv.of(*values.toTypedArray()),
                    feedFetcher = feedFetcher,
                ),
            )
        }
    }

    private suspend fun ApplicationTestBuilder.querySession(token: String? = null): HttpResponse =
        graphQl("query { admin { session { loggedIn passwordConfigured } } }", token = token)

    private suspend fun ApplicationTestBuilder.mutateLogin(password: String): HttpResponse =
        graphQl(
            query =
            "mutation Login(${'$'}password: String!) { admin { " +
                "login(password: ${'$'}password) { session { loggedIn passwordConfigured } failure } } }",
            variables = """{"password":${JsonPrimitive(password)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutateLogout(token: String): HttpResponse =
        graphQl("mutation { admin { logout { loggedIn passwordConfigured } } }", token = token)

    private suspend fun ApplicationTestBuilder.queryAccounts(token: String? = null): HttpResponse =
        graphQl("query { admin { adminAccounts { $ACCOUNT_FIELDS } } }", token = token)

    private suspend fun ApplicationTestBuilder.queryAccount(
        username: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "query Account(${'$'}username: String!) { admin { " +
                "adminAccount(username: ${'$'}username) { $ACCOUNT_FIELDS followerCount } } }",
            token = token,
            variables = """{"username":${JsonPrimitive(username)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutateAddAccount(
        username: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Add(${'$'}username: String!) { admin { " +
                "addAccount(username: ${'$'}username) { adminAccount { $ACCOUNT_FIELDS } " +
                "failure { unusableCharacters maxLength minLength isDuplicated } } } }",
            token = token,
            variables = """{"username":${JsonPrimitive(username)}}""",
        )

    private suspend fun ApplicationTestBuilder.queryPreviewFeed(
        url: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "query Preview(${'$'}url: String!) { admin { " +
                "previewFeed(url: ${'$'}url) { preview { title format itemCount } failure { reason } } } }",
            token = token,
            variables = """{"url":${JsonPrimitive(url)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutateSaveFeed(
        accountId: Long,
        url: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Save(${'$'}accountId: AccountId!, ${'$'}url: String!) { admin { " +
                "saveFeed(saveFeedQuery: { accountId: ${'$'}accountId, url: ${'$'}url }) { " +
                "feed { $FEED_FIELDS } failure { reason } } } }",
            token = token,
            variables =
            """{"accountId":${JsonPrimitive(accountId)},"url":${JsonPrimitive(url)}}""",
        )

    private suspend fun ApplicationTestBuilder.queryUnpublishedFeedItems(
        accountId: Long,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "query Unpublished(${'$'}accountId: AccountId!) { admin { " +
                "unpublishedFeedItems(query: { accountId: ${'$'}accountId }) { " +
                "items { title link } failure { reason } } } }",
            token = token,
            variables = """{"accountId":${JsonPrimitive(accountId)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutatePostFeedItems(
        accountId: Long,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation PostItems(${'$'}accountId: AccountId!) { admin { " +
                "postFeedItems(query: { accountId: ${'$'}accountId }) { items { title link } failure { reason } } } }",
            token = token,
            variables = """{"accountId":${JsonPrimitive(accountId)}}""",
        )

    private suspend fun ApplicationTestBuilder.queryFeedItems(
        accountId: Long,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "query Items(${'$'}accountId: AccountId!) { admin { " +
                "feedItems(query: { accountId: ${'$'}accountId, limit: 10 }) { " +
                "connection { nodes { id title state } pageInfo { hasMore nextCursor } } " +
                "failure { reason } } } }",
            token = token,
            variables = """{"accountId":${JsonPrimitive(accountId)}}""",
        )

    private suspend fun ApplicationTestBuilder.mutateDeleteFeedItem(
        accountId: Long,
        feedItemId: Long,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Delete(${'$'}accountId: AccountId!, ${'$'}feedItemId: FeedItemId!) { admin { " +
                "deleteFeedItem(query: { accountId: ${'$'}accountId, feedItemId: ${'$'}feedItemId }) { " +
                "deletedId failure { reason } } } }",
            token = token,
            variables =
            """{"accountId":${JsonPrimitive(accountId)},"feedItemId":${JsonPrimitive(feedItemId)}}""",
        )

    private suspend fun ApplicationTestBuilder.graphQl(
        query: String,
        token: String? = null,
        variables: String? = null,
    ): HttpResponse =
        client.post(GRAPHQL_PATH) {
            contentType(ContentType.Application.Json)
            if (token != null) withSessionCookie(token)

            setBody(
                buildString {
                    append("""{"query":${JsonPrimitive(query)}""")
                    if (variables != null) append(""","variables":$variables""")
                    append("}")
                },
            )
        }

    private companion object {
        const val PASSWORD = "とても長いパスワード"

        const val FEED_URL = "https://example.com/feed.xml"

        val FEED_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
                <item><title>2 本目</title><link>https://example.com/2</link></item>
              </channel>
            </rss>
        """.trimIndent()

        fun feedFetcherOf(xml: String): FeedFetchService {
            val engine = MockEngine {
                respond(
                    content = xml,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/rss+xml"),
                )
            }
            return FeedFetchService(HttpClient(engine))
        }

        const val FEED_FIELDS = "id url title siteUrl format createdAt"

        const val ACCOUNT_FIELDS = "account { id username acct actorUrl } createdAt feed { $FEED_FIELDS }"

        /**
         * 反復回数は検証にも使われるので、落としても経路は同じ。既定だとテストのたびに待つ
         */
        val PASSWORD_HASH: String = PasswordHash.create(PASSWORD, iterations = 1_000).encode()

        suspend fun HttpResponse.body(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

        /**
         * `data.admin` まで降りる。errors が入っていたらここで落ちる
         */
        suspend fun HttpResponse.admin(): JsonObject = body().obj("data").obj("admin")

        suspend fun HttpResponse.session(): JsonObject = admin().obj("session")

        suspend fun HttpResponse.loginResult(): JsonObject = admin().obj("login")

        suspend fun HttpResponse.accounts(): List<JsonElement> = admin().getValue("adminAccounts").jsonArray

        suspend fun HttpResponse.addAccountResult(): JsonObject = admin().obj("addAccount")

        fun JsonObject.failure(): JsonObject = obj("failure")

        fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

        fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

        fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

        /**
         * Set-Cookie のセッション。無ければ null
         */
        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == AdminSessionCookieManager.COOKIE_NAME }?.value

        fun HttpRequestBuilder.withSessionCookie(token: String) {
            header(HttpHeaders.Cookie, "${AdminSessionCookieManager.COOKIE_NAME}=$token")
        }
    }
}
