package net.matsudamper.mastodon.rss.delivery

import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureSigner

/**
 * 実際に相手のサーバーへ POST する [ActivityDelivery]。
 *
 * 署名対象のヘッダは自分で組み立てて明示的に載せる。engine に任せて後から
 * 足された値と、署名したときの値が食い違うと、相手側では「署名が一致しない」
 * としか見えず原因が追えなくなる。
 *
 * @param actorKey 署名に使う秘密鍵。いまはアクターが何人いても鍵は 1 本で、
 *   使い捨てアクターも同じ鍵を共有する。アクターごとの鍵になるのは Phase 6
 */
class HttpActivityDelivery(
    private val actorKey: ActorKey,
    private val signer: HttpSignatureSigner = HttpSignatureSigner(),
    openTelemetry: OpenTelemetry? = null,
    private val client: HttpClient = defaultClient(openTelemetry),
) : ActivityDelivery,
    Closeable {
    override suspend fun deliver(
        inbox: String,
        sender: ActorUrls,
        body: ByteArray,
    ): DeliveryResult {
        val url = runCatching { Url(inbox) }.getOrNull() ?: return DeliveryResult.Failed("inbox の URL を読めない: $inbox")

        val headers =
            signer.sign(
                keyId = sender.publicKeyId,
                privateKey = actorKey.privateKey,
                method = "POST",
                // 相手はリクエストラインの綴りで署名文字列を組み直す。
                // パスを組み立て直すとクエリや末尾の差で合わなくなる
                requestTarget = requestTarget(url),
                host = hostHeader(url),
                body = body,
            )

        val response =
            runCatching {
                client.post(inbox) {
                    headers.forEach { (name, value) -> header(name, value) }
                    header(HttpHeaders.ContentType, ActivityPubContentTypes.ActivityJson.toString())
                    setBody(body)
                }
            }.getOrElse { error ->
                // runCatching は Throwable を拾うので、呼び出し元が消えた合図まで
                // 配信の失敗に化ける。化けると送れていない記事が投稿済みとして残る
                if (error is CancellationException) throw error
                return DeliveryResult.Failed("POST に失敗した: $inbox ${error.message}")
            }

        if (!response.status.isSuccess()) {
            return DeliveryResult.Failed("相手が受け取らなかった: $inbox ${response.status}")
        }

        return DeliveryResult.Delivered
    }

    override fun close() {
        client.close()
    }

    private companion object {
        /** 署名した `(request-target)` と実際に送るリクエストラインを揃える */
        fun requestTarget(url: Url): String =
            if (url.encodedQuery.isEmpty()) {
                url.encodedPath
            } else {
                "${url.encodedPath}?${url.encodedQuery}"
            }

        /**
         * `Host` に載せる値。既定ポート（https の 443）ならポート番号は付けない。
         * 付いていると相手が組み立てる署名文字列と食い違う。
         */
        fun hostHeader(url: Url): String =
            if (url.port == url.protocol.defaultPort) {
                url.host
            } else {
                "${url.host}:${url.port}"
            }

        fun defaultClient(openTelemetry: OpenTelemetry? = null): HttpClient =
            HttpClient(CIO) {
                if (openTelemetry != null) {
                    install(KtorClientTelemetry) {
                        setOpenTelemetry(openTelemetry)
                    }
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
                // status を見て判断するので、4xx や 5xx で例外にしない
                expectSuccess = false
                // リダイレクトを追わない。追うと署名した Host やパスと違う宛先に
                // ボディごと POST し直すことになる
                followRedirects = false
            }
    }
}
