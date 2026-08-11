package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.ApplicationResponse
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.json.respondJson

/** 管理画面のログイン API のパス。`:frontend` の `api/AdminApi.kt` と対になっている */
object AdminApiPaths {
    /** ログインの状態を見る */
    const val SESSION: String = "/api/admin/session"

    /** パスワードを送ってログインする */
    const val LOGIN: String = "/api/admin/login"

    /** ログアウトする */
    const val LOGOUT: String = "/api/admin/logout"
}

/**
 * 管理画面のログイン。
 *
 * `/admin` そのものは静的ファイルの配信に落ちる。SPA なので画面のパスは全部
 * `index.html` から始まり、ログインしているかどうかは画面が開いた後に
 * [AdminApiPaths.SESSION] を叩いて決める。サーバー側で `/admin` を弾いても、
 * 中身は `index.html` にしか無いので守るものが無い。守る対象は管理 API の方で、
 * そちらは Phase 8 の GraphQL を作るときに [AdminSessions] で見る。
 *
 * 総当たり対策（試行回数の制限）はまだ無い。Phase 7 で入れる。
 *
 * @param passwordHash `ADMIN_PASSWORD_HASH` から読んだもの。未設定なら null で、
 *   この場合はログインできない。起動自体は通す（設定前でも画面は開けるようにする）
 * @param cookieSecure セッション Cookie に `Secure` を付けるか。
 *   付けると http では Cookie が保存されないので、手元で試すときだけ外す
 */
fun Route.adminRoutes(
    passwordHash: PasswordHash?,
    sessions: AdminSessions,
    cookieSecure: Boolean,
) {
    get(AdminApiPaths.SESSION) {
        call.respondSession(
            loggedIn = sessions.isValid(call.sessionToken()),
            passwordConfigured = passwordHash != null,
        )
    }

    post(AdminApiPaths.LOGIN) {
        val body =
            runCatching { AppJson.decodeFromString(AdminLoginRequest.serializer(), call.receiveText()) }
                .getOrElse {
                    call.respondError(HttpStatusCode.BadRequest, "リクエストの形式が違う")
                    return@post
                }

        if (passwordHash == null) {
            // 設定していないだけなので、パスワードが違うのとは分けて返す。
            // 同じ 401 にすると、何を直せばよいのか画面からは分からない
            call.respondError(
                HttpStatusCode.ServiceUnavailable,
                "ADMIN_PASSWORD_HASH が未設定なのでログインできない",
            )
            return@post
        }

        // PBKDF2 を 21 万回まわす。そのまま実行すると、その間このスレッドが
        // 他のリクエストを処理できない。CIO はスレッド数が少ないので詰まる
        val matched = withContext(Dispatchers.IO) { passwordHash.matches(body.password) }

        if (!matched) {
            call.respondError(HttpStatusCode.Unauthorized, "パスワードが違う")
            return@post
        }

        call.response.appendSessionCookie(
            token = sessions.create(),
            maxAgeSeconds = sessions.ttlSeconds,
            secure = cookieSecure,
        )
        call.respondSession(loggedIn = true, passwordConfigured = true)
    }

    post(AdminApiPaths.LOGOUT) {
        // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
        sessions.remove(call.sessionToken())

        call.response.expireSessionCookie(secure = cookieSecure)
        call.respondSession(loggedIn = false, passwordConfigured = passwordHash != null)
    }
}

/**
 * Cookie の値をそのまま入れる。
 *
 * トークンは URL-safe Base64 で、Cookie の区切りに使われる文字が出ないので
 * エンコードするものが無い。Ktor の既定は URI エンコードで、読み書きが揃っていれば
 * 通るものの、値を見たときに元のトークンと違って見えることになる。
 *
 * どちらにしても Ktor は自分で復号するために `$x-enc` という独自の属性を
 * Set-Cookie に付ける。ブラウザは知らない属性を捨てるので害は無い。
 */
private val COOKIE_ENCODING = CookieEncoding.RAW

/** リクエストが持っているセッションのトークン。無ければ null */
private fun ApplicationCall.sessionToken(): String? = request.cookies[AdminSessions.COOKIE_NAME, COOKIE_ENCODING]

/**
 * セッション Cookie を返す。
 *
 * `HttpOnly` を付けるので画面の JavaScript からは読めない。読む必要が無く、
 * 読めるようにすると管理画面に script を差し込まれたときに持ち出される。
 *
 * `SameSite=Strict` にしているのは、他所のページから管理 API を叩かれても
 * Cookie が付かないようにするため。管理画面は自分のページの中からしか
 * API を叩かないので、これで困る操作は無い。
 */
private fun ApplicationResponse.appendSessionCookie(
    token: String,
    maxAgeSeconds: Long,
    secure: Boolean,
) {
    cookies.append(
        name = AdminSessions.COOKIE_NAME,
        value = token,
        encoding = COOKIE_ENCODING,
        maxAge = maxAgeSeconds,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

/**
 * セッション Cookie を消す。
 *
 * 消すのも「過去の期限を付けた同じ Cookie を返す」ことでしかないので、
 * `Path` と `Secure` は発行したときと揃える。`Path` がずれるとブラウザは
 * 別の Cookie として扱って元のものが残り、`Secure` が付いたままだと
 * http で開いているときに丸ごと無視される。
 */
private fun ApplicationResponse.expireSessionCookie(secure: Boolean) {
    cookies.append(
        name = AdminSessions.COOKIE_NAME,
        value = "",
        encoding = COOKIE_ENCODING,
        // 1970-01-01。過ぎている期限を渡すのがブラウザに消させる唯一の手段
        expires = GMTDate.START,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

private suspend fun ApplicationCall.respondSession(
    loggedIn: Boolean,
    passwordConfigured: Boolean,
) {
    respondJson(
        AdminSessionResponse.serializer(),
        AdminSessionResponse(loggedIn = loggedIn, passwordConfigured = passwordConfigured),
    )
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
) {
    respondJson(AdminErrorResponse.serializer(), AdminErrorResponse(message), status = status)
}
