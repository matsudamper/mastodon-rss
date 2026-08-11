package net.matsudamper.mastodon.rss.admin

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
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
import net.matsudamper.mastodon.rss.module
import net.matsudamper.mastodon.rss.testDependencies
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 管理画面のログインを HTTP から見た形で確認する。
// Cookie の保存はクライアント側の実装に寄るので、テストでは Set-Cookie を自分で読んで付け直す。
class AdminRoutesTest {
    @Test
    fun `ログインしていなければ loggedIn は false`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = client.get(AdminApiPaths.SESSION)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"loggedIn":false,"passwordConfigured":true}""", response.bodyAsText())
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun `パスワードハッシュが未設定なら passwordConfigured は false`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            val response = client.get(AdminApiPaths.SESSION)

            assertEquals("""{"loggedIn":false,"passwordConfigured":false}""", response.bodyAsText())
        }

    @Test
    fun `パスワードハッシュが未設定ならログインできない`() =
        testApplication {
            applicationWith(passwordConfigured = false)

            val response = login(PASSWORD)

            // パスワードが違うのと同じ 401 にすると、何を直せばよいのか画面から分からない
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertContains(response.bodyAsText(), "ADMIN_PASSWORD_HASH")
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `正しいパスワードでログインするとセッション Cookie が返る`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = login(PASSWORD)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"loggedIn":true,"passwordConfigured":true}""", response.bodyAsText())
            assertNotNull(response.sessionCookieValue())
        }

    @Test
    fun `セッション Cookie は HttpOnly と SameSite と Secure が付く`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val setCookie = assertNotNull(login(PASSWORD).headers[HttpHeaders.SetCookie])

            // JavaScript から読めるようにすると、script を差し込まれたときに持ち出される
            assertContains(setCookie, "HttpOnly")
            // 他所のページから管理 API を叩かれても Cookie が付かないようにする
            assertContains(setCookie, "SameSite=Strict")
            // 平文で流れると Cookie ごと持って行かれる
            assertContains(setCookie, "Secure")
            // 発行と削除で Path がずれるとブラウザが別の Cookie として扱う
            assertContains(setCookie, "Path=/")
        }

    @Test
    fun `ADMIN_COOKIE_SECURE が false なら Secure を付けない`() =
        testApplication {
            applicationWith(passwordConfigured = true, cookieSecure = false)

            val setCookie = assertNotNull(login(PASSWORD).headers[HttpHeaders.SetCookie])

            assertFalse(setCookie.contains("Secure"))
            assertContains(setCookie, "HttpOnly")
        }

    @Test
    fun `パスワードが違えば 401 で Cookie も返らない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = login("ちがうパスワード")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `本文が JSON でなければ 400`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("パスワード")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `ログインで得た Cookie を付ければログイン済みになる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(login(PASSWORD).sessionCookieValue())

            val response = client.get(AdminApiPaths.SESSION) { withSessionCookie(token) }

            assertEquals("""{"loggedIn":true,"passwordConfigured":true}""", response.bodyAsText())
        }

    @Test
    fun `知らない Cookie ではログイン済みにならない`() =
        testApplication {
            applicationWith(passwordConfigured = true)

            val response = client.get(AdminApiPaths.SESSION) { withSessionCookie("知らないトークン") }

            assertEquals("""{"loggedIn":false,"passwordConfigured":true}""", response.bodyAsText())
        }

    @Test
    fun `ログアウトすると同じ Cookie では通らなくなる`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(login(PASSWORD).sessionCookieValue())

            val logout = client.post(AdminApiPaths.LOGOUT) { withSessionCookie(token) }
            assertEquals(HttpStatusCode.OK, logout.status)
            assertEquals("""{"loggedIn":false,"passwordConfigured":true}""", logout.bodyAsText())

            // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
            val session = client.get(AdminApiPaths.SESSION) { withSessionCookie(token) }
            assertEquals("""{"loggedIn":false,"passwordConfigured":true}""", session.bodyAsText())
        }

    @Test
    fun `ログアウトは Cookie を消す指示を返す`() =
        testApplication {
            applicationWith(passwordConfigured = true)
            val token = assertNotNull(login(PASSWORD).sessionCookieValue())

            val logout = client.post(AdminApiPaths.LOGOUT) { withSessionCookie(token) }

            assertEquals("", logout.sessionCookieValue())
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

    private suspend fun ApplicationTestBuilder.login(password: String): HttpResponse =
        client.post(AdminApiPaths.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody("""{"password":"$password"}""")
        }

    private companion object {
        const val PASSWORD = "とても長いパスワード"

        /**
         * テスト用のハッシュ。反復回数はハッシュの文字列に入っていて検証もその回数で行うので、
         * 既定 (21 万) まで回さなくても経路は同じ。回すとテストのたびに待つことになるので落としてある。
         */
        val PASSWORD_HASH: String = PasswordHash.create(PASSWORD, iterations = 1_000).encode()

        /** Set-Cookie にセッションが入っていればその値。無ければ null */
        fun HttpResponse.sessionCookieValue(): String? =
            setCookie().firstOrNull { it.name == AdminSessions.COOKIE_NAME }?.value

        fun HttpRequestBuilder.withSessionCookie(token: String) {
            header(HttpHeaders.Cookie, "${AdminSessions.COOKIE_NAME}=$token")
        }
    }
}
