package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
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
 * 取りに行く先はフィードの XML に配信元が書いた URL で、こちらの管理者が
 * 決めた値ではない。取得は無認証のエンドポイントから呼ばれるので、
 * 相手が書いた URL でこちらのネットワークの内側を叩けないようにする。
 */
class IconFetchService(
    private val client: HttpClient = defaultClient(),
    private val resolveAddresses: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) : Closeable {
    /**
     * 取ってくる。取れなければ [FetchResult.Failure]。
     *
     * リダイレクトは自分で辿る。クライアントに任せると、飛んだ先が内側を
     * 指していても検査を通さずに繋いでしまう。
     */
    suspend fun fetch(url: String): FetchResult {
        var target = HttpUrl.sanitize(url) ?: return FetchResult.Failure

        return runCatching {
            repeat(MAX_HOPS) {
                if (isInternalTarget(target)) return FetchResult.Failure

                val response = client.get(target) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }

                val redirected = response.redirectLocation()
                if (redirected != null) {
                    response.discardBody()
                    target = HttpUrl.sanitize(redirected, target) ?: return FetchResult.Failure
                    return@repeat
                }

                return response.toResult()
            }
            FetchResult.Failure
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            FetchResult.Failure
        }
    }

    private suspend fun HttpResponse.toResult(): FetchResult {
        val channel = bodyAsChannel()
        if (!status.isSuccess()) {
            channel.cancel(null)
            return FetchResult.Failure
        }

        // 復号できる形式だけを返す。image/svg+xml はスクリプトを実行できるので、
        // 配信元の書いたものをこちらのドメインから配ると同一オリジンで動いてしまう
        val contentType = contentType()?.withoutParameters()
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            channel.cancel(null)
            return FetchResult.Failure
        }

        val bytes = channel.readRemaining((MAX_BYTES + 1).toLong()).readByteArray()
        if (bytes.size > MAX_BYTES) {
            channel.cancel(null)
            return FetchResult.Failure
        }

        return FetchResult.Success(bytes = bytes, contentType = contentType)
    }

    private fun HttpResponse.redirectLocation(): String? {
        if (status.value !in REDIRECT_STATUS_RANGE) return null
        return headers[HttpHeaders.Location]?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun HttpResponse.discardBody() {
        bodyAsChannel().cancel(null)
    }

    /**
     * 繋ぐ前に、その名前が指す先がこちらのネットワークの内側かどうかを見る。
     *
     * 外から `/users/{name}/icon` を叩くだけで内側へ HTTP を飛ばせる状態
     * （SSRF）を塞ぐ。名前が複数のアドレスを持つ場合は 1 つでも内側なら弾く。
     * 引けない名前も弾く。
     *
     * 名前を引いてから繋ぐまでの間に引き直されると別のアドレスになりうるが、
     * そこまでは見ない。
     */
    private fun isInternalTarget(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return true
        val addresses = runCatching { resolveAddresses(host) }.getOrNull() ?: return true
        if (addresses.isEmpty()) return true

        return addresses.any { address ->
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress ||
                address.isUniqueLocalAddress()
        }
    }

    /**
     * IPv6 のユニークローカルアドレス（`fc00::/7`）。
     *
     * 組織内で使う範囲で、[InetAddress.isSiteLocalAddress] では拾えない
     */
    private fun InetAddress.isUniqueLocalAddress(): Boolean {
        if (this !is Inet6Address) return false
        return address.first().toInt() and 0xFE == 0xFC
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

    companion object {
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_BYTES = 1024 * 1024
        private const val MAX_HOPS = 4
        private val REDIRECT_STATUS_RANGE = 300..399

        private val ALLOWED_CONTENT_TYPES = setOf(
            ContentType.Image.PNG,
            ContentType.Image.JPEG,
            ContentType.Image.GIF,
            ContentType("image", "webp"),
        )

        /**
         * リダイレクトを自分で辿るので、クライアントには追わせない。
         *
         * 待ち時間はフィードの取得より短くする。1 本ずつが画面の表示を待たせるうえ、
         * 外から何度でも呼べる
         */
        fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 10_000
                }

                expectSuccess = false
                followRedirects = false
            }
    }
}
