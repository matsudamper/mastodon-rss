package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.matsudamper.mastodon.rss.frontend.api.AdminApi
import net.matsudamper.mastodon.rss.frontend.api.AdminLoginResult
import net.matsudamper.mastodon.rss.frontend.api.AdminSessionResult

/**
 * 管理画面の状態。[AdminApi] とのやり取りをここにまとめ、画面は [uiState] を描くだけにする。
 */
@Stable
class AdminScreenState internal constructor(
    private val api: AdminApi,
) {
    var uiState: AdminUiState by mutableStateOf(AdminUiState.Loading)
        private set

    /** サーバーに今の状態を聞き直す。画面を開いたときとログアウトの後に呼ぶ */
    suspend fun reload() {
        uiState = AdminUiState.Loading
        uiState = api.session().toUiState()
    }

    /**
     * 入力を書き換える。
     *
     * 直前の失敗の表示はここで消す。入力を変えた後も「パスワードが違う」が
     * 残っていると、いま入れているものが弾かれたように見える。
     */
    fun updatePassword(password: String) {
        val login = uiState as? AdminUiState.Login ?: return
        if (login.submitting) return

        uiState = login.copy(password = password, error = null)
    }

    /** パスワードを送る。空のまま送っても弾かれるだけなので、その場合は何もしない */
    suspend fun login() {
        val login = uiState as? AdminUiState.Login ?: return
        if (login.submitting || login.password.isEmpty()) return

        uiState = login.copy(submitting = true, error = null)

        uiState =
            when (val result = api.login(login.password)) {
                AdminLoginResult.Success -> AdminUiState.LoggedIn

                // 入れ直せば通るので、入力はそのまま残して理由だけ出す
                AdminLoginResult.WrongPassword -> login.copy(submitting = false, error = "パスワードが違う")

                // 入力を直しても通らない。サーバーの設定の問題として出す
                AdminLoginResult.NotConfigured -> AdminUiState.NotConfigured

                is AdminLoginResult.Failure -> login.copy(submitting = false, error = result.message)
            }
    }

    /** ログアウトする。結果は聞き直して確かめる */
    suspend fun logout() {
        if (uiState != AdminUiState.LoggedIn) return

        uiState = AdminUiState.Loading
        uiState = api.logout().toUiState()
    }

    private fun AdminSessionResult.toUiState(): AdminUiState =
        when (this) {
            is AdminSessionResult.Success -> {
                when {
                    loggedIn -> AdminUiState.LoggedIn
                    !passwordConfigured -> AdminUiState.NotConfigured
                    else -> AdminUiState.Login()
                }
            }

            is AdminSessionResult.Failure -> {
                AdminUiState.Unavailable(message)
            }
        }
}

/**
 * 画面が生きている間だけ [AdminApi] を持つ [AdminScreenState] を作る。
 *
 * 画面を開いた時点で状態を聞きに行く。Cookie を持っていれば、そのまま
 * ログイン済みとして出る。
 */
@Composable
fun rememberAdminScreenState(): AdminScreenState {
    val api = remember { AdminApi() }

    // 抱えている接続を閉じる。画面を離れた後も残ると、開き直すたびに増える
    DisposableEffect(api) {
        onDispose { api.close() }
    }

    val state = remember(api) { AdminScreenState(api) }

    LaunchedEffect(state) {
        state.reload()
    }

    return state
}
