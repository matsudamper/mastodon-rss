package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

data class AdminInboxScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * @param coolOffEmptyText いま止めている送信元が無いことを伝える一行。1 件でもあれば null
         * @param historyEmptyText 記録が無いことを伝える一行。1 件でもあれば null
         */
        data class Loaded(
            val coolOffs: List<CoolOff>,
            val coolOffEmptyText: String?,
            val history: List<Record>,
            val historyEmptyText: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * いま止めている送信元 1 つ。
     *
     * @param untilText いつまで止めるか
     * @param countText 拒否した回数と、通さずに返した回数
     */
    data class CoolOff(
        val clientIp: String,
        val untilText: String,
        val countText: String,
    )

    /**
     * 止め始めた記録 1 件。
     *
     * @param blockedAtText 止め始めた時刻
     * @param untilText そのときに決まった、通すようになる時刻
     */
    data class Record(
        val clientIp: String,
        val blockedAtText: String,
        val untilText: String,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickReload()
    }
}
