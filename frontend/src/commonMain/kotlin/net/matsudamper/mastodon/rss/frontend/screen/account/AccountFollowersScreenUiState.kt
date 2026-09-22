package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Immutable

data class AccountFollowersScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        /**
         * この名前のアカウントは無い。フォロワーがいないのとは分けて出す
         */
        data object NotFound : Content

        data class Loaded(
            val followers: List<Follower>,
            val loadMore: LoadMore,
            val listener: Loaded.Listener,
        ) : Content {
            @Immutable
            interface Listener {
                fun onLoadMore()
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

    sealed interface LoadMore {
        /**
         * 最後まで出している
         */
        data object Hidden : LoadMore

        /**
         * 枠が見えたら続きを取りに行く
         */
        data object LoadOnVisible : LoadMore

        data object Loading : LoadMore

        /**
         * 続きが取れなかった。自動では取りに行かず、押して再試行するボタンと一緒に出す
         */
        data class Error(val message: String) : LoadMore
    }

    /**
     * どの状態でもできること
     */
    @Immutable
    interface Listener {
        fun onClickClose()
    }

    /**
     * @param name 一覧に出す名前。表示名が無ければ acct、それも無ければプロフィールの URL
     * @param acct 名前の下に出す。表示名を名乗っていなければ [name] と同じになるので出さない
     */
    data class Follower(
        val name: String,
        val acct: String?,
        val iconUrl: String?,
        val listener: Follower.Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClick()
        }
    }
}
