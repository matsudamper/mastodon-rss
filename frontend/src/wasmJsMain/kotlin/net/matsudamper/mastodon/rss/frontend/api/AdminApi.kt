package net.matsudamper.mastodon.rss.frontend.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 管理画面のログイン API。
 *
 * 叩き先は画面を配信しているのと同じオリジン。パスを相対で書いているのはそのためで、
 * ホスト名を持たないので設定も要らない。開発サーバー (8081) から動かす場合は、
 * webpack の devServer が `/api` を backend (8080) に転送する
 * （`webpack.config.d/dev-server-proxy.js`）。
 *
 * セッションは `HttpOnly` の Cookie なので、ここからは読めないし持ち回りもしない。
 * 同じオリジンへのリクエストにはブラウザが勝手に付ける。
 *
 * 型とパスは `:backend` の `admin/` と対になっている。両方から使える置き場
 * （`:shared`）がまだ無いので二重に持っている。作ったらそちらに寄せる。
 */
class AdminApi(
    private val client: HttpClient = HttpClient(Js),
) : AutoCloseable {
    /** いまログインしているかを聞く */
    suspend fun session(): AdminSessionResult = request { client.get(SESSION_PATH).toSessionResult() } ?: unreachable()

    /**
     * パスワードを送る。通れば Cookie が返り、以降のリクエストに付く。
     *
     * サーバーは PBKDF2 を 21 万回まわしてから返すので、応答まで一拍ある。
     * 呼ぶ側は待っている間の表示を出すこと。
     */
    suspend fun login(password: String): AdminLoginResult {
        val result =
            request {
                val response =
                    client.post(LOGIN_PATH) {
                        contentType(ContentType.Application.Json)
                        setBody(AdminJson.encodeToString(AdminLoginRequest.serializer(), AdminLoginRequest(password)))
                    }

                when (response.status) {
                    HttpStatusCode.OK -> AdminLoginResult.Success

                    HttpStatusCode.Unauthorized -> AdminLoginResult.WrongPassword

                    // サーバーに ADMIN_PASSWORD_HASH が入っていない。パスワードの問題ではない
                    HttpStatusCode.ServiceUnavailable -> AdminLoginResult.NotConfigured

                    else -> AdminLoginResult.Failure(response.errorMessage())
                }
            }

        return result ?: AdminLoginResult.Failure(UNREACHABLE_MESSAGE)
    }

    /** ログアウトする。サーバー側のセッションも消える */
    suspend fun logout(): AdminSessionResult = request { client.post(LOGOUT_PATH).toSessionResult() } ?: unreachable()

    override fun close() {
        client.close()
    }

    /**
     * 繋がらなかったときに null を返す。
     *
     * 例外をそのまま投げると画面が消える。ここで拾えるのは大抵
     * サーバーが落ちているか経路が切れている場合で、どちらも画面に
     * 出して再試行できる形にした方がよい。
     */
    private suspend fun <T> request(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

    private suspend fun HttpResponse.toSessionResult(): AdminSessionResult {
        if (status != HttpStatusCode.OK) return AdminSessionResult.Failure(errorMessage())

        val body =
            runCatching { AdminJson.decodeFromString(AdminSessionResponse.serializer(), bodyAsText()) }
                .getOrElse { return AdminSessionResult.Failure("サーバーの応答を読めなかった") }

        return AdminSessionResult.Success(
            loggedIn = body.loggedIn,
            passwordConfigured = body.passwordConfigured,
        )
    }

    /** サーバーが理由を書いていればそれを、書いていなければステータスを出す */
    private suspend fun HttpResponse.errorMessage(): String =
        runCatching { AdminJson.decodeFromString(AdminErrorResponse.serializer(), bodyAsText()).message }
            .getOrElse { "サーバーが $status を返した" }

    private fun unreachable(): AdminSessionResult = AdminSessionResult.Failure(UNREACHABLE_MESSAGE)

    private companion object {
        const val SESSION_PATH = "/api/admin/session"
        const val LOGIN_PATH = "/api/admin/login"
        const val LOGOUT_PATH = "/api/admin/logout"

        const val UNREACHABLE_MESSAGE = "サーバーに繋がらなかった"
    }
}

/**
 * ログイン API の本文を読み書きする [Json]。
 *
 * 知らないキーで落とさない。サーバー側に項目が増えても画面は動き続ける。
 */
private val AdminJson: Json =
    Json {
        ignoreUnknownKeys = true
    }

/** [AdminApi.session] と [AdminApi.logout] の結果 */
sealed interface AdminSessionResult {
    /**
     * 状態を取れた。
     *
     * @param passwordConfigured サーバーに `ADMIN_PASSWORD_HASH` が入っているか。
     *   入っていなければログインする手段が無いので、画面には設定方法を出す
     */
    data class Success(
        val loggedIn: Boolean,
        val passwordConfigured: Boolean,
    ) : AdminSessionResult

    /** 状態が分からなかった。ログインしているともしていないとも言えない */
    data class Failure(
        val message: String,
    ) : AdminSessionResult
}

/** [AdminApi.login] の結果 */
sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    /** パスワードが違う。入力を直せば通る */
    data object WrongPassword : AdminLoginResult

    /** サーバーに `ADMIN_PASSWORD_HASH` が無い。入力を直しても通らない */
    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}

/** `:backend` の `admin/AdminApiModels.kt` と同じ形 */
@Serializable
private data class AdminLoginRequest(
    val password: String,
)

@Serializable
private data class AdminSessionResponse(
    val loggedIn: Boolean,
    val passwordConfigured: Boolean,
)

@Serializable
private data class AdminErrorResponse(
    val message: String,
)
