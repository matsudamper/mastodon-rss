package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.channelIdFromPageHtml
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.resolve

class FeedFetchService(
    private val client: HttpClient = defaultClient(),
    private val externalHosts: ExternalHosts = InternalHosts,
) : Closeable {
    /**
     * 記事のリンク先を取る口。飛ばされた先を自分で見るために、client には追わせない
     */
    private val pageClient: HttpClient = client.config { followRedirects = false }

    suspend fun fetch(url: String): FetchResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return FetchResult.InvalidUrl

        return runCatching {
            // 解決も同じ中に置く。/@handle のような形はここで YouTube のページを
            // 取りに行くので、外に出すと DNS の失敗やタイムアウトが素通りする
            val resolvedUrl = resolveFeedUrl(trimmed) ?: return FetchResult.InvalidUrl

            val response = client.get(resolvedUrl) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            if (!response.status.isSuccess()) {
                response.discardBody()
                return FetchResult.HttpError(response.status.value)
            }

            // 配信元が /feed から /feed/ へ、http から https へ飛ばすのは普通にある。
            // 保存するのは飛んだ先の URL で、次からはそこを直接取りに行く
            val finalUrl = response.request.url.normalize()
            val bytes = response.readBodyUpTo(MAX_BODY_BYTES) ?: return FetchResult.TooLarge

            val parsed = FeedParser.parse(bytes)
            FetchResult.Success(
                requestedUrl = trimmed,
                feedUrl = finalUrl,
                parsed = parsed,
            )
        }.getOrElse { error ->
            when (error) {
                // runCatching は Throwable を拾うので、呼び出し元が消えた合図まで
                // 取得の失敗に化ける。化けると保存まで進んでしまう
                is CancellationException -> throw error

                is FeedParseException -> FetchResult.ParseError(error.message ?: "パースに失敗した")

                else -> FetchResult.HttpError(message = error.message ?: "取得に失敗した")
            }
        }
    }

    /**
     * 記事のリンク先のページを取って `og:image` を返す。
     *
     * 取れなければ null を返して、記事の取り込みはそのまま続ける。画像は投稿の
     * 飾りなので、配信元のページが落ちているだけで記事を落とすほうが困る。
     *
     * 打ち切りをフィードより短くするのは、記事の数だけ繰り返すため。1 本が
     * 黙り込んだだけで取り込み全体が待たされる。
     *
     * @return 絶対化した http / https の URL。見つからなければ null
     */
    suspend fun fetchOpenGraphImageUrl(url: String): String? =
        withTimeoutOrNull(PAGE_TIMEOUT_MILLIS) {
            runCatching { loadOpenGraphImageUrl(url) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    null
                }
        }

    /**
     * リンク先はフィードの配信元が自由に書けるので、取りに行く前に
     * [externalHosts] で内部向けかどうかを見る。飛ばされた先も同じなので、
     * リダイレクトは client に任せず自分で辿って毎回見る。
     */
    private suspend fun loadOpenGraphImageUrl(url: String): String? {
        var target = HttpUrl.sanitize(url) ?: return null

        repeat(MAX_PAGE_REDIRECTS + 1) {
            if (!externalHosts.isExternal(target)) return null

            val response = pageClient.get(target) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                // OGP は HTML にしか無い。PDF や画像を指す link を取りに行かないよう先に伝える
                header(HttpHeaders.Accept, "text/html;q=1.0, application/xhtml+xml;q=0.9, */*;q=0.1")
            }

            val location = response.headers[HttpHeaders.Location]
            if (response.status.value in 300..399 && location != null) {
                response.discardBody()
                target = HttpUrl.sanitize(location, target) ?: return null
                return@repeat
            }

            if (!response.status.isSuccess() || !response.isHtml()) {
                response.discardBody()
                return null
            }

            val bytes = response.readBodyUpTo(MAX_PAGE_BYTES) ?: return null
            val imageUrl = OpenGraph.imageUrl(bytes.decodeToString()) ?: return null

            // og:image は相対 URL でもよい。基準は飛んだ先のページ
            return HttpUrl.sanitize(imageUrl, target)
        }

        return null
    }

    /**
     * HTML として読める応答か。XHTML を配るページがあるので両方を通す
     */
    private fun HttpResponse.isHtml(): Boolean {
        val type = contentType() ?: return false
        return type.match(ContentType.Text.Html) || type.match(XHTML)
    }

    private suspend fun resolveFeedUrl(url: String): String? {
        // YouTube はスキームの無い形も受けるので、先に解決してから確かめる。
        // 逆にすると YouTubeFeedResolver が対応している形を弾いてしまう。
        // ページを引く先は YouTubeFeedResolver が組み立てた YouTube の URL で、
        // 入力をそのまま取りに行くわけではない
        val resolved = when (val source = resolve(url)) {
            null -> url

            is YouTubeFeedSource.Feed -> source.url

            is YouTubeFeedSource.NeedsPageLookup -> {
                val response = client.get(source.pageUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (!response.status.isSuccess()) {
                    response.discardBody()
                    return null
                }

                val html = response.readBodyUpTo(MAX_PAGE_BYTES)?.decodeToString() ?: return null
                val channelId = channelIdFromPageHtml(html) ?: return null
                YouTubeFeedResolver.feedUrlForChannel(channelId) ?: return null
            }
        }

        val parsed = runCatching { URI(resolved) }.getOrNull() ?: return null
        // スキームは大文字小文字を区別しない。貼り付けた URL が HTTPS でも通す
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (parsed.host.isNullOrBlank()) return null

        return resolved
    }

    /**
     * 上限を超えたら null を返す。超えた時点で読むのをやめるので、
     * 大きすぎる応答を最後まで受け取らない。
     *
     * 途中でやめた場合は残りを読む相手がいなくなるので、channel を閉じて
     * 接続を返す。閉じないと繰り返すうちに接続が尽きる
     */
    private suspend fun HttpResponse.readBodyUpTo(limit: Int): ByteArray? {
        val channel = bodyAsChannel()
        val bytes = channel.readRemaining((limit + 1).toLong()).readByteArray()
        if (bytes.size > limit) {
            channel.cancel(null)
            return null
        }
        return bytes
    }

    /**
     * 保存と重複の判定に使う形に揃える。
     *
     * 同じリソースを指す綴りの違いをここで吸収しないと、`findByUrl` の
     * 完全一致をすり抜けて同じフィードを何本も登録できてしまう。
     *
     * - フラグメントは取得先に送られず、同じリソースを指す
     * - ホスト名は大文字小文字を区別しない
     * - ホスト名の末尾の `.` はルートラベルで、付けても同じホストを指す
     * - パスの `%XX` は、中身が予約文字でなければ書かないのと同じ意味になる。
     *   予約文字で残す分も、16 進数の綴りでは意味が変わらない
     *
     * パスの大文字小文字と末尾の形は意味を持つので触らない
     */
    private fun Url.normalize(): String =
        URLBuilder(this)
            .apply {
                fragment = ""
                host = host.lowercase().trimEnd('.')
                encodedPathSegments = encodedPathSegments.map { it.normalizePercentEncoding() }
            }
            .buildString()

    /**
     * 予約文字でない文字の `%XX` を元に戻し、残った `%XX` は大文字に揃える。
     *
     * 予約文字は書き方で意味が変わる（`%2F` はパスの区切りではない）ので戻さない。
     * ただし `%2F` と `%2f` は同じ文字を指すので、綴りだけ揃える。
     * 多バイト文字の `%XX` は 0x80 以上で予約文字でもないため、戻す方には入らない
     */
    private fun String.normalizePercentEncoding(): String =
        PERCENT_ENCODED.replace(this) { match ->
            val decoded = match.value.substring(1).toInt(16).toChar()
            if (decoded.isUnreserved()) decoded.toString() else match.value.uppercase()
        }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

    /**
     * 読まずに捨てる。読む相手がいないまま置くと接続が返らず、
     * 繰り返すうちに接続が尽きる
     */
    private suspend fun HttpResponse.discardBody() {
        bodyAsChannel().cancel(null)
    }

    override fun close() {
        // engine は client のものなので、こちらを先に閉じても取りに行けなくならない
        pageClient.close()
        client.close()
    }

    sealed interface FetchResult {
        data class Success(
            val requestedUrl: String,
            val feedUrl: String,
            val parsed: ParsedFeed,
        ) : FetchResult

        data object InvalidUrl : FetchResult

        data class HttpError(
            val status: Int? = null,
            val message: String? = null,
        ) : FetchResult

        data object TooLarge : FetchResult

        data class ParseError(
            val message: String,
        ) : FetchResult
    }

    companion object {
        private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024
        private const val MAX_PAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_PAGE_REDIRECTS = 3
        private const val PAGE_TIMEOUT_MILLIS = 10_000L
        private val XHTML = ContentType("application", "xhtml+xml")

        fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }

                expectSuccess = false
            }
    }
}

internal fun FeedFormat.toDisplayName(): String =
    when (this) {
        FeedFormat.RSS_2_0 -> "RSS 2.0"
        FeedFormat.RSS_1_0 -> "RSS 1.0"
        FeedFormat.ATOM_1_0 -> "Atom 1.0"
    }
