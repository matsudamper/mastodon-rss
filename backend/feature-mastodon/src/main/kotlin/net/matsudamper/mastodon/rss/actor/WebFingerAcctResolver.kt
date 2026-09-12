package net.matsudamper.mastodon.rss.actor

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.webfinger.WebFingerLink
import org.slf4j.LoggerFactory

/**
 * 相手のホストの WebFinger を引いて、アクターの acct を確定させる。
 *
 * `preferredUsername` とアクターのホストを繋げただけの acct は、Mastodon の
 * `WEB_DOMAIN` と `LOCAL_DOMAIN` を分けている相手（アクターは前者、acct は後者）では
 * 検索窓で解決しない。正しい acct はアクターのホストの WebFinger が返す
 * `subject` にしかないので、フォローを受けた時点で引く。
 *
 * acct は管理画面でリンクの表示文字列になるので、相手の言い分をそのまま出さない。
 * `subject` は 2 つ確かめてから信じる。
 *
 * - `links` の `self` がこのアクターを指していること。同じホストの別の
 *   アカウントの名前を名乗られないため
 * - `subject` のホストがアクターのホストと違う場合は、名乗られたホストの
 *   WebFinger にも同じことを言わせる。確かめないと、リンク先はこちらのホスト、
 *   名乗りは他所のホスト、という表示を相手が作れる。委譲は正しくこの形になる
 *
 * 確定できなくてもフォローは成立する。引けなかった理由はログに残し、null を返す。
 */
internal class WebFingerAcctResolver(
    private val client: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(WebFingerAcctResolver::class.java)

    /**
     * @param preferredUsername アクター文書の `preferredUsername`。名前として出せない
     *   ものなら問い合わせずに null。問い合わせる名前が決まらないため
     */
    suspend fun resolve(
        actorId: String,
        actorUrl: Url,
        preferredUsername: String?,
    ): String? {
        val name = preferredUsername?.takeIf { isDisplayableUsername(it) } ?: return null

        // inbox の応答を待たせないよう、裏付けの 2 回目まで含めて短く切る
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            resolveWithoutTimeout(actorId = actorId, actorUrl = actorUrl, preferredUsername = name)
        }
    }

    private suspend fun resolveWithoutTimeout(
        actorId: String,
        actorUrl: Url,
        preferredUsername: String,
    ): String? {
        // 既定でないポートで動いている相手は、ポートまで含めないと別の接続先を指す
        val authority =
            if (actorUrl.port == actorUrl.protocol.defaultPort) actorUrl.host else "${actorUrl.host}:${actorUrl.port}"

        val document =
            fetch(
                authority = authority,
                resource = "$ACCT_SCHEME$preferredUsername@$authority",
                allowedHosts = setOf(actorUrl.host),
                requiredPort = actorUrl.port,
            ) ?: return null

        val acct = document.acctPointingTo(actorId)
        if (acct == null) {
            logger.info("WebFinger の応答がこのアクターを指していない: $actorId")
            return null
        }

        // ポートまで含めて同じでなければ、別の接続先を名乗っているので裏付けを取る
        if (acct.host.equals(authority, ignoreCase = true)) return acct.text

        // 名乗られたホスト側の WebFinger。LOCAL_DOMAIN 側はアクターのホストへ
        // リダイレクトするのが普通なので、移った先はどちらでもよい
        val delegated =
            fetch(
                authority = acct.host,
                resource = "$ACCT_SCHEME${acct.name}@${acct.host}",
                allowedHosts = setOf(acct.host.substringBefore(':'), actorUrl.host),
                requiredPort = null,
            ) ?: return null

        val confirmed = delegated.acctPointingTo(actorId)
        if (confirmed?.text != acct.text) {
            logger.info("acct のホストが名乗りを裏付けない: $actorId acct=${acct.text}")
            return null
        }

        return acct.text
    }

    /**
     * WebFinger を 1 回引く。読めなければ null。
     *
     * @param allowedHosts リダイレクトで移ってよいホスト。ここに無いホストが返した
     *   ものは、そのホストが他人の acct を名乗れることになるので捨てる
     * @param requiredPort 移った先に許すポート。null なら問わない
     */
    private suspend fun fetch(
        authority: String,
        resource: String,
        allowedHosts: Set<String>,
        requiredPort: Int?,
    ): RemoteWebFingerDocument? {
        val response =
            runCatching {
                client.get("https://$authority/.well-known/webfinger") {
                    parameter("resource", resource)
                    // JRD だけを返すサーバーに application/json だけで問い合わせると 406 になる
                    header(
                        HttpHeaders.Accept,
                        "${ActivityPubContentTypes.JrdJson}, ${ContentType.Application.Json}",
                    )
                }
            }.getOrNull()

        if (response == null) {
            logger.info("WebFinger を引けなかった: $resource")
            return null
        }

        if (!response.status.isSuccess()) {
            logger.info("WebFinger が失敗を返した: $resource status=${response.status.value}")
            return null
        }

        // リダイレクトで別の宛先に移っていたら、そこが他人の acct を名乗れる。
        // ポートを落とすと別の接続先になるのと同じ理由で、scheme とポートまで見る
        val finalUrl = response.request.url
        val movedAway =
            finalUrl.protocol != URLProtocol.HTTPS ||
                allowedHosts.none { finalUrl.host.equals(it, ignoreCase = true) } ||
                (requiredPort != null && finalUrl.port != requiredPort)
        if (movedAway) {
            logger.info("WebFinger が別の宛先へ移った: $resource 移った先=${finalUrl.host}")
            return null
        }

        val body = runCatching { response.readBodyUpTo(MAX_BODY_BYTES) }.getOrNull()
        if (body == null) {
            logger.info("WebFinger の応答を読めないか大きすぎる: $resource")
            return null
        }

        val document = runCatching { AppJson.decodeFromString(RemoteWebFingerDocument.serializer(), body) }.getOrNull()
        if (document == null) {
            logger.info("WebFinger の応答を読めなかった: $resource")
        }
        return document
    }

    /**
     * 上限を超えたら null を返す。超えた時点で読むのをやめるので、
     * 大きすぎる応答をメモリに展開しない。
     *
     * 途中でやめた場合は残りを読む相手がいなくなるので、channel を閉じて
     * 接続を返す。閉じないと繰り返すうちに接続が尽きる
     */
    private suspend fun HttpResponse.readBodyUpTo(limit: Int): String? {
        val channel = bodyAsChannel()
        val bytes = channel.readRemaining((limit + 1).toLong()).readByteArray()
        if (bytes.size > limit) {
            channel.cancel(null)
            return null
        }
        return bytes.decodeToString()
    }

    /**
     * `self` が [actorId] を指しているときだけ、`subject` を acct として読む
     */
    private fun RemoteWebFingerDocument.acctPointingTo(actorId: String): ResolvedAcct? {
        val pointsBackToActor = links.any { it.rel == WebFingerLink.REL_SELF && it.href == actorId }
        if (!pointsBackToActor) return null

        val acctSubject = subject?.removePrefix(ACCT_SCHEME) ?: return null
        val name = acctSubject.substringBefore('@', missingDelimiterValue = "")
        val host = acctSubject.substringAfter('@', missingDelimiterValue = "")
        if (!isDisplayableUsername(name) || !isAcctHost(host)) return null

        return ResolvedAcct(name = name, host = host)
    }

    /**
     * acct の名前として出してよいか。
     *
     * `@` や空白を通すと、リンク先とは別のホストを名乗る表示を相手が作れる。
     * プロフィールの URL を同じホストに絞っているのと同じ理由で、
     * 名前の側も相手の言い分をそのまま出さない。
     */
    private fun isDisplayableUsername(raw: String): Boolean =
        raw.isNotEmpty() &&
            raw.length <= MAX_USERNAME_LENGTH &&
            raw.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '.' || it == '-' }

    /**
     * acct のホストとして出してよいか。
     *
     * ホストは相手のサーバーが決めるので、こちらから形を決めきれない。
     * acct の形（`@name@host`）を崩す文字と、表示を壊す文字だけを弾く。
     * 既定でないポートで動いている相手は `host:port` を返してくるので、`:` は通す。
     */
    private fun isAcctHost(raw: String): Boolean =
        raw.isNotEmpty() &&
            raw.length <= MAX_HOST_LENGTH &&
            raw.none { it == '@' || it == '/' || it.isWhitespace() || it.isISOControl() }

    private companion object {
        /**
         * acct の確定に使ってよい時間。アクター文書の取得と `Accept` の送信に
         * 積み上がるので短く切る。ここで諦めても「未取得」になるだけ
         */
        const val TIMEOUT_MILLIS = 3_000L

        /**
         * `subject` に付く scheme。`acct:alice@example.com` の形で返ってくる
         */
        const val ACCT_SCHEME = "acct:"

        /**
         * acct に出すホストの長さの上限。DNS 名の上限に合わせる
         */
        const val MAX_HOST_LENGTH = 253

        /**
         * acct に出す名前の長さの上限。Mastodon は 30 文字までだが、
         * 他の実装まで同じとは限らないので緩めに取る
         */
        const val MAX_USERNAME_LENGTH = 64

        /**
         * 読み込む応答の上限。JRD は `subject` と数本の `links` で、数 KB にしかならない
         */
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

/**
 * WebFinger から読めた acct。
 *
 * @param text 画面に出す `@name@host` の形
 */
private data class ResolvedAcct(
    val name: String,
    val host: String,
) {
    val text: String = "@$name@$host"
}

/**
 * 相手のホストの WebFinger の応答のうち、こちらが見る部分だけ。
 *
 * こちらが返す [net.matsudamper.mastodon.rss.webfinger.WebFingerResponse] を
 * 使い回さないのは、あちらが「返すときに必ず入れるもの」を必須にしているため。
 * 相手が `links` を省略しただけで読めなくなるのは筋が悪い。
 */
@Serializable
private data class RemoteWebFingerDocument(
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("links")
    val links: List<RemoteWebFingerLink> = listOf(),
)

@Serializable
private data class RemoteWebFingerLink(
    @SerialName("rel")
    val rel: String? = null,
    @SerialName("href")
    val href: String? = null,
)
