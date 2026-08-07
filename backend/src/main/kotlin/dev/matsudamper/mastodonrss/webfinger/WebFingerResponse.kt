package dev.matsudamper.mastodonrss.webfinger

import kotlinx.serialization.Serializable

/**
 * WebFinger (RFC 7033) の JRD レスポンス。
 *
 * Mastodon は `@admin@example.com` の検索でまずここを引き、`links` の
 * `rel: "self"` から Actor の URL を得る。ActivityPub 自体の仕様ではないが、
 * この 1 ホップ目が無いとアカウントを発見できない。
 *
 * @param subject 問い合わせられた `acct:` をそのまま返す
 * @param aliases 同じ対象を指す別の URI。Actor の id を入れておく
 */
@Serializable
data class WebFingerResponse(
    val subject: String,
    val aliases: List<String>? = null,
    val links: List<WebFingerLink>,
)

/**
 * @param rel リンクの種類。Actor を指すものは `self`
 * @param type 参照先の Content-Type。`self` では `application/activity+json`
 */
@Serializable
data class WebFingerLink(
    val rel: String,
    val type: String? = null,
    val href: String? = null,
) {
    companion object {
        const val REL_SELF: String = "self"
    }
}
