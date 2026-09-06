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
         * @param loadMoreErrorMessage 続きを取れなかった理由。取れた分は出したままにする
         */
        data class Loaded(
            val followers: List<Follower>,
            val loadMore: LoadMore,
            val loadMoreErrorMessage: String?,
            val listener: Loaded.Listener,
        ) : Content {
            @Immutable
            interface Listener {
                fun onClickLoadMore()
            }
        }

        data class Error(
            val message: String,
            val listener: Error.Listener,
        ) : Content {
            @Immutable
            interface Listener {
                fun onClickReload()
            }
        }
    }

    /**
     * 一覧の下に出すもの
     */
    sealed interface LoadMore {
        /**
         * 最後まで出している
         */
        data object Hidden : LoadMore

        data object Button : LoadMore

        data object Loading : LoadMore
    }

    /**
     * どの状態でもできること
     */
    @Immutable
    interface Listener {
        fun onClickClose()
    }

    /**
     * フォロワー 1 人。名前もアイコンも分からないので、相手を指す URL だけを出す
     */
    data class Follower(
        val actorUrl: String,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClick()
        }
    }
}
