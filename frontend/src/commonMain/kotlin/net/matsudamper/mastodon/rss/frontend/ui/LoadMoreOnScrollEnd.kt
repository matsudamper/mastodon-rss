package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/**
 * verticalScroll の一覧で、下端の近くまでスクロールされたら続きを取りに行く。
 * LazyColumn と違い末尾の枠も最初から組まれるので、組まれたことは合図にならない
 *
 * @param itemCount 1 ページ足された後も下端にいるなら、件数が変わったのを合図にもう 1 ページ取る
 */
@Composable
internal fun LoadMoreOnScrollEnd(
    scrollState: ScrollState,
    loadMoreOnVisible: Boolean,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val thresholdPx = with(LocalDensity.current) { 200.dp.roundToPx() }
    LaunchedEffect(scrollState, loadMoreOnVisible, itemCount, thresholdPx) {
        if (loadMoreOnVisible) {
            snapshotFlow { scrollState.maxValue - scrollState.value <= thresholdPx }
                .first { reachedEnd -> reachedEnd }
            currentOnLoadMore()
        }
    }
}
