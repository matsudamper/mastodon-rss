package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

/**
 * @param acct どのアカウントに追加するか
 * @param canClose 閉じるとその画面ごと消えるので、登録の途中は false になる
 * @param preview 取得したフィードの中身。取得前は null
 * @param errorMessage 取得か登録に失敗した理由。どちらも同時には進まないので 1 つで足りる
 */
data class AdminAccountFeedNewScreenUiState(
    val acct: String,
    val url: String,
    val fetching: Boolean,
    val canFetch: Boolean,
    val saving: Boolean,
    val canSave: Boolean,
    val canClose: Boolean,
    val preview: Preview?,
    val errorMessage: String?,
    val listener: Listener,
) {
    data class Preview(
        val title: String?,
        val siteUrl: String?,
        val format: String,
        val description: String?,
        val itemCount: Int,
        val sampleItems: List<PreviewItem>,
    )

    data class PreviewItem(
        val title: String?,
        val link: String?,
        val publishedAt: String?,
    )

    @Immutable
    interface Listener {
        fun onUrlChanged(text: String)

        fun onClickFetch()

        fun onClickSave()

        fun onClickClose()
    }
}
