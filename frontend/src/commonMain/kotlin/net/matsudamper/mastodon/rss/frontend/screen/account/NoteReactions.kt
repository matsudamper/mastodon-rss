package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 投稿に届いたお気に入りとスタンプ。
 *
 * 押した相手は出さない。こちらが記録しているのは数だけで、誰が押したかは
 * 相手のサーバー側に残る情報として扱っている。
 */
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
            ReactionChip(count = uiState.favouriteCount) {
                Text(
                    text = FAVOURITE_MARK,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        uiState.stamps.forEach { reaction ->
            ReactionChip(count = reaction.count) {
                ReactionEmoji(reaction)
            }
        }
    }
}

/**
 * スタンプの絵文字。カスタム絵文字は画像で、読めなければ名前を出す。
 *
 * 画像が出せないときに空の枠だけが残ると、何が押されたのか分からなくなる
 */
@Composable
private fun ReactionEmoji(reaction: NoteReactionUiState) {
    var imageFailed by remember(reaction.imageUrl) { mutableStateOf(false) }

    if (reaction.imageUrl == null || imageFailed) {
        Text(
            text = reaction.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        AsyncImage(
            model = reaction.imageUrl,
            // 絵文字の名前は隣に出ないので、読み上げるものとして名前を渡す
            contentDescription = reaction.name,
            modifier = Modifier.size(20.dp),
            onError = { imageFailed = true },
        )
    }
}

@Composable
private fun ReactionChip(
    count: String,
    emoji: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emoji()
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * お気に入りには絵文字が付かないので、代わりに出す印
 */
private const val FAVOURITE_MARK = "★"
