package net.matsudamper.mastodon.rss.frontend

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import net.matsudamper.mastodon.rss.admin.api.AdminApiPaths
import net.matsudamper.mastodon.rss.admin.api.AdminErrorResponse
import net.matsudamper.mastodon.rss.admin.api.AdminLoginRequest
import net.matsudamper.mastodon.rss.admin.api.AdminPasswordHashRequest
import net.matsudamper.mastodon.rss.admin.api.AdminPasswordHashResponse
import net.matsudamper.mastodon.rss.admin.api.AdminSessionResponse

/** API 呼び出しの結果。失敗はすべて画面に出せるメッセージに畳む */
internal sealed interface AdminResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AdminResult<T>

    data class Failure(
        val message: String,
    ) : AdminResult<Nothing>
}

/**
 * 管理 API のクライアント。
 *
 * `ContentNegotiation` は入れず serializer を明示する。backend 側と揃えているだけで、
 * こちらは native-image の制約とは関係ない。
 *
 * セッションは Cookie で持つので、この型は認証情報を持たない。
 * 同一オリジンへのリクエストにはブラウザが Cookie を付ける。
 */
internal class AdminApiClient(
    private val client: HttpClient = HttpClient(Js),
) {
    suspend fun session(): AdminResult<AdminSessionResponse> =
        request(AdminSessionResponse.serializer()) {
            client.get(AdminApiPaths.SESSION)
        }

    suspend fun login(password: String): AdminResult<AdminSessionResponse> =
        request(AdminSessionResponse.serializer()) {
            client.post(AdminApiPaths.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(encode(AdminLoginRequest.serializer(), AdminLoginRequest(password)))
            }
        }

    suspend fun logout(): AdminResult<AdminSessionResponse> =
        request(AdminSessionResponse.serializer()) {
            client.post(AdminApiPaths.LOGOUT)
        }

    suspend fun createPasswordHash(password: String): AdminResult<AdminPasswordHashResponse> =
        request(AdminPasswordHashResponse.serializer()) {
            client.post(AdminApiPaths.PASSWORD_HASH) {
                contentType(ContentType.Application.Json)
                setBody(encode(AdminPasswordHashRequest.serializer(), AdminPasswordHashRequest(password)))
            }
        }

    private suspend fun <T> request(
        deserializer: DeserializationStrategy<T>,
        block: suspend () -> HttpResponse,
    ): AdminResult<T> {
        val response =
            try {
                block()
            } catch (e: Exception) {
                // サーバーが落ちている / 通信できない。例外の型は環境依存なので広く受ける
                return AdminResult.Failure("サーバーに接続できない: ${e.message}")
            }

        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
            return AdminResult.Failure(errorMessage(response, body))
        }

        return try {
            AdminResult.Success(json.decodeFromString(deserializer, body))
        } catch (e: SerializationException) {
            AdminResult.Failure("サーバーの応答を読めない: ${e.message}")
        }
    }

    private fun errorMessage(
        response: HttpResponse,
        body: String,
    ): String =
        try {
            json.decodeFromString(AdminErrorResponse.serializer(), body).message
        } catch (e: SerializationException) {
            // 想定外の失敗。ステータスだけでも出す
            "エラー (${response.status.value}): ${e.message}"
        }

    private fun <T> encode(
        serializer: SerializationStrategy<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
