package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * 2 ペインを 1 枚のページとして動かすスクロール位置。
 *
 * ページの高さは長い方のカラムで決まる。投稿は 1 万件を想定していて全部の高さは測れないので、
 * 投稿側は LazyColumn に送った量として持ち、投稿が尽きた先はカラムごとずらして送る
 */
@Stable
internal class TwoPaneScrollState {
    var headerCollapsePx: Float by mutableFloatStateOf(0f)
        private set

    private var contentOffsetPx: Float by mutableFloatStateOf(0f)
    private var notesOverflowPx: Float by mutableFloatStateOf(0f)
    private var headerHeightPx: Int by mutableIntStateOf(0)
    private var sideHeightPx: Int by mutableIntStateOf(0)
    private var viewportHeightPx: Int by mutableIntStateOf(0)

    fun notesShiftPx(): Int = notesOverflowPx.roundToInt()

    fun sideShiftPx(): Int = contentOffsetPx.roundToInt().coerceAtMost(sideHeightPx)

    fun updateHeaderHeight(height: Int) {
        headerHeightPx = height
        headerCollapsePx = headerCollapsePx.coerceIn(0f, height.toFloat())
    }

    fun updateSideHeight(height: Int) {
        sideHeightPx = height
    }

    fun updateViewportHeight(height: Int) {
        viewportHeightPx = height
    }

    fun scrollBy(delta: Float, notesListState: LazyListState): Float {
        return if (delta > 0f) {
            scrollForward(delta, notesListState)
        } else {
            scrollBackward(delta, notesListState)
        }
    }

    /**
     * ヘッダーを畳んでから投稿、投稿が尽きたらカラムごとずらす。
     *
     * ヘッダーを畳むと投稿側の表示領域が広がるが、それが反映されるのは次の計測なので、
     * 送る量は畳む前に測った「画面の下に隠れている高さ」で頭打ちにする
     */
    private fun scrollForward(delta: Float, notesListState: LazyListState): Float {
        val notesBelow = notesBelowViewportPx(notesListState)
        var rest = delta.coerceAtMost(maxOf(notesBelow, sideBelowViewportPx()).coerceAtLeast(0f))
        var consumed = 0f

        val collapsed = collapseHeader(rest)
        consumed += collapsed
        rest -= collapsed

        val scrolled = scrollNotes(notesListState, rest.coerceAtMost((notesBelow - collapsed).coerceAtLeast(0f)))
        consumed += scrolled
        rest -= scrolled

        return consumed + shiftPastNotesEnd(rest)
    }

    private fun scrollBackward(delta: Float, notesListState: LazyListState): Float {
        var rest = delta
        var consumed = 0f

        val unshifted = shiftPastNotesEnd(rest)
        consumed += unshifted
        rest -= unshifted

        val scrolled = scrollNotes(notesListState, rest)
        consumed += scrolled
        rest -= scrolled

        return consumed + collapseHeader(rest)
    }

    private fun collapseHeader(delta: Float): Float {
        val next = (headerCollapsePx + delta).coerceIn(0f, headerHeightPx.toFloat())
        val consumed = next - headerCollapsePx
        headerCollapsePx = next
        return consumed
    }

    private fun scrollNotes(notesListState: LazyListState, delta: Float): Float {
        if (delta == 0f) return 0f
        val consumed = notesListState.dispatchRawDelta(delta)
        contentOffsetPx += consumed
        return consumed
    }

    private fun shiftPastNotesEnd(delta: Float): Float {
        val consumed = if (delta > 0f) {
            delta.coerceAtMost(sideBelowViewportPx().coerceAtLeast(0f))
        } else {
            delta.coerceAtLeast(-notesOverflowPx)
        }
        notesOverflowPx += consumed
        contentOffsetPx += consumed
        return consumed
    }

    private fun sideBelowViewportPx(): Float =
        headerHeightPx - headerCollapsePx - contentOffsetPx + sideHeightPx - viewportHeightPx

    private fun notesBelowViewportPx(notesListState: LazyListState): Float {
        val layoutInfo = notesListState.layoutInfo
        val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return 0f
        if (lastItem.index < layoutInfo.totalItemsCount - 1) return Float.POSITIVE_INFINITY
        return (lastItem.offset + lastItem.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset)
            .toFloat()
    }
}

@Composable
internal fun CoordinatedTwoPaneLayout(
    headerCollapsePx: Float,
    onHeaderHeightChange: (Int) -> Unit,
    header: @Composable () -> Unit,
    panes: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .layoutId("header")
                    .onSizeChanged { onHeaderHeightChange(it.height) },
            ) {
                header()
            }
            Box(modifier = Modifier.layoutId("panes")) {
                panes()
            }
        },
    ) { measurables, constraints ->
        val headerPlaceable = measurables.first { it.layoutId == "header" }.measure(
            constraints.copy(
                minWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )
        val visibleHeaderHeight = (headerPlaceable.height - headerCollapsePx)
            .roundToInt()
            .coerceIn(0, headerPlaceable.height)
        val panesHeight = (constraints.maxHeight - visibleHeaderHeight).coerceAtLeast(0)
        val panesPlaceable = measurables.first { it.layoutId == "panes" }.measure(
            Constraints.fixed(
                width = constraints.maxWidth,
                height = panesHeight,
            ),
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.place(x = 0, y = visibleHeaderHeight - headerPlaceable.height)
            panesPlaceable.place(x = 0, y = visibleHeaderHeight)
        }
    }
}

@Composable
internal fun rememberCoordinatedTwoPaneScrollableModifier(
    pageScrollState: TwoPaneScrollState,
    notesListState: LazyListState,
): Modifier {
    val scrollableState = rememberScrollableState { delta ->
        pageScrollState.scrollBy(delta = delta, notesListState = notesListState)
    }

    return Modifier
        .clipToBounds()
        .scrollable(
            state = scrollableState,
            orientation = Orientation.Vertical,
            reverseDirection = ScrollableDefaults.reverseDirection(
                layoutDirection = LocalLayoutDirection.current,
                orientation = Orientation.Vertical,
                reverseScrolling = false,
            ),
        )
        .onSizeChanged { pageScrollState.updateViewportHeight(it.height) }
}
