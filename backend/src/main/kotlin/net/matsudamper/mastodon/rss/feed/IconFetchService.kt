package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining

/**
 * フィードが名乗っているアイコンを取ってくる。
 *
 * 配信元の URL を画面にそのまま出すのではなく、こちらで取ってから返す。
 * ブラウザから直接引くと、配信元が CORS を許していない画像は canvas に描けず、
 * 見に来た人の閲覧先が配信元に漏れる。
 *
 * 取りに行く先は管理画面から登録したフィードが名乗った URL に限る。
 * 任意の URL を外から渡せる口にはしない。
 */
class IconFetchService(
    private val client: HttpClient = FeedFetchService.defaultClient(),
) : Closeable {
    suspend fun fetch(url: String): FetchResult {
        val target = HttpUrl.sanitize(url) ?: return FetchResult.Failure

        return runCatching {
            val response = client.get(target) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            val channel = response.bodyAsChannel()
            if (!response.status.isSuccess()) {
                channel.cancel(null)
                return FetchResult.Failure
            }

            // 画像以外は返さない。配信元が HTML のエラーページを 200 で返すことがあり、
            // そのまま流すとこちらが別のサイトの HTML を自分のドメインで配ることになる
            val contentType = response.contentType()
            if (contentType == null || contentType.contentType != IMAGE_CONTENT_TYPE) {
                channel.cancel(null)
                return FetchResult.Failure
            }

            val bytes = channel.readRemaining((MAX_BYTES + 1).toLong()).readByteArray()
            if (bytes.size > MAX_BYTES) {
                channel.cancel(null)
                return FetchResult.Failure
            }

            FetchResult.Success(bytes = bytes, contentType = contentType)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            FetchResult.Failure
        }
    }

    override fun close() {
        client.close()
    }

    sealed interface FetchResult {
        data class Success(
            val bytes: ByteArray,
            val contentType: ContentType,
        ) : FetchResult

        data object Failure : FetchResult
    }

    private companion object {
        const val USER_AGENT = "mastodon-rss/0.1"
        const val IMAGE_CONTENT_TYPE = "image"
        const val MAX_BYTES = 1024 * 1024
    }
}
