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

                // 例外の message は取得先の URL を含むことがある（Ktor のタイムアウトなど）。
                // 購読の URL はトークンを含みうるので、記録やログに回らない形にする
                else -> FetchResult.HttpError(message = error::class.simpleName ?: "取得に失敗した")
            }
        }
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

        data class ParseError(
            val message: String,
        ) : FetchResult
    }

    companion object {
        private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")
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
