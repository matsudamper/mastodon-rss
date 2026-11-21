package net.matsudamper.mastodon.rss.image

import java.io.Closeable
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import net.matsudamper.mastodon.rss.feed.HttpUrl
import okhttp3.Dns
import org.slf4j.LoggerFactory

/**
 * 相手が名乗った画像を取ってくる。フィードのアイコンとヘッダー、フォロワーのアイコン、
 * リンク先の OGP 画像に使う。
 *
 * 配信元の URL を画面にそのまま出すのではなく、こちらで取ってから返す。
 * ブラウザから直接引くと、配信元が CORS を許していない画像は canvas に描けず、
 * 見に来た人の閲覧先が配信元に漏れる。
 *
 * 取りに行く先は配信元が書いた URL で、こちらの管理者が決めた値ではない。
 * 取得は無認証のエンドポイントから呼ばれるので、相手が書いた URL で
 * こちらのネットワークの内側を叩けないようにし、画像でないものは返さない。
 */
class RemoteImageFetchService(
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

    private val logger = LoggerFactory.getLogger(RemoteImageFetchService::class.java)

    /**
     * 取ってくる。取れなければ [FetchResult.Failure]。
     *
     * リダイレクトは自分で辿る。クライアントに任せると、飛んだ先が内側を
     * 指していても検査を通さずに繋いでしまう。
     *
     * @param maxFreshFor 配信元が言う「取り直さなくてよい時間」の上限。呼び出し側ごとに
     *   持たせてよい長さが違うので既定値を上書きできるようにする
     */
    suspend fun fetch(
        url: String,
        maxFreshFor: Duration = DEFAULT_MAX_FRESH_FOR,
    ): FetchResult {
        var target = HttpUrl.sanitize(url) ?: return FetchResult.Failure

        return runCatching {
            repeat(MAX_HOPS) {
                if (isInternalTarget(target)) return FetchResult.Failure

                val response = client.get(target) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    // 圧縮されたまま置くと、返すときに Content-Encoding を付けないので
                    // 見に来た側は画像として読めない。こちらでは復号しない
                    header(HttpHeaders.AcceptEncoding, IDENTITY_ENCODING)
                }

                val redirected = response.redirectLocation()
                if (redirected != null) {
                    response.discardBody()
                    target = HttpUrl.sanitize(redirected, target) ?: return FetchResult.Failure
                    return@repeat
                }

                return response.toResult(maxFreshFor)
            }
            FetchResult.Failure
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            FetchResult.Failure
        }
    }

    /**
     * [fetch] と同じ確かめ方で取り、本文は丸ごと読まずに [relay] へ渡す。
     * 取れなければ [relay] を呼ばずに null。
     *
     * 本文は [relay] の中でだけ読める。[relay] が投げたものはそのまま投げる。
     * 応答を書き始めた後なので、取れなかったことにはできない
     */
    suspend fun <T> relay(
        url: String,
        maxFreshFor: Duration = DEFAULT_MAX_FRESH_FOR,
        relay: suspend (RelayedImage) -> T,
    ): T? {
        var target = HttpUrl.sanitize(url) ?: return null
        var relayed: Result<T>? = null

        runCatching {
            repeat(MAX_HOPS) {
                if (isInternalTarget(target)) return null

                val redirected = client.prepareGet(target) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.AcceptEncoding, IDENTITY_ENCODING)
                }.execute { response ->
                    val redirected = response.redirectLocation()
                    if (redirected == null) {
                        val image = response.toRelayedImage(maxFreshFor)
                        if (image != null) relayed = runCatching { relay(image) }
                    }
                    redirected
                } ?: return@runCatching

                target = HttpUrl.sanitize(redirected, target) ?: return null
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }

        return relayed?.getOrThrow()
    }

    private suspend fun HttpResponse.toRelayedImage(maxFreshFor: Duration): RelayedImage? {
        val channel = bodyAsChannel()
        val imageType = acceptedImageType() ?: return null

        val contentLength = contentLength()
        if (contentLength != null && contentLength > MAX_BYTES) {
            logger.warn("画像が大きすぎる: host={}, 上限={} バイト", request.url.host, MAX_BYTES)
            return null
        }

        val head = channel.readHead(SIGNATURE_BYTES)
        if (!imageType.matches(head)) {
            logger.warn("名乗った種類と中身が違う: host={}, 名乗り={}", request.url.host, imageType.contentType)
            return null
        }

        return RelayedImage(
            imageType = imageType,
            freshFor = cacheControlMaxAge(maxFreshFor),
            contentLength = contentLength,
            head = head,
            rest = channel,
        )
    }

    private suspend fun ByteReadChannel.readHead(size: Int): ByteArray {
        val head = ByteArray(size)
        var filled = 0
        while (filled < size) {
            val read = readAvailable(head, filled, size - filled)
            if (read < 0) break
            filled += read
        }
        return head.copyOf(filled)
    }

    /**
     * 応答の見出しから、受ける画像の種類を決める。受けないものなら null
     */
    private fun HttpResponse.acceptedImageType(): RemoteImageType? {
        if (!status.isSuccess()) return null

        // 頼んでいないのに圧縮して返してくる配信元は受けない。中身をそのまま置くので、
        // 圧縮されていると画像として読めないものを配ることになる
        val encoding = headers[HttpHeaders.ContentEncoding]?.trim()?.lowercase()
        if (encoding != null && encoding != IDENTITY_ENCODING) return null

        return contentType()?.let { RemoteImageType.of(it) }
    }

    private suspend fun HttpResponse.toResult(maxFreshFor: Duration): FetchResult {
        val channel = bodyAsChannel()
        val imageType = acceptedImageType()
        if (imageType == null) {
            channel.cancel(null)
            return FetchResult.Failure
        }

        val bytes = channel.readRemaining((MAX_BYTES + 1).toLong()).readByteArray()
        if (bytes.size > MAX_BYTES) {
            // 出さないと、画像が出ない理由が外から分からない。
            // URL の残りはクエリに購読者だけが知るトークンを含むことがあるのでホストだけ出す
            logger.warn("画像が大きすぎる: host={}, 上限={} バイト", request.url.host, MAX_BYTES)
            channel.cancel(null)
            return FetchResult.Failure
        }

        // 名乗った種類と中身が食い違うものは受けない。名乗りだけを信じると、
        // 画像でないものをこちらのドメインから画像として配ることになる
        if (!imageType.matches(bytes)) {
            logger.warn("名乗った種類と中身が違う: host={}, 名乗り={}", request.url.host, imageType.contentType)
            return FetchResult.Failure
        }

        return FetchResult.Success(
            bytes = bytes,
            imageType = imageType,
            freshFor = cacheControlMaxAge(maxFreshFor),
        )
    }

    /**
     * 配信元が言う「取り直さなくてよい時間」。言っていなければ null。
     *
     * `no-store` と `no-cache` は毎回取り直せという意味なので 0 にする。
     * 長い側は [maxFreshFor] で切る。桁の大きい値をそのまま足すと期限の計算が
     * 溢れるうえ、事実上取り直さなくなる
     */
    private fun HttpResponse.cacheControlMaxAge(maxFreshFor: Duration): Duration? {
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

        return seconds.seconds.coerceAtMost(maxFreshFor)
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
     * 名前を引いてから繋ぐまでの間に引き直されると別のアドレスになりうるので、
     * [defaultClient] は繋ぐときにも [PublicAddressDns] で確かめる。
     */
    private suspend fun isInternalTarget(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return true
        val addresses = resolve(host) ?: return true
        if (addresses.isEmpty()) return true

        return addresses.any { it.isInternal() }
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
            withTimeoutOrNull(resolveTimeout) { resolving.await() }
        }
        if (resolved == null) resolving.cancel()

        return resolved
    }

    override fun close() {
        resolveScope.cancel()
        client.close()
    }

    /**
     * 中継する画像。本文は相手から読みながら流す
     *
     * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
     * @param contentLength 相手が名乗った大きさ。名乗っていなければ null
     */
    class RelayedImage(
        val imageType: RemoteImageType,
        val freshFor: Duration?,
        val contentLength: Long?,
        private val head: ByteArray,
        private val rest: ByteReadChannel,
    ) {
        /**
         * 上限を超えたら [IOException] で打ち切る。応答を書き始めた後なので、接続を切るしかない
         */
        suspend fun copyTo(output: ByteWriteChannel) {
            output.writeFully(head)
            var total = head.size.toLong()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = rest.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) throw IOException("画像が上限 $MAX_BYTES バイトを超えた")
                output.writeFully(buffer, startIndex = 0, endIndex = read)
            }
            output.flush()
        }
    }

    sealed interface FetchResult {
        /**
         * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
         */
        data class Success(
            val bytes: ByteArray,
            val imageType: RemoteImageType,
            val freshFor: Duration?,
        ) : FetchResult

        data object Failure : FetchResult
    }

    companion object {
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val IDENTITY_ENCODING = "identity"
        private const val MAX_BYTES = 50 * 1024 * 1024
        private const val MAX_HOPS = 4

        /**
         * [RemoteImageType.matches] が見る先頭のバイト数
         */
        private const val SIGNATURE_BYTES = 12
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private val REDIRECT_STATUS_RANGE = 300..399
        private val MAX_AGE = Regex("max-age\\s*=\\s*(\\d+)")

        /**
         * 呼び出し側が [fetch] で上書きしなかったときに使う上限。
         *
         * アイコン・ヘッダーはこの既定値のまま使う
         */
        private val DEFAULT_MAX_FRESH_FOR: Duration = 1.days
        private val DEFAULT_RESOLVE_TIMEOUT: Duration = 5.seconds

        /**
         * リダイレクトを自分で辿るので、クライアントには追わせない。
         *
         * 待ち時間はフィードの取得より短くする。1 本ずつが画面の表示を待たせるうえ、
         * 外から何度でも呼べる
         */
        fun defaultClient(): HttpClient =
            HttpClient(OkHttp) {
                engine {
                    config {
                        dns(PublicAddressDns)
                        // プロキシを通すと、名前を引くのがプロキシの側になって PublicAddressDns を通らない
                        proxy(Proxy.NO_PROXY)
                        followRedirects(false)
                        followSslRedirects(false)
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 10_000
                }

                expectSuccess = false
                followRedirects = false
            }

        private fun InetAddress.isInternal(): Boolean =
            isAnyLocalAddress ||
                isLoopbackAddress ||
                isLinkLocalAddress ||
                isSiteLocalAddress ||
                isMulticastAddress ||
                isUniqueLocalAddress() ||
                isSharedAddressSpace()

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
    }

    /**
     * 接続に使うアドレスをここで確かめる。
     *
     * [isInternalTarget] で確かめた後に引き直した結果が内側のアドレスに変わっていても
     * （DNS rebinding）、OkHttp はここで返したアドレスにだけ接続するので内側には繋がらない。
     * IP アドレスがそのまま書かれた URL はここを通らないので、[isInternalTarget] で確かめる
     */
    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.any { it.isInternal() }) throw UnknownHostException("内側のアドレスには接続しない")
            return addresses
        }
    }
}
