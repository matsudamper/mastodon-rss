package net.matsudamper.mastodon.rss.feed

import java.io.Closeable
import java.net.URI
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
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.channelIdFromPageHtml
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.resolve

class FeedFetchService(
    private val client: HttpClient = defaultClient(),
) : Closeable {
    suspend fun fetch(url: String): FetchResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return FetchResult.InvalidUrl

        val resolvedUrl = resolveFeedUrl(trimmed) ?: return FetchResult.InvalidUrl

        return runCatching {
            val response = client.get(resolvedUrl) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            if (!response.status.isSuccess()) {
                return FetchResult.HttpError(response.status.value)
            }

            // 配信元が /feed から /feed/ へ、http から https へ飛ばすのは普通にある。
            // 保存するのは飛んだ先の URL で、次からはそこを直接取りに行く。
            // フラグメントは相手に送られず同じリソースを指すので、
            // 残すと #a と #b が別のフィードとして登録できてしまう
            val finalUrl = URLBuilder(response.request.url).apply { fragment = "" }.buildString()
            val bytes = response.readBodyUpTo(MAX_BODY_BYTES) ?: return FetchResult.TooLarge

            val parsed = FeedParser.parse(bytes)
            FetchResult.Success(
                requestedUrl = trimmed,
                feedUrl = finalUrl,
                parsed = parsed,
            )
        }.getOrElse { error ->
            when (error) {
                is FeedParseException -> FetchResult.ParseError(error.message ?: "パースに失敗した")
                else -> FetchResult.HttpError(message = error.message ?: "取得に失敗した")
            }
        }
    }

    private suspend fun resolveFeedUrl(url: String): String? {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        // スキームは大文字小文字を区別しない。貼り付けた URL が HTTPS でも通す
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (parsed.host.isNullOrBlank()) return null

        return when (val source = resolve(url)) {
            null -> url

            is YouTubeFeedSource.Feed -> source.url

            is YouTubeFeedSource.NeedsPageLookup -> {
                val response = client.get(source.pageUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (!response.status.isSuccess()) return null

                val html = response.readBodyUpTo(MAX_PAGE_BYTES)?.decodeToString() ?: return null
                val channelId = channelIdFromPageHtml(html) ?: return null
                YouTubeFeedResolver.feedUrlForChannel(channelId) ?: return null
            }
        }
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

        data class ParseError(
            val message: String,
        ) : FetchResult
    }

    companion object {
        private const val USER_AGENT = "mastodon-rss/0.1"
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024
        private const val MAX_PAGE_BYTES = 2 * 1024 * 1024

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
