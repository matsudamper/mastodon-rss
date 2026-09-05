package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

/**
 * @param acct どのアカウントに追加するか
 * @param url 入力中の URL
 * @param fetching 取得中。ボタンの文字が変わる
 * @param canFetch false の間は取得のボタンを押せなくする
 * @param saving 登録中。ボタンの文字が変わる
 * @param canSave false の間は登録のボタンを押せなくする
 * @param canClose false の間は閉じられない。閉じると登録の途中で打ち切られる
 * @param preview 取得したフィードの中身。取得前は null
 * @param errorMessage 取得か登録に失敗した理由
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
