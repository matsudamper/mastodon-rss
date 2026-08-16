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
import io.ktor.http.setCookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.matsudamper.mastodon.rss.TestServerEnv
import net.matsudamper.mastodon.rss.crypto.PasswordHash
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
    fun `列挙には設定で決まるアカウントが必ず入る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val account = queryAccounts(token).accounts().single().jsonObject

            assertEquals(TestServerEnv.USERNAME, account.string("username"))
            assertEquals("@${TestServerEnv.USERNAME}@${TestServerEnv.DOMAIN}", account.string("acct"))
            assertEquals(
                "https://${TestServerEnv.DOMAIN}/users/${TestServerEnv.USERNAME}",
                account.string("actorUrl"),
            )
            // 設定で決まるアカウントは管理画面から消せない。追加した時刻も持っていない
            assertFalse(account.boolean("deletable"))
            assertEquals(JsonNull, account.getValue("createdAt"))
        }

    @Test
    fun `追加したアカウントが列挙に入る`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val added = assertNotNull(mutateAddAccount("feed1", token).addAccountResult().obj("account"))

            assertEquals("feed1", added.string("username"))
            assertEquals("@feed1@${TestServerEnv.DOMAIN}", added.string("acct"))
            assertTrue(added.boolean("deletable"))
            // 時刻は文字列にせずエポックからの秒数で返す。書式の解釈を受け取る側に委ねない
            assertTrue(added.getValue("createdAt").jsonPrimitive.long > 0)

            assertEquals(
                listOf(TestServerEnv.USERNAME, "feed1"),
                queryAccounts(token).accounts().map { it.jsonObject.string("username") },
            )
        }

    @Test
    fun `使えない文字は入力にあったものを返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            val result = mutateAddAccount("feed 1/あ", token).addAccountResult()

            assertEquals(JsonNull, result.getValue("account"))
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
    fun `設定で決まるアカウントと同じ名前は追加できない`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(mutateLogin(PASSWORD).sessionCookieValue())

            // 設定で決まるアカウントも引き当ての対象なので、名前は埋まっている
            val result = mutateAddAccount(TestServerEnv.USERNAME.uppercase(), token).addAccountResult()

            assertTrue(result.failure().boolean("isDuplicated"))
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

            assertTrue(mutateAddAccount("feed1").body().containsKey("errors"))
        }

    // スキーマに無いものが通ってしまうと、結線の漏れに気付けない
    @Test
    fun `スキーマに無いフィールドは errors になる`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = graphQl("query { admin { 知らないフィールド } }")

            // 問い合わせのエラーは HTTP 200 で errors に入る
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.body().containsKey("errors"))
        }

    private fun ApplicationTestBuilder.applicationWith(
        passwordConfigured: Boolean,
        cookieSecure: Boolean = true,
    ) {
        val values =
            buildList {
                add("ADMIN_COOKIE_SECURE" to cookieSecure.toString())
                if (passwordConfigured) add("ADMIN_PASSWORD_HASH" to PASSWORD_HASH)
            }

        application {
            module(testDependencies(env = TestServerEnv.of(*values.toTypedArray())))
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
        graphQl("query { admin { accounts { $ACCOUNT_FIELDS } } }", token = token)

    private suspend fun ApplicationTestBuilder.mutateAddAccount(
        username: String,
        token: String? = null,
    ): HttpResponse =
        graphQl(
            query =
            "mutation Add(${'$'}username: String!) { admin { " +
                "addAccount(username: ${'$'}username) { account { $ACCOUNT_FIELDS } " +
                "failure { unusableCharacters maxLength minLength isDuplicated } } } }",
            token = token,
            variables = """{"username":${JsonPrimitive(username)}}""",
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

        const val ACCOUNT_FIELDS = "username acct actorUrl deletable createdAt"

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

        suspend fun HttpResponse.accounts(): List<JsonElement> = admin().getValue("accounts").jsonArray

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
