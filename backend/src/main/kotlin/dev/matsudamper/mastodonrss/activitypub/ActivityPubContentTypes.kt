package dev.matsudamper.mastodonrss.activitypub

import io.ktor.http.ContentType

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
}
