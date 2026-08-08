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

        // 品質値 (q=) の高い順に並べ替えてから先頭から見る
        for (item in parseAndSortHeader(acceptHeader)) {
            // ld+json は profile パラメータ付きで飛んでくる。
            // ContentType.match はパラメータまで見るので、残っていても当たるよう落としておく
            val pattern =
                runCatching { ContentType.parse(item.value) }
                    .getOrNull()
                    ?.withoutParameters()
                    ?: continue

            val matched = negotiable.firstOrNull { it.match(pattern) }
            if (matched != null) return matched
        }

        return ActivityJson
    }
}
