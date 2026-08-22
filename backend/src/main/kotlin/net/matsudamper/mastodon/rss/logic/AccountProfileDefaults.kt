package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.AccountProfile

/**
 * プロフィールが未保存のときに使う既定値。
 */
object AccountProfileDefaults {
    const val SUMMARY = "RSS/Atom フィードを ActivityPub で配信するアカウント"

    const val DISPLAY_NAME_MAX_LENGTH = 30

    const val SUMMARY_MAX_LENGTH = 500

    fun displayName(
        username: String,
        stored: AccountProfile?,
    ): String = stored?.displayName ?: username

    fun summary(stored: AccountProfile?): String = stored?.summary ?: SUMMARY
}
