package dev.matsudamper.mastodonrss.admin

import dev.matsudamper.mastodonrss.admin.api.AdminApiPaths
import dev.matsudamper.mastodonrss.admin.api.AdminErrorResponse
import dev.matsudamper.mastodonrss.admin.api.AdminPasswordHashRequest
import dev.matsudamper.mastodonrss.admin.api.AdminPasswordHashResponse
import dev.matsudamper.mastodonrss.admin.api.AdminSessionResponse
import dev.matsudamper.mastodonrss.crypto.PasswordHash
import dev.matsudamper.mastodonrss.json.AppJson
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 管理画面のログイン。
// 静的ファイルはテスト用のリソース (admin-test-static) を配信して、
// :frontend のビルド成果物の有無に左右されないようにする。
class AdminRoutesTest {
    private val password = "correct horse battery"

    // 既定の 210,000 回はテストには重いだけなので減らす
    private val passwordHash = PasswordHash.create(password, iterations = 1_000)

    private fun ApplicationTestBuilder.installAdmin(config: AdminConfig) {
        application {
            routing {
                adminRoutes(config, AdminSessions(config.sessionTtl), AdminStaticContent("admin-test-static"))
            }
        }
    }

    private fun configuredConfig(cookieSecure: Boolean = true): AdminConfig =
        AdminConfig(
            passwordHash = passwordHash,
            sessionTtl = AdminConfig.DEFAULT_SESSION_TTL,
            cookieSecure = cookieSecure,
        )

    private fun unconfiguredConfig(): AdminConfig =
        AdminConfig(
            passwordHash = null,
            sessionTtl = AdminConfig.DEFAULT_SESSION_TTL,
            cookieSecure = true,
        )

    private suspend fun HttpResponse.session(): AdminSessionResponse =
        AppJson.decodeFromString(AdminSessionResponse.serializer(), bodyAsText())

    private suspend fun HttpResponse.error(): String =
        AppJson.decodeFromString(AdminErrorResponse.serializer(), bodyAsText()).message

    private fun HttpResponse.sessionCookieValue(): String? =
        setCookie()
            .firstOrNull {
                it.name == ADMIN_SESSION_COOKIE
            }?.value

    @Test
    fun `ハッシュ未設定ならログインできない状態として返る`() =
        testApplication {
            installAdmin(unconfiguredConfig())

            val session = client.get(AdminApiPaths.SESSION).session()

            assertFalse(session.loginConfigured)
            assertFalse(session.authenticated)
        }

    @Test
    fun `ハッシュ未設定でログインを試すと理由が返る`() =
        testApplication {
            installAdmin(unconfiguredConfig())

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"$password"}""")
                }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.error().contains(AdminConfig.ENV_PASSWORD_HASH))
        }

    @Test
    fun `正しいパスワードでログインするとセッションの Cookie が返る`() =
        testApplication {
            installAdmin(configuredConfig())

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"$password"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.session().authenticated)

            val cookie = assertNotNull(response.setCookie().firstOrNull { it.name == ADMIN_SESSION_COOKIE })
            assertTrue(cookie.value.isNotEmpty())
            // ActivityPub のエンドポイントには送らせない
            assertEquals(AdminApiPaths.BASE, cookie.path)
            assertEquals(true, cookie.httpOnly)
            assertEquals(true, cookie.secure)
            assertEquals("Strict", cookie.extensions["SameSite"])
        }

    @Test
    fun `Cookie の Secure は設定で外せる`() =
        testApplication {
            // http の localhost で試すときは Secure が付いていると Cookie が保存されない
            installAdmin(configuredConfig(cookieSecure = false))

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"$password"}""")
                }

            assertEquals(
                false,
                assertNotNull(response.setCookie().firstOrNull { it.name == ADMIN_SESSION_COOKIE }).secure,
            )
        }

    @Test
    fun `違うパスワードでは 401 になり Cookie も返らない`() =
        testApplication {
            installAdmin(configuredConfig())

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"違うパスワード"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(response.sessionCookieValue())
        }

    @Test
    fun `ログインした Cookie を付けるとログイン済みとして返る`() =
        testApplication {
            installAdmin(configuredConfig())

            val token = assertNotNull(login().sessionCookieValue())

            val session =
                client
                    .get(AdminApiPaths.SESSION) {
                        header(HttpHeaders.Cookie, "$ADMIN_SESSION_COOKIE=$token")
                    }.session()

            assertTrue(session.authenticated)
            assertTrue(session.loginConfigured)
        }

    @Test
    fun `知らない Cookie ではログイン済みにならない`() =
        testApplication {
            installAdmin(configuredConfig())

            val session =
                client
                    .get(AdminApiPaths.SESSION) {
                        header(HttpHeaders.Cookie, "$ADMIN_SESSION_COOKIE=適当なトークン")
                    }.session()

            assertFalse(session.authenticated)
        }

    @Test
    fun `ログアウトすると同じ Cookie では通らなくなる`() =
        testApplication {
            installAdmin(configuredConfig())

            val token = assertNotNull(login().sessionCookieValue())

            val logout =
                client.post(AdminApiPaths.LOGOUT) {
                    header(HttpHeaders.Cookie, "$ADMIN_SESSION_COOKIE=$token")
                }

            assertFalse(logout.session().authenticated)
            // ブラウザ側からも消す
            assertEquals(0, assertNotNull(logout.setCookie().firstOrNull { it.name == ADMIN_SESSION_COOKIE }).maxAge)

            val session =
                client
                    .get(AdminApiPaths.SESSION) {
                        header(HttpHeaders.Cookie, "$ADMIN_SESSION_COOKIE=$token")
                    }.session()

            assertFalse(session.authenticated)
        }

    @Test
    fun `ハッシュ未設定ならログインなしでハッシュを作れる`() =
        testApplication {
            // ここを閉じると最初のハッシュを作る手段が無くなる
            installAdmin(unconfiguredConfig())

            val response = requestPasswordHash("設定する前に作るパスワード")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = AppJson.decodeFromString(AdminPasswordHashResponse.serializer(), response.bodyAsText())
            assertEquals(AdminConfig.ENV_PASSWORD_HASH, body.environmentVariable)
            // 作った値をそのまま環境変数に入れれば、そのパスワードでログインできる
            assertTrue(PasswordHash.parse(body.hash).matches("設定する前に作るパスワード"))
        }

    @Test
    fun `ハッシュ設定済みならログインしないと作れない`() =
        testApplication {
            installAdmin(configuredConfig())

            val response = requestPasswordHash("新しく設定するパスワード")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `ログインしていれば作り直せる`() =
        testApplication {
            installAdmin(configuredConfig())

            val token = assertNotNull(login().sessionCookieValue())

            val response = requestPasswordHash("新しく設定するパスワード", token = token)

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `短すぎるパスワードは断る`() =
        testApplication {
            installAdmin(unconfiguredConfig())

            val response = requestPasswordHash("短い")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `長すぎるパスワードは断る`() =
        testApplication {
            // ハッシュ化は PBKDF2 を数十万回まわすので、長い入力を投げ続けられると CPU を占有される
            installAdmin(unconfiguredConfig())

            val response = requestPasswordHash("あ".repeat(1_000))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `壊れた JSON は 400 で返す`() =
        testApplication {
            installAdmin(configuredConfig())

            val response =
                client.post(AdminApiPaths.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody("{")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `admin を開くと末尾にスラッシュを付けて返す`() =
        testApplication {
            // /admin のままだと index.html の中の相対パス（frontend.js）が / から引かれる
            installAdmin(configuredConfig())
            val noRedirectClient = createClient { followRedirects = false }

            val response = noRedirectClient.get(AdminApiPaths.BASE)

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("${AdminApiPaths.BASE}/", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `画面は静的ファイルとして返る`() =
        testApplication {
            installAdmin(configuredConfig())

            val response = client.get("${AdminApiPaths.BASE}/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
            assertTrue(response.bodyAsText().contains("テスト用の index"))
        }

    @Test
    fun `画面の中のパスでも index を返す`() =
        testApplication {
            // ハッシュ生成の画面はファイルとして存在しない。リロードや直接開いても同じ画面になる必要がある
            installAdmin(configuredConfig())

            val response = client.get(AdminApiPaths.PASSWORD_HASH_PAGE)

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("テスト用の index"))
        }

    @Test
    fun `無い静的ファイルは 404`() =
        testApplication {
            // 拡張子付きのパスまで index を返すと、読み込み失敗が 200 になって切り分けが難しくなる
            installAdmin(configuredConfig())

            assertEquals(HttpStatusCode.NotFound, client.get("${AdminApiPaths.BASE}/存在しない.js").status)
        }

    private suspend fun ApplicationTestBuilder.login(): HttpResponse =
        client.post(AdminApiPaths.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody("""{"password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.requestPasswordHash(raw: String, token: String? = null): HttpResponse =
        client.post(AdminApiPaths.PASSWORD_HASH) {
            contentType(ContentType.Application.Json)
            if (token != null) {
                header(HttpHeaders.Cookie, "$ADMIN_SESSION_COOKIE=$token")
            }
            setBody(AppJson.encodeToString(AdminPasswordHashRequest.serializer(), AdminPasswordHashRequest(raw)))
        }
}
