package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val resolveTimeout: Duration = DEFAULT_RESOLVE_TIMEOUT,
) : Closeable {
    /**
     * 名前を引く待ちを呼び出し側から切り離すための場所。
     *
     * 引き終わらないものが残っても、呼び出し側は待たずに戻る
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        return FetchResult.Success(
            bytes = bytes,
            contentType = contentType,
            freshFor = cacheControlMaxAge(),
        )
    }

    /**
     * 配信元が言う「取り直さなくてよい時間」。言っていなければ null。
     *
     * `no-store` と `no-cache` は毎回取り直せという意味なので 0 にする。
     * 長い側は [MAX_FRESH_FOR] で切る。桁の大きい値をそのまま足すと期限の計算が
     * 溢れるうえ、事実上取り直さなくなる
     */
    private fun HttpResponse.cacheControlMaxAge(): Duration? {
        // カンマで区切って 1 つずつ丸ごと見る。ヘッダの文字列全体から探すと、
        // 共有キャッシュ向けの s-maxage や、名前の一部が同じ別の指示を拾いうる
        val directives = headers[HttpHeaders.CacheControl]
            ?.lowercase()
            ?.split(',')
            ?.map { it.trim() }
            ?: return null

        if (directives.any { it == "no-store" || it == "no-cache" }) return Duration.ZERO

        val seconds = directives
            .firstNotNullOfOrNull { MAX_AGE.matchEntire(it)?.groupValues?.get(1) }
            ?.toLongOrNull()
            ?: return null

        return Duration.ofSeconds(seconds).coerceAtMost(MAX_FRESH_FOR)
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
    private suspend fun isInternalTarget(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return true
        val addresses = resolve(host) ?: return true
        if (addresses.isEmpty()) return true

        return addresses.any { address ->
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress ||
                address.isUniqueLocalAddress() ||
                address.isSharedAddressSpace()
        }
    }

    /**
     * 名前を引く。引けなければ null。
     *
     * 引くのは呼び出し側から切り離した所に投げて、[resolveTimeout] だけ待つ。
     * 名前を引く呼び出しは途中で止められないので、同じ所で待つと
     * [HttpTimeout] の待ち時間に関係なく、応答しない名前 1 つで取り込みが止まる。
     * 待つのを IO の側で行うのは、待ち時間を実際の時計で計るため
     */
    private suspend fun resolve(host: String): List<InetAddress>? {
        val resolving = resolveScope.async { runCatching { resolveAddresses(host) }.getOrNull() }

        val resolved = withContext(Dispatchers.IO) {
            withTimeoutOrNull(resolveTimeout.toMillis()) { resolving.await() }
        }
        if (resolved == null) resolving.cancel()

        return resolved
    }

    /**
     * 事業者やクラスタの内側で使う `100.64.0.0/10`。
     *
     * RFC 1918 の範囲ではないので [InetAddress.isSiteLocalAddress] では拾えないが、
     * Kubernetes などがここを内部のアドレスに使う
     */
    private fun InetAddress.isSharedAddressSpace(): Boolean {
        if (this !is Inet4Address) return false
        val bytes = address
        return bytes[0].toInt() and 0xFF == 100 && (bytes[1].toInt() and 0xC0) == 0x40
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
        resolveScope.cancel()
        client.close()
    }

    sealed interface FetchResult {
        /**
         * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
         */
        data class Success(
            val bytes: ByteArray,
            val contentType: ContentType,
            val freshFor: Duration?,
        ) : FetchResult

        data object Failure : FetchResult
    }

    companion object {
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_BYTES = 1024 * 1024
        private const val MAX_HOPS = 4
        private val REDIRECT_STATUS_RANGE = 300..399
        private val MAX_AGE = Regex("max-age\\s*=\\s*(\\d+)")
        private val MAX_FRESH_FOR: Duration = Duration.ofDays(1)
        private val DEFAULT_RESOLVE_TIMEOUT: Duration = Duration.ofSeconds(5)

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
