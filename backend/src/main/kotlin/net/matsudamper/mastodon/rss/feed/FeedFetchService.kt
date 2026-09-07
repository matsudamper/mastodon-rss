package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
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
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.channelIdFromPageHtml
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.resolve

class FeedFetchService(
    private val client: HttpClient = defaultClient(),
) : Closeable {
    /**
     * フィードを取得して解析する。
     *
     * @param needsDescription 呼び出し側が説明文を使うか。YouTube のようにフィード自体に
     *   説明文が無い配信元では、これが true のときだけ別のページを引いて補う。
     *   定期取得は説明文を使わないので、毎回ページを引かないよう false を渡す
     */
    suspend fun fetch(
        url: String,
        needsDescription: Boolean,
    ): FetchResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return FetchResult.InvalidUrl

        return runCatching {
            // 解決も同じ中に置く。/@handle のような形はここで YouTube のページを
            // 取りに行くので、外に出すと DNS の失敗やタイムアウトが素通りする
            val resolved = when (val resolution = resolveFeedUrl(trimmed)) {
                is FeedUrlResolution.Resolved -> resolution.feed
                is FeedUrlResolution.Failed -> return resolution.result
            }

            val response = client.get(resolved.feedUrl) {
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
                .copy(headerUrl = runCatching { FeedHeaderParser.parse(bytes) }.getOrNull())
                .withYouTubeChannelPage(
                    resolved = resolved,
                    needsDescription = needsDescription,
                )
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

                // 例外の message は取得先の URL を含むことがある（Ktor のタイムアウトなど）
                else -> FetchResult.HttpError(message = error::class.simpleName ?: "取得に失敗した")
            }
        }
    }

    /**
     * 取得しに行く URL を決める。
     *
     * 決められなかった理由は [FeedUrlResolution.Failed] に載せて返す。null にまとめると
     * 呼び出し側では URL が不正だったことにしかできず、ページの取得に失敗したのか、
     * ページが大きすぎたのか、ページからチャンネル ID を抜けなかったのかが消える
     */
    private suspend fun resolveFeedUrl(url: String): FeedUrlResolution {
        // YouTube はスキームの無い形も受けるので、先に解決してから確かめる。
        // 逆にすると YouTubeFeedResolver が対応している形を弾いてしまう。
        // ページを引く先は YouTubeFeedResolver が組み立てた YouTube の URL で、
        // 入力をそのまま取りに行くわけではない
        val resolved = when (val source = resolve(url)) {
            null -> ResolvedFeed(feedUrl = url)

            is YouTubeFeedSource.Feed -> ResolvedFeed(
                feedUrl = source.url,
                youtubeChannelId = source.id.takeIf { source.kind == YouTubeFeedSource.Kind.CHANNEL },
            )

            is YouTubeFeedSource.NeedsPageLookup -> {
                val response = client.get(source.pageUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (!response.status.isSuccess()) {
                    response.discardBody()
                    return FeedUrlResolution.Failed(FetchResult.HttpError(response.status.value))
                }

                val html = response.readBodyUpTo(MAX_YOUTUBE_PAGE_BYTES)?.decodeToString()
                    ?: return FeedUrlResolution.Failed(FetchResult.TooLarge)
                val channelId = channelIdFromPageHtml(source.page, html)
                    ?: return FeedUrlResolution.Failed(FetchResult.ChannelIdNotFound)
                ResolvedFeed(
                    feedUrl = YouTubeFeedResolver.feedUrlForChannel(channelId)
                        ?: return FeedUrlResolution.Failed(FetchResult.ChannelIdNotFound),
                    youtubeChannelId = channelId,
                    // 引いたのがチャンネルのページなら、プロフィールの補完に使う。
                    // 動画のページは、サムネイルなど別用途の画像を拾わないよう持たせない
                    youtubeChannelPage = when (source.page) {
                        YouTubeFeedSource.NeedsPageLookup.Page.CHANNEL -> html
                        YouTubeFeedSource.NeedsPageLookup.Page.VIDEO -> null
                    },
                )
            }
        }

        val parsed = runCatching { URI(resolved.feedUrl) }.getOrNull()
            ?: return FeedUrlResolution.Failed(FetchResult.InvalidUrl)
        // スキームは大文字小文字を区別しない。貼り付けた URL が HTTPS でも通す
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return FeedUrlResolution.Failed(FetchResult.InvalidUrl)
        if (parsed.host.isNullOrBlank()) return FeedUrlResolution.Failed(FetchResult.InvalidUrl)

        return FeedUrlResolution.Resolved(resolved)
    }

    /**
     * YouTube のチャンネルのページから、フィードに無いプロフィール情報を補う。
     *
     * YouTube の Atom には説明文・アイコン・ヘッダー画像が無いので、チャンネルの
     * ページを 1 回だけ引き、必要なものをまとめて埋める。
     */
    private suspend fun ParsedFeed.withYouTubeChannelPage(
        resolved: ResolvedFeed,
        needsDescription: Boolean,
    ): ParsedFeed {
        val channelId = resolved.youtubeChannelId ?: return this
        val wantsDescription = needsDescription && description == null
        val wantsIcon = iconUrl == null
        val wantsHeader = headerUrl == null
        if (!wantsDescription && !wantsIcon && !wantsHeader) return this

        val html = resolved.youtubeChannelPage ?: fetchYouTubeChannelPage(channelId) ?: return this

        val pageDescription = if (wantsDescription) {
            YouTubeFeedResolver.channelDescriptionFromPageHtml(html)
                ?.let { FeedContent(text = it, type = FeedContent.Type.TEXT) }
        } else {
            null
        }
        val pageIconUrl = if (wantsIcon) YouTubeFeedResolver.channelIconFromPageHtml(html) else null
        val pageHeaderUrl = if (wantsHeader) YouTubeFeedResolver.channelHeaderFromPageHtml(html) else null

        return copy(
            description = pageDescription ?: description,
            iconUrl = pageIconUrl ?: iconUrl,
            headerUrl = pageHeaderUrl ?: headerUrl,
        )
    }

    private suspend fun fetchYouTubeChannelPage(channelId: String): String? {
        val pageUrl = YouTubeFeedResolver.channelPageUrl(channelId) ?: return null
        return runCatching {
            val response = client.get(pageUrl) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (!response.status.isSuccess()) {
                response.discardBody()
                return null
            }
            response.readBodyUpTo(MAX_YOUTUBE_PAGE_BYTES)?.decodeToString()
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
    }

    /**
     * 取得しに行く先と、その過程で分かったこと。
     *
     * @param feedUrl 実際に取得する URL
     * @param youtubeChannelId YouTube のチャンネルなら、その ID
     * @param youtubeChannelPage 解決の途中でチャンネルのページを引いていれば、その HTML
     */
    private data class ResolvedFeed(
        val feedUrl: String,
        val youtubeChannelId: String? = null,
        val youtubeChannelPage: String? = null,
    )

    /** 取得しに行く URL を決められたかどうか */
    private sealed interface FeedUrlResolution {
        data class Resolved(
            val feed: ResolvedFeed,
        ) : FeedUrlResolution

        data class Failed(
            val result: FetchResult,
        ) : FeedUrlResolution
    }

    /**
     * 上限を超えたら null を返す。超えた時点で読むのをやめるので、
     * 大きすぎる応答を最後まで受け取らない。
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
     */
    private fun Url.normalize(): String =
        URLBuilder(this)
            .apply {
                fragment = ""
                host = host.lowercase().trimEnd('.')
                encodedPathSegments = encodedPathSegments.map { it.normalizePercentEncoding() }
            }
            .buildString()

    /** 予約文字でない文字の `%XX` を元に戻し、残った `%XX` は大文字に揃える */
    private fun String.normalizePercentEncoding(): String =
        PERCENT_ENCODED.replace(this) { match ->
            val decoded = match.value.substring(1).toInt(16).toChar()
            if (decoded.isUnreserved()) decoded.toString() else match.value.uppercase()
        }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

    /** 読まずに捨てる */
    private suspend fun HttpResponse.discardBody() {
        bodyAsChannel().cancel(null)
    }

    override fun close() {
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

        data object ChannelIdNotFound : FetchResult

        data class ParseError(
            val message: String,
        ) : FetchResult
    }

    companion object {
        private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024
        private const val MAX_YOUTUBE_PAGE_BYTES = 50 * 1024 * 1024

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
