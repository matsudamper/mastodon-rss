package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.CoroutineScope

/**
 * 画面ごとに [RetainedScreenState] と rememberSaveable の保存先を持ち、
 * ブラウザの戻る / 進むで来たときに離れる前の表示（スクロール位置など）で出す。
 *
 * バックスタックは URL から組み直すので、別の画面へ進むと前の画面はスタックから消え、
 * Navigation 3 はその画面の状態を捨てる。戻ったときに作り直さないよう、ここで別に持つ。
 *
 * アプリの中から開き直したときは [discard] で捨てて作り直す。
 * 履歴ごとではなく画面ごとに持つので、同じ画面が履歴に 2 つあれば状態は共有になる。
 */
@Stable
internal class ScreenStateStore(
    private val parentScope: CoroutineScope,
    private val saveableStateHolder: SaveableStateHolder,
) {
    /** 最後に出した順。古いものから捨てる */
    private val statesByScreen: MutableMap<Screen, RetainedScreenState> = mutableMapOf()
    private val shownCountByScreen: MutableMap<Screen, Int> = mutableMapOf()

    @Composable
    fun Provide(
        screen: Screen,
        content: @Composable () -> Unit,
    ) {
        val retainedScreenState = remember(screen) { stateOf(screen) }
        DisposableEffect(screen) {
            shownCountByScreen[screen] = shownCountByScreen.getOrElse(screen) { 0 } + 1
            onDispose {
                val shownCount = shownCountByScreen.getOrElse(screen) { 0 } - 1
                if (shownCount > 0) {
                    shownCountByScreen[screen] = shownCount
                } else {
                    shownCountByScreen.remove(screen)
                }
                trim()
            }
        }
        CompositionLocalProvider(LocalRetainedScreenState provides retainedScreenState) {
            saveableStateHolder.SaveableStateProvider(key = screen.path, content = content)
        }
    }

    /**
     * 出している最中の画面は捨てない。組み立て中の画面が持っている ViewModel が止まる
     */
    fun discard(screen: Screen) {
        if (screen in shownCountByScreen) return

        statesByScreen.remove(screen)?.dispose()
        saveableStateHolder.removeState(screen.path)
    }

    private fun stateOf(screen: Screen): RetainedScreenState {
        val state = statesByScreen.remove(screen) ?: RetainedScreenState(parentScope)
        statesByScreen[screen] = state
        return state
    }

    /**
     * 離れた画面の ViewModel も動いたままなので、持っておく数を絞る
     */
    private fun trim() {
        val overflowCount = statesByScreen.size - MAX_RETAINED_SCREENS
        if (overflowCount <= 0) return

        statesByScreen.keys
            .filter { it !in shownCountByScreen }
            .take(overflowCount)
            .forEach { discard(it) }
    }

    private companion object {
        const val MAX_RETAINED_SCREENS = 16
    }
}

@Composable
internal fun rememberScreenStateStore(): ScreenStateStore {
    val coroutineScope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    return remember(coroutineScope, saveableStateHolder) {
        ScreenStateStore(
            parentScope = coroutineScope,
            saveableStateHolder = saveableStateHolder,
        )
    }
}
