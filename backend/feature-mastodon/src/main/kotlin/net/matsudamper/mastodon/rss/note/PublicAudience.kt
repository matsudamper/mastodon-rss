package net.matsudamper.mastodon.rss.note

/**
 * 誰でも見られることを表す宛先。
 *
 * `to` に入れると公開投稿、`cc` に入れると未収載（フォロワーには届くが
 * 公開タイムラインには出ない）になる。
 */
const val PUBLIC_AUDIENCE: String = "https://www.w3.org/ns/activitystreams#Public"
