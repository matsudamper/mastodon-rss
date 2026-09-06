package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminUpdateAccountProfileResult {
    data class Success(
        val account: AdminAccount,
    ) : AdminUpdateAccountProfileResult

    /**
     * 入力が通らなかった。
     *
     * @param unknownAccount その名前のアカウントが無い
     * @param displayNameMaxLength 表示名が長すぎる場合の上限
     * @param summaryMaxLength 説明文が長すぎる場合の上限
     */
    data class Rejected(
        val unknownAccount: Boolean,
        val displayNameMaxLength: Int?,
        val summaryMaxLength: Int?,
    ) : AdminUpdateAccountProfileResult

    data class Failure(
        val message: String,
    ) : AdminUpdateAccountProfileResult
}
