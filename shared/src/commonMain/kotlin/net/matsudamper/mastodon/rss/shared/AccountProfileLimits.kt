package net.matsudamper.mastodon.rss.shared

/**
 * プロフィールに入れられる文字数。
 *
 * 数え方はコードポイント
 */
object AccountProfileLimits {
    const val DISPLAY_NAME_MAX_LENGTH: Int = 30

    const val SUMMARY_MAX_LENGTH: Int = 1000
}
