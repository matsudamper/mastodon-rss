package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
    private var notesAnchorKey: Any? = null
    private var notesAnchorOffsetPx: Int = 0
    private val measuredNoteHeights: MutableMap<Any, Int> = mutableMapOf()

    fun notesShiftPx(): Int = notesOverflowPx.roundToInt()

    fun sideShiftPx(): Int = contentOffsetPx.roundToInt().coerceIn(0, sideHeightPx)

    /**
     * 畳みきった後に高さが変わっても、畳んだままにする。
     *
     * 文字の高さはフォントの読み込みで後から変わる。畳んだ量のままだと、伸びた分だけ投稿の上に出てくる
     */
    fun updateHeaderHeight(height: Int) {
        val collapsedFully = headerHeightPx > 0 && headerCollapsePx >= headerHeightPx
        headerHeightPx = height
        headerCollapsePx = if (collapsedFully) {
            height.toFloat()
        } else {
            headerCollapsePx.coerceIn(0f, height.toFloat())
        }
    }

    fun updateSideHeight(height: Int) {
        sideHeightPx = height
    }

    fun updateViewportHeight(height: Int) {
        viewportHeightPx = height
    }

    /**
     * 投稿側のレイアウトが変わるたびに呼ぶ。
     *
     * 追加読み込みに限らず、要素の高さが後から変わったときも位置を合わせ直す
     */
    fun onNotesLayoutChanged(notesListState: LazyListState) {
        syncNotesLayout(notesListState)
        resyncNotesOverflow(notesListState)
    }

    /**
     * 前に見たレイアウトから、こちらが送っていない変化を取り込む。
     *
     * [onNotesLayoutChanged] はレイアウトの後に遅れて届くので、先にスクロールが来ると
     * その変化を送った量と取り違える。スクロールの前にも呼ぶ
     */
    private fun syncNotesLayout(notesListState: LazyListState) {
        absorbNotesDrift(notesListState)
        trackNotesItems(notesListState)
        collapseHeaderAwayFromPageTop(notesListState)
    }

    /**
     * LazyColumn が自分で動かしたスクロール位置を取り込む。
     *
     * 末尾が縮んだり表示領域が広がったりすると、LazyColumn は要求していなくても
     * スクロール位置を詰める。こちらの持ち高だけで両ペインを置くと、その分だけ
     * 配信元のカラムが取り残されてずれる。動いた分はまずカラムのずらし量で打ち消し、
     * 打ち消しきれない分だけページごと動かす
     */
    private fun absorbNotesDrift(notesListState: LazyListState) {
        val anchor = notesListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == notesAnchorKey } ?: return
        val drift = (notesAnchorOffsetPx - anchor.offset).toFloat()
        val absorbed = drift.coerceAtMost(notesOverflowPx)
        notesOverflowPx -= absorbed
        contentOffsetPx += drift - absorbed
    }

    /**
     * 画面の上から入ってきた投稿の高さが前に測ったときと違えば、その差だけ位置を直す。
     *
     * 画面外の投稿は測り直されないので、フォントの読み込みなどで高さが変わっても、
     * 戻ってきて測られるまで分からない。送った量だけで持つと差が積み上がり、
     * 先頭に戻ったときに配信元のカラムだけずれて残る。
     * 測ったことのない投稿（画面外で増えたもの）の分は直せないので、先頭の投稿が見えたら
     * 実際の位置に合わせる
     */
    private fun trackNotesItems(notesListState: LazyListState) {
        val visibleItems = notesListState.layoutInfo.visibleItemsInfo
        val anchorIndex = visibleItems.firstOrNull { it.key == notesAnchorKey }?.index
        if (anchorIndex != null) {
            contentOffsetPx += visibleItems
                .takeWhile { it.index < anchorIndex }
                .sumOf { item -> item.size - (measuredNoteHeights[item.key] ?: item.size) }
        }
        val firstVisible = visibleItems.firstOrNull()
        if (firstVisible?.index == 0) {
            contentOffsetPx = notesOverflowPx - firstVisible.offset
        }
        visibleItems.forEach { measuredNoteHeights[it.key] = it.size }
        notesAnchorKey = firstVisible?.key
        notesAnchorOffsetPx = firstVisible?.offset ?: 0
    }

    /**
     * ヘッダーを出すのは、投稿が先頭にあってずらしてもいないときだけにする。
     *
     * 戻る操作などで投稿の位置だけが復元されると、ヘッダーが出たまま投稿が途中から始まる
     */
    private fun collapseHeaderAwayFromPageTop(notesListState: LazyListState) {
        val notesAtTop = notesListState.firstVisibleItemIndex == 0 && notesListState.firstVisibleItemScrollOffset == 0
        if (!notesAtTop || notesOverflowPx > 0f) {
            headerCollapsePx = headerHeightPx.toFloat()
        }
    }

    /**
     * 終端超過分を LazyColumn のスクロールへ戻す。
     *
     * 投稿が尽きた先はカラムごとずらしている。下に伸びた分は LazyColumn 側で
     * 吸収できるので、ずらし量を減らして両ペインの位置を揃える。
     */
    private fun resyncNotesOverflow(notesListState: LazyListState) {
        if (notesOverflowPx <= 0f) return
        val notesBelow = notesBelowViewportPx(notesListState)
        if (notesBelow <= 0f) return
        val target = when (notesBelow) {
            Float.POSITIVE_INFINITY -> notesOverflowPx
            else -> notesOverflowPx.coerceAtMost(notesBelow)
        }
        if (target <= 0f) return
        val scrolled = notesListState.dispatchRawDelta(target)
        notesOverflowPx -= scrolled
        trackNotesItems(notesListState)
    }

    fun scrollBy(delta: Float, notesListState: LazyListState): Float {
        syncNotesLayout(notesListState)
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
        trackNotesItems(notesListState)
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

    LaunchedEffect(pageScrollState, notesListState) {
        snapshotFlow { notesListState.layoutInfo }
            .collect { pageScrollState.onNotesLayoutChanged(notesListState) }
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
