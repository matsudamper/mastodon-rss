package net.matsudamper.mastodon.rss.activitypub

import io.ktor.http.ContentType
import io.ktor.http.parseAndSortHeader

/**
 * ActivityPub と WebFinger で使う Content-Type。
 *
 * Ktor 既定の `application/json` で返すと Mastodon はアクターとして認識せず、
 * 検索してもプロフィールが出てこない。レスポンスごとにここの定数を明示する。
 */
object ActivityPubContentTypes {
    /** ActivityPub の標準。アクターやアクティビティを返すときはこれ */
    val ActivityJson: ContentType = ContentType("application", "activity+json")

    /**
     * JSON-LD 形式。仕様上は profile パラメータ付きで指定される。
     * 受信時の Accept ヘッダとして飛んでくるので、受け付けられるようにしておく。
     */
    val LdJson: ContentType = ContentType("application", "ld+json")

    /** WebFinger (RFC 7033) のレスポンス用 */
    val JrdJson: ContentType = ContentType("application", "jrd+json")

    /** [negotiate] が選ぶ候補。先に書いたものが優先される */
    private val negotiable: List<ContentType> = listOf(ActivityJson, LdJson)

    /**
     * `Accept` ヘッダを見て、アクターやアクティビティを返すときの Content-Type を選ぶ。
     *
     * Mastodon は `application/activity+json` と、profile パラメータ付きの
     * `application/ld+json` のどちらでも取りに来る。要求された方で返さないと
     * 実装によっては解釈してもらえない。
     *
     * 判断できない場合は [ActivityJson] を返す。`Accept` を送ってこない相手や
     * `*&#47;*` だけの相手に `application/json` を返すと、アクターとして認識されないため。
     */
    fun negotiate(acceptHeader: String?): ContentType {
        if (acceptHeader.isNullOrBlank()) return ActivityJson

        // ld+json は profile パラメータ付きで飛んでくる。
        // ContentType.match はパラメータまで見るので、残っていても当たるよう落としておく
        val ranges =
            parseAndSortHeader(acceptHeader).mapNotNull { item ->
                runCatching { ContentType.parse(item.value) }
                    .getOrNull()
                    ?.withoutParameters()
                    ?.let { AcceptedRange(pattern = it, quality = item.quality) }
            }

        // 品質値が同じなら negotiable に先に書いた方を選ぶ。maxByOrNull は最初の最大を返す
        return negotiable
            .map { candidate -> candidate to qualityOf(candidate, ranges) }
            .filter { (_, quality) -> quality > 0.0 }
            .maxByOrNull { (_, quality) -> quality }
            ?.first
            ?: ActivityJson
    }

    /**
     * [candidate] に当たる指定のうち、最も具体的なものの品質値。当たるものが無ければ 0。
     *
     * `application/&#42;;q=1, application/activity+json;q=0` のように、ワイルドカードで広く許しつつ
     * 特定の型だけ断ることができる。ワイルドカードの品質値で決めると、断られた型を返してしまう
     */
    private fun qualityOf(
        candidate: ContentType,
        ranges: List<AcceptedRange>,
    ): Double =
        ranges
            .filter { candidate.match(it.pattern) }
            .maxByOrNull { it.specificity }
            ?.quality
            ?: 0.0

    private class AcceptedRange(
        val pattern: ContentType,
        val quality: Double,
    ) {
        val specificity: Int =
            when {
                pattern.contentType == "*" -> 0
                pattern.contentSubtype == "*" -> 1
                else -> 2
            }
    }
}
