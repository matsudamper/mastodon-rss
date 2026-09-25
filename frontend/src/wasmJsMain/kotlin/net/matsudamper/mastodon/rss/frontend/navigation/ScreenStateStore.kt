package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.CoroutineScope

/**
 * 履歴ごとに [RetainedScreenState] と rememberSaveable の保存先を持ち、
 * ブラウザの戻る / 進むで来たときに離れる前の表示（スクロール位置など）で出す。
 *
 * バックスタックは URL から組み直すので、別の画面へ進むと前の画面はスタックから消え、
 * Navigation 3 はその画面の状態を捨てる。戻ったときに作り直さないよう、ここで別に持つ。
 */
@Stable
internal class ScreenStateStore(
    private val parentScope: CoroutineScope,
    private val saveableStateHolder: SaveableStateHolder,
) {
    /** 最後に出した順。古いものから捨てる */
    private val statesByEntryId: MutableMap<String, RetainedScreenState> = mutableMapOf()
    private val shownCountByEntryId: MutableMap<String, Int> = mutableMapOf()

    @Composable
    fun Provide(
        entryId: String,
        content: @Composable (RetainedScreenState) -> Unit,
    ) {
        val retainedScreenState = remember(entryId) { stateOf(entryId) }
        DisposableEffect(entryId) {
            shownCountByEntryId[entryId] = shownCountByEntryId.getOrElse(entryId) { 0 } + 1
            onDispose {
                val shownCount = shownCountByEntryId.getOrElse(entryId) { 0 } - 1
                if (shownCount > 0) {
                    shownCountByEntryId[entryId] = shownCount
                } else {
                    shownCountByEntryId.remove(entryId)
                }
                trim()
            }
        }
        saveableStateHolder.SaveableStateProvider(key = entryId) {
            content(retainedScreenState)
        }
    }

    private fun stateOf(entryId: String): RetainedScreenState {
        val state = statesByEntryId.remove(entryId) ?: RetainedScreenState(parentScope)
        statesByEntryId[entryId] = state
        return state
    }

    /**
     * 離れた画面の ViewModel も動いたままなので、持っておく数を絞る。
     * 出している最中の画面は捨てない。組み立て中の画面が持っている ViewModel が止まる
     */
    private fun trim() {
        val overflowCount = statesByEntryId.size - MAX_RETAINED_ENTRIES
        if (overflowCount <= 0) return

        statesByEntryId.keys
            .filter { it !in shownCountByEntryId }
            .take(overflowCount)
            .forEach { entryId ->
                statesByEntryId.remove(entryId)?.dispose()
                saveableStateHolder.removeState(entryId)
            }
    }

    private companion object {
        const val MAX_RETAINED_ENTRIES = 16
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
