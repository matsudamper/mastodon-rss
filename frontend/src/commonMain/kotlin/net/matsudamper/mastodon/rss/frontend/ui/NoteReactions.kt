package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteReactions(
    uiState: NoteReactionsUiState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.favouriteCount != null) {
            NoteFavouriteCount(count = uiState.favouriteCount)
        }
        uiState.stamps.forEach { stamp ->
            NoteStampCount(stamp = stamp)
        }
    }
}
