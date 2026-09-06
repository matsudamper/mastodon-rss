package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminAccountProfileLimitsResult {
    /**
     * @param displayNameMaxLength 表示名に入れられる文字数
     * @param summaryMaxLength 説明文に入れられる文字数
     */
    data class Success(
        val displayNameMaxLength: Int,
        val summaryMaxLength: Int,
    ) : AdminAccountProfileLimitsResult

    data class Failure(
        val message: String,
    ) : AdminAccountProfileLimitsResult
}
