package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Immutable

data class AccountFollowersScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * フォロワーが 1 人もいない
         */
        data object Empty : Content

        /**
         * この名前のアカウントは無い。フォロワーがいないのとは分けて出す
         */
        data object NotFound : Content

        /**
         * @param loadMoreButtonVisible 続きがあるうちだけ「もっと見る」を出す
         * @param loadMoreButtonLoading 続きを取っている間は待っていると分かるものに差し替える
         * @param loadMoreErrorMessage 続きを取れなかった理由。取れた分は出したままにする
         */
        data class Loaded(
            val followers: List<FollowerUiState>,
            val loadMoreButtonVisible: Boolean,
            val loadMoreButtonLoading: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickClose()

        fun onClickReload()

        fun onClickLoadMore()
    }
}

/**
 * フォロワー 1 人。名前もアイコンも分からないので、相手を指す URL だけを出す
 */
data class FollowerUiState(
    val actorUrl: String,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClick()
    }
}
