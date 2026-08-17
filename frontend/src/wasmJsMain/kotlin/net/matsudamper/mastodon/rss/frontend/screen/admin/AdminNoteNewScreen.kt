package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
fun AdminNoteNewScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminNoteNewScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminNoteNewScreen(uiState = uiState, onNavigate = onNavigate)
}

@Composable
private fun AdminNoteNewScreen(
    uiState: AdminNoteNewScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "投稿",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminNoteNewScreenUiState.Content.Loading -> {
                SectionCard(title = "読み込み中") {
                    Text(text = "アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }
            }

            AdminNoteNewScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onNavigate = onNavigate)
            }

            is AdminNoteNewScreenUiState.Content.Error -> {
                SectionCard(title = "投稿できない") {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is AdminNoteNewScreenUiState.Content.Input -> {
                InputCard(content = content, listener = uiState.listener)
                PostedCard(content = content, listener = uiState.listener)
            }
        }
    }
}

@Composable
private fun InputCard(
    content: AdminNoteNewScreenUiState.Content.Input,
    listener: AdminNoteNewScreenUiState.Listener,
) {
    SectionCard(title = "新しい投稿") {
        Text(
            text = "選んだアカウントのフォロワーに配る。プレーンテキストで書くと、" +
                "段落と改行だけの HTML にして送る。",
            style = MaterialTheme.typography.bodyMedium,
        )

        // どのアカウントから流れるかは相手のタイムラインでの見え方そのものなので、
        // 選んでいるものが常に見えている形にする
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content.accounts.forEach { account ->
                FilterChip(
                    selected = account.username == content.selectedUsername,
                    onClick = { listener.onAccountSelected(account.username) },
                    enabled = !content.submitting,
                    label = { Text(account.acct) },
                )
            }
        }

        OutlinedTextField(
            value = content.body,
            onValueChange = { listener.onBodyChanged(it) },
            enabled = !content.submitting,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("本文") },
            minLines = 4,
        )

        if (content.error != null) {
            Text(
                text = content.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { listener.onClickPost() },
                enabled = content.canSubmit,
            ) {
                Text(if (content.submitting) "配信中" else "投稿する")
            }
        }

        val result = content.result
        if (result != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 記録と配信は別。フォロワーが 0 人でも投稿自体は成立するし、
                // 相手が受け取らなくてもこちらの記録は残る
                Text(
                    text = "投稿した。宛先 ${result.targets} 件のうち ${result.delivered} 件に届いた。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // 外部リンクを開く口がまだ無いので URL は文字として出す。
                // Mastodon 側から開くときは相手がこの URL を引きに来る
                Text(
                    text = result.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostedCard(
    content: AdminNoteNewScreenUiState.Content.Input,
    listener: AdminNoteNewScreenUiState.Listener,
) {
    SectionCard(title = "配信した投稿") {
        val notes = content.notes
        if (notes.isEmpty()) {
            Text(text = "まだ何も配信していない。", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }

        notes.forEach { note ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = note.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = note.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (content.canLoadMore) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { listener.onClickLoadMore() },
                    enabled = !content.loadingMore,
                ) {
                    Text(if (content.loadingMore) "読み込み中" else "もっと見る")
                }
            }
        }
    }
}
