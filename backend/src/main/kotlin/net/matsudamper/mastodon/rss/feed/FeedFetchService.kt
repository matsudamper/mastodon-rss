package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import java.net.URI
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.channelIdFromPageHtml
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.resolve

class FeedFetchService(
    private val client: HttpClient = defaultClient(),
    private val externalHosts: ExternalHosts = InternalHosts(),
) : Closeable {
    /**
     * 記事のリンク先を取る口。飛ばされた先を自分で見るために、client には追わせない
     */
    private val pageClient: HttpClient = client.config { followRedirects = false }

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
     * 記事のリンク先のページを取って `og:image` を返す。
     *
     * 取れなければ null を返して、記事の取り込みはそのまま続ける。画像は投稿の
     * 飾りなので、配信元のページが落ちているだけで記事を落とすほうが困る。
     *
     * 打ち切りをフィードより短くするのは、記事の数だけ繰り返すため。1 本が
     * 黙り込んだだけで取り込み全体が待たされる。打ち切れるのは取得までで、
     * 読むほうは中断点を持たないので外に出してある。走査は入力長に比例する
     * ([OpenGraph]) ので、そこで待たされることはない
     *
     * @return 絶対化した http / https の URL。見つからなければ null
     */
    suspend fun fetchOpenGraphImageUrl(url: String): String? {
        // 打ち切りは呼び出し元とは別の時計で測る。仮想時間で回っている相手の
        // 上で測ると、こちらが応答を待っている間に時計が進んで、取りに行く前に
        // 打ち切られたことになる
        val page = withContext(Dispatchers.IO) {
            withTimeoutOrNull(PAGE_TIMEOUT_MILLIS) {
                runCatching { loadPage(url) }
                    .getOrElse { error ->
                        if (error is CancellationException) throw error
                        null
                    }
            }
        } ?: return null

        // 呼び出し元のスレッドを借りない。ページを読むのは CPU の仕事
        val imageUrl = withContext(Dispatchers.Default) { OpenGraph.imageUrl(page.html) } ?: return null

        // og:image は相対 URL でもよい。基準は飛んだ先のページ。
        // content には長さの上限が無く、長すぎるものは DB にも Create の本文にも
        // 毎回載るので、URL として妥当な長さを超えたら持たない
        return HttpUrl.sanitize(imageUrl, page.url)?.takeIf { it.length <= MAX_IMAGE_URL_LENGTH }
    }

    /**
     * リンク先はフィードの配信元が自由に書けるので、取りに行く前に
     * [externalHosts] で内部向けかどうかを見る。飛ばされた先も同じなので、
     * リダイレクトは client に任せず自分で辿って毎回見る。
     */
    private suspend fun loadPage(url: String): FetchedPage? {
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
            return FetchedPage(url = target, html = String(bytes, response.bodyCharset() ?: Charsets.UTF_8))
        }

        return null
    }

    /**
     * 取れたページ。[url] は飛んだ先で、相対 URL を解決する基準になる
     */
    private data class FetchedPage(
        val url: String,
        val html: String,
    )

    /**
     * 本文の文字コード。名乗っていなければ null。
     *
     * 名乗りが読めない綴りでも取り込みは続けたいので、例外にはしない
     */
    private fun HttpResponse.bodyCharset(): Charset? =
        runCatching { contentType()?.charset() }.getOrNull()

    /**
     * HTML として読める応答か。XHTML を配るページがあるので両方を通す
     */
    private fun HttpResponse.isHtml(): Boolean {
        val type = contentType() ?: return false
        return type.match(ContentType.Text.Html) || type.match(XHTML)
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
                    // 引いたのがチャンネルのページなら、説明文とプロフィール画像はこの中にある。
                    // 動画のページは持たせない。og:image がその動画のサムネイルなので、
                    // チャンネルのアイコンとして拾ってしまう
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
     * YouTube のチャンネルのページから、フィードに無いものを補う。
     *
     * YouTube の Atom には `subtitle` も、アイコンを表す要素（`icon` / `logo`）も無い。
     * チャンネル名は `title` にあるが、説明文・アイコン・ヘッダーはチャンネルのページにしか
     * 無いので、欠けているものがあるときだけそのページを引いて埋める。
     *
     * 説明文は登録時にしか使わないので [needsDescription] で分ける。アイコンとヘッダーは
     * 定期取得でも埋める。ここで埋めないと、取り込みのたびにプロフィール画像が欠ける。
     *
     * 取れなくてもフィード自体は使えるので、失敗は無いものとして扱い、
     * 取得の失敗にはしない。
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
        val pageHeaderUrl = if (wantsHeader) YouTubeChannelHeader.fromPageHtml(html) else null

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

    /**
     * 取得しに行く URL を決められたかどうか。
     */
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

        /**
         * @param message 取得できなかった理由。例外の message は取得先の URL を含むことが
         *   あるので入れない。記録やログに回るため、秘密を含まない形だけを渡す
         */
        data class HttpError(
            val status: Int? = null,
            val message: String? = null,
        ) : FetchResult

        data object TooLarge : FetchResult

        /**
         * YouTube のページは取れたが、そこからチャンネル ID を抜き出せなかった。
         *
         * 同意画面やレート制限のページが 200 で返ってくることがあり、
         * 取得の成否だけでは区別できない
         */
        data object ChannelIdNotFound : FetchResult

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
        private const val MAX_IMAGE_URL_LENGTH = 2048
        private val XHTML = ContentType("application", "xhtml+xml")

        /**
         * YouTube のページから読む上限。
         *
         * 引く先は YouTubeFeedResolver が組み立てた YouTube の URL に限られるので、
         * 大きめに取る。実際のチャンネルのページは 2MiB を超えることがあり、
         * そこで切ると `/@handle` のチャンネルを登録できない
         */
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
