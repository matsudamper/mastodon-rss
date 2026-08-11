package net.matsudamper.mastodon.rss.frontend.screen.admin

/**
 * 管理画面に出す内容。ログインしているかはサーバーに聞かないと分からない
 * （セッションは `HttpOnly` の Cookie）ので、「まだ分からない」も状態として持つ。
 */
sealed interface AdminUiState {
    /** サーバーに状態を聞いている最中 */
    data object Loading : AdminUiState

    /**
     * @param submitting 送信中。待ちがあるので押しっぱなしにならないようボタンを止める
     * @param error 直前の試行が失敗した理由。入力を変えたら消す
     */
    data class Login(
        val password: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    ) : AdminUiState

    /** ログイン済み。中身はこれから作る */
    data object LoggedIn : AdminUiState

    /** `ADMIN_PASSWORD_HASH` が無い。入れても通らないので、入力欄は出さず設定方法を出す */
    data object NotConfigured : AdminUiState

    /**
     * 状態が分からなかった。ここで入力欄を出すと、パスワードの問題だと思って何度も試すことになる。
     */
    data class Unavailable(
        val message: String,
    ) : AdminUiState
}
