package net.matsudamper.mastodon.rss.admin

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

// セッションを Cookie で持ち回る部分。
//
// GraphQL は HTTP を知らないので、データフェッチャーから
// [ApplicationCall] を通してここを呼ぶ形になる。

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
fun ApplicationCall.sessionToken(): String? = request.cookies[AdminSessions.COOKIE_NAME, COOKIE_ENCODING]

/**
 * セッション Cookie を返す。
 *
 * `HttpOnly` を付けるので画面の JavaScript からは読めない。読む必要が無く、
 * 読めるようにすると管理画面に script を差し込まれたときに持ち出される。
 *
 * `SameSite=Strict` にしているのは、他所のページから管理 API を叩かれても
 * Cookie が付かないようにするため。管理画面は自分のページの中からしか
 * API を叩かないので、これで困る操作は無い。
 *
 * @param secure `Secure` を付けるか。http では Cookie が保存されなくなるので、
 *   手元で試すときだけ外す（`ADMIN_COOKIE_SECURE`）
 */
fun ApplicationCall.appendSessionCookie(
    token: String,
    maxAgeSeconds: Long,
    secure: Boolean,
) {
    response.cookies.append(
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
fun ApplicationCall.expireSessionCookie(secure: Boolean) {
    response.cookies.append(
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
