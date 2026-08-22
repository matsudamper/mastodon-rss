package net.matsudamper.mastodon.rss.feed // pragma: allowlist secret // pragma: allowlist secret

import java.io.Closeable
import java.net.URI
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.channelIdFromPageHtml // pragma: allowlist secret
import net.matsudamper.mastodon.rss.feed.YouTubeFeedResolver.resolve // pragma: allowlist secret

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

            val finalUrl = response.request.url.toString()
            val bytes = response.bodyAsBytes()
            if (bytes.size > MAX_BODY_BYTES) {
                return FetchResult.TooLarge
            }

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
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (parsed.host.isNullOrBlank()) return null

        return when (val source = resolve(url)) {
            null -> url

            is YouTubeFeedSource.Feed -> source.url

            is YouTubeFeedSource.NeedsPageLookup -> {
                val response = client.get(source.pageUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (!response.status.isSuccess()) return null

                val html = response.bodyAsBytes().decodeToString().take(MAX_PAGE_BYTES)
                val channelId = channelIdFromPageHtml(html) ?: return null
                YouTubeFeedResolver.feedUrlForChannel(channelId) ?: return null
            }
        }
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

                followRedirects = false

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
