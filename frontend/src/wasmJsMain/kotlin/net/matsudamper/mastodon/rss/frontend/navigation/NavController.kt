// pushState の第 1 引数（履歴に紐付ける状態）は JsAny? で、null を渡すのに opt-in が要る。
// 状態はバックスタックごと URL から作り直すので、ここには何も持たせない
@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Navigation 3 のバックスタックを、ブラウザの履歴に合わせて持つ。
 *
 * Navigation 3 が扱うのはバックスタックだけで URL は見ない。一方ブラウザには
 * アドレスバーと戻る / 進むがあり、こちらを無視すると「戻ると画面が変わらない」
 * 「共有した URL で別の画面が出る」ことになる。
 *
 * そこで履歴の持ち主はブラウザ側に一本化する。
 *
 * - 画面遷移は [navigateTo]。`pushState` してからバックスタックを組み直す
 * - 戻るはブラウザに任せる。`popstate` を受けて URL からバックスタックを組み直す
 *
 * バックスタックは URL から決まる形にしている（トップ以外は「トップ + その画面」）。
 * 積んだ順を別に覚えると、ブラウザの履歴と二重管理になってずれる。
 */
@Stable
class NavController internal constructor(
    initial: Screen,
) {
    /** [androidx.navigation3.ui.NavDisplay] に渡すバックスタック */
    val backStack: SnapshotStateList<Screen> = mutableStateListOf<Screen>().apply { addAll(stackOf(initial)) }

    /** いま出している画面 */
    val current: Screen get() = backStack.last()

    /**
     * 画面を切り替え、アドレスバーも合わせる。
     *
     * 同じ画面なら履歴に積まない。積むと戻るボタンを押しても同じ画面のままになる。
     */
    fun navigateTo(screen: Screen) {
        if (screen == current) return

        window.history.pushState(PUSHED_BY_APP.toJsString(), screen.title, screen.path)
        applyStack(stackOf(screen))
    }

    /**
     * 戻る。
     *
     * バックスタックを直接削らずブラウザの履歴を戻す。ここで削ると
     * アドレスバーが古いパスのまま残り、再読み込みで別の画面が出る。
     * バックスタックは `popstate` を受けた [syncWithLocation] が組み直す。
     *
     * この画面を直接開いた場合は戻れる履歴がこのサイトの中に無く、
     * そのまま戻るとサイトの外に出る。
     */
    fun back() {
        if (currentEntryPushedByApp) {
            window.history.back()
            return
        }

        val screenBelow = backStack.getOrNull(backStack.lastIndex - 1) ?: return
        replaceCurrentEntry(screenBelow)
    }

    /**
     * いま見えている履歴を [screen] のものに差し替える。目印は付けない。
     *
     * 直接開いた履歴のままにしておくと、ここから更に戻ろうとしたときも
     * [currentEntryPushedByApp] が同じ判断をする。
     */
    private fun replaceCurrentEntry(screen: Screen) {
        window.history.replaceState(null, screen.title, screen.path)
        applyStack(stackOf(screen))
    }

    private val currentEntryPushedByApp: Boolean
        get() = window.history.state != null

    /** 戻る / 進むで URL が変わったときに呼ぶ */
    internal fun syncWithLocation() {
        applyStack(stackOf(Screen.of(window.location.pathname)))
    }

    private fun applyStack(next: List<Screen>) {
        if (next == backStack.toList()) return

        backStack.clear()
        backStack.addAll(next)
    }

    private companion object {
        /**
         * 中身は見ない。目印が付いているかどうかだけを見る
         */
        const val PUSHED_BY_APP: String = "pushed-by-app"

        /**
         * URL から決まるバックスタック。
         *
         * トップを常に下に敷いておくと、直接開いた URL からでも
         * 戻り先が画面の中に必ず 1 つある状態になる。
         *
         * 重ねて出す画面は下に敷く画面も一緒に積む。ダイアログの URL を
         * 直接開いても、下の画面ごと組み上がる。
         */
        fun stackOf(screen: Screen): List<Screen> =
            when {
                screen == Screen.Home -> listOf(Screen.Home)
                screen is Screen.Overlay -> stackOf(screen.background) + screen
                else -> listOf(Screen.Home, screen)
            }
    }
}

/**
 * 現在の URL から [NavController] を作り、履歴の操作とタブのタイトルを繋ぐ。
 */
@Composable
fun rememberNavController(): NavController {
    val navController = remember { NavController(Screen.of(window.location.pathname)) }

    DisposableEffect(navController) {
        // 追加したものと同じ参照でないと外せないので、変数に持ってから渡す
        val onPopState: (Event) -> Unit = { navController.syncWithLocation() }
        window.addEventListener("popstate", onPopState)
        onDispose { window.removeEventListener("popstate", onPopState) }
    }

    // タブのタイトルは canvas の外にあるので Compose では描けない。
    // 画面が変わるたびにここで書き換える
    LaunchedEffect(navController.current) {
        document.title = navController.current.title
    }

    return navController
}
