// pushState の第 1 引数（履歴に紐付ける状態）は JsAny? で、読み書きに opt-in が要る
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
import kotlin.random.Random
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
 *
 * ただし重ねて出す画面だけは、アプリの中から開いたときに開いた画面の上へ重ねる。
 * URL だけでは下に何があったか分からないので、下の画面のパスを履歴の状態に持たせる。
 */
@Stable
class NavController internal constructor() {
    /**
     * [androidx.navigation3.ui.NavDisplay] に渡すバックスタック。
     *
     * 再読み込みしても履歴の状態は残るので、アプリの中で開いたときと同じ画面を下に敷ける
     */
    val backStack: SnapshotStateList<HistoryEntry> =
        mutableStateListOf<HistoryEntry>().apply { addAll(stackOfCurrentEntry()) }

    /** いま出している画面 */
    val current: Screen get() = backStack.last().screen

    /**
     * 画面を切り替え、アドレスバーも合わせる。
     *
     * 同じ画面なら履歴に積まない。積むと戻るボタンを押しても同じ画面のままになる。
     */
    fun navigateTo(screen: Screen) {
        if (screen == current) return

        val entry = HistoryEntry(screen = screen, id = createEntryId())
        val next = if (screen is Screen.Overlay) backStack.toList() + entry else stackOf(entry)
        val entryBelow = next.getOrNull(next.lastIndex - 1)
        window.history.pushState(
            createHistoryState(
                id = entry.id,
                screenBelowPath = entryBelow?.screen?.path,
                screenBelowId = entryBelow?.id,
                pushedByApp = true,
            ),
            screen.title,
            screen.path,
        )
        applyStack(next)
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

        val entryBelow = backStack.getOrNull(backStack.lastIndex - 1) ?: return
        replaceCurrentEntry(entryBelow)
    }

    /**
     * いま見えている履歴を [entry] のものに差し替える。アプリが積んだ目印は付けない。
     *
     * 直接開いた履歴のままにしておくと、ここから更に戻ろうとしたときも
     * [currentEntryPushedByApp] が同じ判断をする。
     * id は下に敷いていたときのものを引き継ぐ。変えると下で出していた画面が作り直される。
     */
    private fun replaceCurrentEntry(entry: HistoryEntry) {
        window.history.replaceState(
            createHistoryState(
                id = entry.id,
                screenBelowPath = null,
                screenBelowId = null,
                pushedByApp = false,
            ),
            entry.screen.title,
            entry.screen.path,
        )
        applyStack(stackOf(entry))
    }

    private val currentEntryPushedByApp: Boolean
        get() = isPushedByApp(window.history.state)

    /** 戻る / 進むで URL が変わったときに呼ぶ */
    internal fun syncWithLocation() {
        applyStack(stackOfCurrentEntry())
    }

    private fun applyStack(next: List<HistoryEntry>) {
        if (next == backStack.toList()) return

        backStack.clear()
        backStack.addAll(next)
    }

    private companion object {
        /**
         * いま見えている履歴から組むバックスタック。
         *
         * id の無い履歴（直接開いたもの）にはここで振る。戻る / 進むで同じ履歴に来たときに、
         * 前に出した状態を引けるようにする
         */
        fun stackOfCurrentEntry(): List<HistoryEntry> {
            val state = window.history.state
            val id = historyEntryId(state) ?: assignEntryIdToCurrentEntry(state)
            val entry = HistoryEntry(screen = Screen.of(window.location.pathname), id = id)
            val screenBelowPath = historyScreenBelowPath(state)
            return if (entry.screen is Screen.Overlay && screenBelowPath != null) {
                val entryBelow = HistoryEntry(
                    screen = Screen.of(screenBelowPath),
                    id = historyScreenBelowId(state) ?: "$id/below",
                )
                stackOf(entryBelow) + entry
            } else {
                stackOf(entry)
            }
        }

        fun assignEntryIdToCurrentEntry(state: JsAny?): String {
            val id = createEntryId()
            window.history.replaceState(
                createHistoryState(
                    id = id,
                    screenBelowPath = historyScreenBelowPath(state),
                    screenBelowId = null,
                    pushedByApp = isPushedByApp(state),
                ),
                document.title,
            )
            return id
        }

        /**
         * 再読み込みしても前の履歴は残り、画面の状態は残らない。
         * 読み込みごとに数え直すと前の履歴の id と重なり、別の履歴の状態を出してしまうので乱数にする
         */
        fun createEntryId(): String = Random.nextLong().toULong().toString(36)

        /**
         * URL から決まるバックスタック。
         *
         * トップを常に下に敷いておくと、直接開いた URL からでも
         * 戻り先が画面の中に必ず 1 つある状態になる。
         *
         * 重ねて出す画面は下に敷く画面も一緒に積む。ダイアログの URL を
         * 直接開いても、下の画面ごと組み上がる。
         *
         * 下に敷く画面は履歴を持たないので、上の画面の id から決まる id にする
         */
        fun stackOf(entry: HistoryEntry): List<HistoryEntry> {
            val screen = entry.screen
            return when {
                screen == Screen.Home -> listOf(entry)
                screen is Screen.Overlay -> stackOf(HistoryEntry(screen.background, "${entry.id}/below")) + entry
                else -> listOf(HistoryEntry(Screen.Home, "${entry.id}/home"), entry)
            }
        }
    }
}

/**
 * バックスタックに積む 1 つ分。[id] はブラウザの履歴 1 つごとに振る。
 *
 * 同じ画面を履歴に 2 つ積んでも、それぞれの履歴で見ていた位置に戻れるよう画面とは別に持つ
 */
data class HistoryEntry(
    val screen: Screen,
    val id: String,
)

// 履歴に紐付ける状態。以前は下に敷く画面のパスだけを文字列で持っていたので、その形も読む
private fun createHistoryState(
    id: String,
    screenBelowPath: String?,
    screenBelowId: String?,
    pushedByApp: Boolean,
): JsAny = js("({ id: id, screenBelowPath: screenBelowPath, screenBelowId: screenBelowId, pushedByApp: pushedByApp })")

private fun historyEntryId(state: JsAny?): String? =
    js("typeof state?.id === 'string' ? state.id : null")

private fun historyScreenBelowPath(state: JsAny?): String? =
    js("typeof state === 'string' ? state : (typeof state?.screenBelowPath === 'string' ? state.screenBelowPath : null)")

private fun historyScreenBelowId(state: JsAny?): String? =
    js("typeof state?.screenBelowId === 'string' ? state.screenBelowId : null")

private fun isPushedByApp(state: JsAny?): Boolean =
    js("typeof state === 'string' || state?.pushedByApp === true")

/**
 * 現在の URL から [NavController] を作り、履歴の操作とタブのタイトルを繋ぐ。
 */
@Composable
fun rememberNavController(): NavController {
    val navController = remember { NavController() }

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
