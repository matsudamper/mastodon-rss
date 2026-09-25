package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun NoteStampCount(
    stamp: NoteReactionsUiState.Stamp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "スタンプ ${stamp.name} の数: ${stamp.count}" },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StampEmoji(stamp)
            Text(
                text = stamp.count,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 画像が出せないときに空の枠だけが残ると、何が押されたのか分からなくなるので名前を出す
 */
@Composable
private fun StampEmoji(stamp: NoteReactionsUiState.Stamp) {
    var imageFailed by remember(stamp.imageUrl) { mutableStateOf(false) }

    val imageUrl = stamp.imageUrl
    if (imageUrl == null || imageFailed) {
        Text(
            text = stamp.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        ExternalImage(
            url = imageUrl,
            contentDescription = stamp.name,
            onError = { imageFailed = true },
            modifier = Modifier.size(20.dp),
        )
    }
}
