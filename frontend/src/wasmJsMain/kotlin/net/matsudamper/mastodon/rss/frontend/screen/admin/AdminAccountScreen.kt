package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
fun AdminAccountScreen(
    username: String,
    onNavigate: (Screen) -> Unit,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) {
        AdminAccountScreenViewModel(username = username, viewModelScope = viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(username) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminAccountScreen(
        screen = Screen.AdminAccount(username),
        uiState = uiState,
        onNavigate = onNavigate,
    )
}

@Composable
private fun AdminAccountScreen(
    screen: Screen.AdminAccount,
    uiState: AdminAccountScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(screen = screen, onNavigate = onNavigate) { _ ->
        Text(
            text = uiState.acct,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminAccountScreenUiState.Content.Loading -> {
                SectionCard(title = "読み込み中") {
                    Text(text = "アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }
            }

            AdminAccountScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onNavigate = onNavigate)
            }

            AdminAccountScreenUiState.Content.NotFound -> {
                SectionCard(title = "このアカウントは無い") {
                    Text(
                        text = "この名前では Mastodon からも見つからない。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextLink(
                        text = "アカウントの一覧に戻る",
                        onClick = { onNavigate(Screen.AdminAccounts) },
                    )
                }
            }

            is AdminAccountScreenUiState.Content.Error -> {
                SectionCard(title = "この画面を出せない") {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { uiState.listener.onClickReload() }) {
                            Text("もう一度試す")
                        }
                    }
                }
            }

            is AdminAccountScreenUiState.Content.Loaded -> {
                AccountCard(account = content.account, onNavigate = onNavigate)
                PostCard(post = content.post, listener = uiState.listener)
                NotesCard(content = content, listener = uiState.listener)
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AdminAccountScreenUiState.Account,
    onNavigate: (Screen) -> Unit,
) {
    SectionCard(title = "このアカウント") {
        Text(
            text = "フォロワー ${account.followerCount} 人",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = account.actorUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (account.createdAt != null) {
            Text(
                text = "追加: ${account.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextLink(
            text = "公開されているアカウント画面を開く",
            onClick = { onNavigate(Screen.Account(account.username)) },
        )
    }
}

@Composable
private fun PostCard(
    post: AdminAccountScreenUiState.Post,
    listener: AdminAccountScreenUiState.Listener,
) {
    SectionCard(title = "新しい投稿") {
        Text(
            text = "このアカウントのフォロワーに配る。プレーンテキストで書くと、" +
                "段落と改行だけの HTML にして送る。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = post.body,
            onValueChange = { listener.onBodyChanged(it) },
            enabled = !post.submitting,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("本文") },
            minLines = 4,
        )

        if (post.error != null) {
            Text(
                text = post.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { listener.onClickPost() },
                enabled = post.canSubmit,
            ) {
                Text(if (post.submitting) "配信中" else "投稿する")
            }
        }

        val result = post.result
        if (result != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "投稿した。宛先 ${result.targets} 件のうち ${result.delivered} 件に届いた。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // 外部リンクを開く口がまだ無いので URL は文字として出す
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
private fun NotesCard(
    content: AdminAccountScreenUiState.Content.Loaded,
    listener: AdminAccountScreenUiState.Listener,
) {
    SectionCard(title = "配信した投稿") {
        val notes = content.notes
        val error = content.notesError

        when {
            content.notesLoading && notes.isEmpty() -> {
                Text(
                    text = "配信した投稿を取ってきている。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            notes.isEmpty() && error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { listener.onClickReloadNotes() }) {
                        Text("もう一度試す")
                    }
                }
            }

            notes.isEmpty() -> {
                Text(
                    text = "まだ投稿していない。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                notes.forEach { note ->
                    key(note.url) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            NoteContent(
                                contentHtml = note.contentHtml,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${note.publishedAt}  ${note.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { listener.onClickReloadNotes() }) {
                            Text("もう一度試す")
                        }
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
    }
}
