package net.matsudamper.mastodon.rss.admin

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import net.matsudamper.mastodon.rss.admin.api.AdminApiPaths
import net.matsudamper.mastodon.rss.admin.api.AdminErrorResponse
import net.matsudamper.mastodon.rss.admin.api.AdminLoginRequest
import net.matsudamper.mastodon.rss.admin.api.AdminPasswordHashRequest
import net.matsudamper.mastodon.rss.admin.api.AdminPasswordHashResponse
import net.matsudamper.mastodon.rss.admin.api.AdminPasswordPolicy
import net.matsudamper.mastodon.rss.admin.api.AdminSessionResponse
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * 管理画面と管理 API。
 *
 * ログインの状態は 3 つある。
 *
 * | `ADMIN_PASSWORD_HASH` | ログイン | ハッシュ生成 |
 * | --- | --- | --- |
 * | 未設定 | できない | 誰でもできる（初期設定のため） |
 * | 設定済み・未ログイン | できる | できない |
 * | 設定済み・ログイン済み | 済んでいる | できる（作り直しのため） |
 *
 * 未設定のときにハッシュ生成を開けているのは、そうしないと最初のハッシュを
 * 作る手段が無くなるため。この状態では管理画面から何も操作できず、
 * 生成 API も自分が送ったパスワードのハッシュを返すだけで、
 * サーバーの状態は何も変わらない。
 */
internal fun Route.adminRoutes(
    config: AdminConfig,
    sessions: AdminSessions,
    staticContent: AdminStaticContent = AdminStaticContent(),
) {
    // 静的ファイルより先に API を登録する。あとに置くと {...} の方に食われる
    route(AdminApiPaths.API_BASE) {
        get("session") {
            call.respondSession(config, sessions)
        }

        post("login") {
            val passwordHash = config.passwordHash
            if (passwordHash == null) {
                // ここに来るのは設定前だけ。ログインの口が無いことを画面に伝える
                call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "${AdminConfig.ENV_PASSWORD_HASH} が未設定なのでログインできない。" +
                        "${AdminApiPaths.PASSWORD_HASH_PAGE} でハッシュを作り、環境変数に入れて起動し直すこと",
                )
                return@post
            }

            val request = call.receiveJson(AdminLoginRequest.serializer()) ?: return@post

            if (!passwordHash.matches(request.password)) {
                // 「そのパスワードは存在しない」等の区別が付く文言にしない
                call.respondError(HttpStatusCode.Unauthorized, "パスワードが違う")
                return@post
            }

            call.response.cookies.append(sessionCookie(sessions.issue(), config))
            // このレスポンスの時点ではリクエストに Cookie が無いので、
            // 状態を引き直さずログイン済みとして返す
            call.respondJson(
                AdminSessionResponse.serializer(),
                AdminSessionResponse(authenticated = true, loginConfigured = true),
            )
        }

        post("logout") {
            sessions.revoke(call.sessionToken())
            // 期限切れの Cookie を返してブラウザ側からも消す
            call.response.cookies.append(expiredSessionCookie(config))
            call.respondSession(config, sessions)
        }

        post("password-hash") {
            // 設定済みなら運用中のサーバー。作り直せるのはログインした人だけにする
            if (config.loginConfigured && !sessions.isValid(call.sessionToken())) {
                call.respondError(HttpStatusCode.Unauthorized, "ログインが必要")
                return@post
            }

            val request = call.receiveJson(AdminPasswordHashRequest.serializer()) ?: return@post

            val password = request.password
            if (password.length !in AdminPasswordPolicy.MIN_LENGTH..AdminPasswordPolicy.MAX_LENGTH) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    "パスワードは ${AdminPasswordPolicy.MIN_LENGTH} 文字以上 ${AdminPasswordPolicy.MAX_LENGTH} 文字以下にすること",
                )
                return@post
            }

            call.respondJson(
                AdminPasswordHashResponse.serializer(),
                AdminPasswordHashResponse(
                    environmentVariable = AdminConfig.ENV_PASSWORD_HASH,
                    hash = PasswordHash.create(password).encode(),
                ),
            )
        }
    }

    // /admin だと index.html の中の相対パス（frontend.js）が / から引かれてしまう。
    // /admin/ に寄せてから配信する
    get(AdminApiPaths.BASE) {
        call.respondRedirect("${AdminApiPaths.BASE}/")
    }

    get("${AdminApiPaths.BASE}/{path...}") {
        // tailcard。Ktor が URL デコードしたあとの区切り済みの形で取れる
        val segments = call.parameters.getAll(STATIC_PATH_PARAMETER).orEmpty()

        val file =
            staticContent.read(segments)
                // 画面の中のパス（/admin/password-hash など）はファイルとして存在しない。
                // 拡張子が無いものは画面の URL とみなして index.html を返し、frontend に解釈させる
                ?: segments.lastOrNull()?.takeIf { !it.contains('.') }?.let { staticContent.readIndex() }

        if (file == null) {
            call.respondText(
                "管理画面が見つからない。:frontend のビルド成果物が resources に取り込まれていない可能性がある",
                status = HttpStatusCode.NotFound,
            )
            return@get
        }

        call.respondBytes(bytes = file.bytes, contentType = file.contentType)
    }
}

/** セッション Cookie の名前 */
internal const val ADMIN_SESSION_COOKIE: String = "admin_session"

/** 静的ファイルのパスを受ける tailcard の名前 */
private const val STATIC_PATH_PARAMETER = "path"

/**
 * Cookie の値をそのまま扱う。
 *
 * トークンは URL-safe Base64 なので、エスケープの要る文字を含まない。
 * 送るときと読むときで同じ指定にしておかないと値が変わってしまうため、
 * 定数にして 1 か所から使う。
 *
 * なお Ktor は `Set-Cookie` に `$x-enc=RAW`（復号方法の目印）も出す。
 * ブラウザは知らない属性として無視するので害は無い。
 */
private val COOKIE_ENCODING = CookieEncoding.RAW

private fun ApplicationCall.sessionToken(): String? = request.cookies[ADMIN_SESSION_COOKIE, COOKIE_ENCODING]

private fun sessionCookie(
    token: String,
    config: AdminConfig,
): Cookie =
    Cookie(
        name = ADMIN_SESSION_COOKIE,
        value = token,
        encoding = COOKIE_ENCODING,
        // 管理画面の外（ActivityPub のエンドポイント）には送られないようにする
        path = AdminApiPaths.BASE,
        // JavaScript から読めるようにする理由が無い
        httpOnly = true,
        secure = config.cookieSecure,
        maxAge = config.sessionTtl.inWholeSeconds.toInt(),
        // 他サイトからのリクエストに Cookie を乗せない（CSRF 対策）
        extensions = mapOf("SameSite" to "Strict"),
    )

private fun expiredSessionCookie(config: AdminConfig): Cookie =
    sessionCookie(token = "", config = config).copy(maxAge = 0)

private suspend fun ApplicationCall.respondSession(
    config: AdminConfig,
    sessions: AdminSessions,
) {
    respondJson(
        AdminSessionResponse.serializer(),
        AdminSessionResponse(
            authenticated = sessions.isValid(sessionToken()),
            loginConfigured = config.loginConfigured,
        ),
    )
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
) {
    respondJson(
        serializer = AdminErrorResponse.serializer(),
        value = AdminErrorResponse(message),
        status = status,
    )
}

/**
 * ボディを読む。読めなければ 400 を返して null。
 *
 * `call.receive<T>()` を使わないのは他のエンドポイントと同じ理由で、
 * リフレクションで serializer を引く実装が native-image で動かないため。
 */
private suspend fun <T> ApplicationCall.receiveJson(deserializer: DeserializationStrategy<T>): T? =
    try {
        AppJson.decodeFromString(deserializer, receiveText())
    } catch (e: SerializationException) {
        respondError(HttpStatusCode.BadRequest, "リクエストの JSON が読めない: ${e.message}")
        null
    }
