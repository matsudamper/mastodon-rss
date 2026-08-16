package net.matsudamper.mastodon.rss.frontend.logic.admin
sealed interface AdminAddAccountResult {
    data class Success(
        val acct: String,
    ) : AdminAddAccountResult

    /**
     * 追加できなかった理由。当てはまらないものは null か空で来る。
     *
     * @param unusableCharacters 入力に含まれていた使えない文字
     * @param maxLength 文字数が多すぎる場合の上限
     * @param minLength 文字数が足りない場合の下限
     * @param isDuplicated 同じ名前のアカウントが既にある
     */
    data class Rejected(
        val unusableCharacters: List<String>,
        val maxLength: Int?,
        val minLength: Int?,
        val isDuplicated: Boolean,
    ) : AdminAddAccountResult

    data class Failure(
        val message: String,
    ) : AdminAddAccountResult
}
