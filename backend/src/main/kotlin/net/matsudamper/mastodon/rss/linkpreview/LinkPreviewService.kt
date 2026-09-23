package net.matsudamper.mastodon.rss.linkpreview

import java.io.Closeable
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import okhttp3.Dns

/**
 * 投稿の本文にあるリンク先の OGP を取ってくる。
 *
 * 画面（ブラウザ）からは CORS に阻まれて他のサイトの HTML を読めないので、こちらで取る。
 * 取りに行く先は記録済みの投稿の本文にあるリンクだけで、画面から任意の URL は渡せない。
 */
class LinkPreviewService(
    private val client: HttpClient = defaultClient(),
    private val clock: Clock = Clock.systemUTC(),
) : Closeable {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val fetchPermits = Semaphore(MAX_CONCURRENT_FETCHES)

    /**
     * OGP を取りに行くリンク。[previews] はこの並びで返す
     */
    fun links(contentHtml: String): List<String> = OgpParser.links(contentHtml).take(MAX_LINKS_PER_NOTE)

    /**
     * 本文のリンクごとに 1 件返す。取れなかったリンクも URL だけ入れて返す
     */
    suspend fun previews(contentHtml: String): List<LinkPreview> {
        val links = links(contentHtml)
        return coroutineScope {
            links.map { url -> async { preview(url) } }.awaitAll()
        }
    }

    private suspend fun preview(url: String): LinkPreview {
        val now = clock.instant()
        val cached = cache[url]
        if (cached != null && cached.expiresAt.isAfter(now)) return cached.preview

        val ogp = fetchPermits.withPermit { fetchOgp(url = url, remainingRedirects = MAX_REDIRECTS) }
        val preview = LinkPreview(
            url = url,
            title = ogp?.title,
            siteName = ogp?.siteName,
            imageUrl = ogp?.imageUrl,
        )
        store(
            url = url,
            entry = CacheEntry(
                preview = preview,
                expiresAt = now.plus(if (ogp == null) FAILURE_TTL else SUCCESS_TTL),
            ),
            now = now,
        )
        return preview
    }

    private fun store(
        url: String,
        entry: CacheEntry,
        now: Instant,
    ) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.entries.removeIf { !it.value.expiresAt.isAfter(now) }
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        }
        cache[url] = entry
    }

    /**
     * リダイレクトは自分で辿る。飛んだ先ごとに宛先を確かめないと、
     * 外のサイトから内側のアドレスへ飛ばされたときに素通りする
     */
    private suspend fun fetchOgp(
        url: String,
        remainingRedirects: Int,
    ): OgpParser.Ogp? {
        if (!isPublicHttpUrl(url)) return null

        val response = runCatching {
            client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return null
        }

        return when {
            response.status.value in 300..399 -> {
                val location = response.headers[HttpHeaders.Location]
                response.discardBody()
                val next = location?.let { runCatching { URI(url).resolve(it).toString() }.getOrNull() }
                if (next == null || remainingRedirects <= 0) {
                    null
                } else {
                    fetchOgp(url = next, remainingRedirects = remainingRedirects - 1)
                }
            }

            !response.status.isSuccess() -> {
                response.discardBody()
                null
            }

            else -> {
                val html = runCatching { response.readHtml() }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    null
                }
                html?.let { OgpParser.parse(html = it, pageUrl = url) }
            }
        }
    }

    private suspend fun isPublicHttpUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return false
        val addresses = runCatching {
            withContext(Dispatchers.IO) { InetAddress.getAllByName(host.removeSurrounding("[", "]")).toList() }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return false
        }
        return addresses.isNotEmpty() && addresses.none { it.isInternal() }
    }

    /**
     * 先頭の [MAX_BODY_BYTES] だけ読む。OGP は `<head>` にあるので、残りは要らない
     */
    private suspend fun HttpResponse.readHtml(): String? {
        val contentType = contentType()
        if (contentType != null && !contentType.contentSubtype.contains("html", ignoreCase = true)) {
            discardBody()
            return null
        }
        val channel = bodyAsChannel()
        val bytes = channel.readRemaining(MAX_BODY_BYTES.toLong()).readByteArray()
        channel.cancel(null)

        val charset = contentType?.charset()
            ?: OgpParser.declaredCharset(bytes.toString(Charsets.ISO_8859_1))
                ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?: Charsets.UTF_8
        return bytes.toString(charset)
    }

    private suspend fun HttpResponse.discardBody() {
        bodyAsChannel().cancel(null)
    }

    override fun close() {
        client.close()
    }

    data class LinkPreview(
        val url: String,
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
    )

    private data class CacheEntry(
        val preview: LinkPreview,
        val expiresAt: Instant,
    )

    companion object {
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_LINKS_PER_NOTE = 10
        private const val MAX_CONCURRENT_FETCHES = 4
        private const val MAX_REDIRECTS = 5
        private const val MAX_BODY_BYTES = 1024 * 1024
        private const val MAX_CACHE_ENTRIES = 2000
        private val SUCCESS_TTL: Duration = Duration.ofHours(24)

        /**
         * 取れなかったものも覚えておく。覚えないと、タイムラインを開くたびに同じ相手へ取りに行く
         */
        private val FAILURE_TTL: Duration = Duration.ofHours(1)

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
                followRedirects = false
                expectSuccess = false
            }

        private fun InetAddress.isInternal(): Boolean =
            isAnyLocalAddress ||
                isLoopbackAddress ||
                isLinkLocalAddress ||
                isSiteLocalAddress ||
                isMulticastAddress ||
                // IPv6 のユニークローカル（fc00::/7）は isSiteLocalAddress に含まれない
                (this is Inet6Address && (address[0].toInt() and 0xFE) == 0xFC)
    }

    /**
     * 接続に使うアドレスをここで確かめる。
     *
     * 取得の前に名前を引いて確かめるだけだと、接続のときに引き直した結果が内側のアドレスに
     * 変わっていても素通りする（DNS rebinding）。OkHttp はここで返したアドレスにだけ接続する。
     * IP アドレスがそのまま書かれた URL はここを通らないので、[isPublicHttpUrl] で確かめる
     */
    private object PublicAddressDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.any { it.isInternal() }) throw UnknownHostException("内側のアドレスには接続しない")
            return addresses
        }
    }
}
