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
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
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
        username = username,
        uiState = uiState,
        onNavigate = onNavigate,
    )
}

@Composable
private fun AdminAccountScreen(
    username: String,
    uiState: AdminAccountScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AdminScaffold(title = "@$username の管理", onNavigate = onNavigate) { wide ->
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
                FeedCard(feed = content.feed, listener = uiState.listener, wide = wide)
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

        Text(
            text = "追加: ${account.createdAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextLink(
            text = "公開されているアカウント画面を開く",
            onClick = { onNavigate(Screen.Account(account.username)) },
        )
    }
}

@Composable
private fun FeedCard(
    feed: AdminAccountScreenUiState.Feed,
    listener: AdminAccountScreenUiState.Listener,
    wide: Boolean,
) {
    when (feed) {
        is AdminAccountScreenUiState.Feed.Registered -> {
            SectionCard(title = "RSS フィード") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "登録済み",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = feed.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (feed.title != null) {
                        Text(
                            text = feed.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (feed.format != null) {
                        Text(
                            text = feed.format,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (feed.unpublishedItems.isNotEmpty()) {
                        Text(
                            text = "未投稿の記事が ${feed.unpublishedItems.size} 件ある。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        feed.unpublishedItems.take(5).forEach { item ->
                            Text(
                                text = item.title ?: item.link.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { listener.onClickPostUnpublished() },
                            enabled = !feed.postingUnpublished,
                        ) {
                            Text(if (feed.postingUnpublished) "投稿中" else "未投稿を投稿する")
                        }
                    }
                    if (feed.unpublishedError != null) {
                        Text(
                            text = feed.unpublishedError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        is AdminAccountScreenUiState.Feed.Input -> {
            SectionCard(title = "RSS フィード") {
                if (wide) {
                    // 入力欄とプレビューを見比べられる幅がある時だけ横に並べる
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FeedInputPanel(modifier = Modifier.weight(1f), feed = feed, listener = listener)
                        FeedPreviewPanel(modifier = Modifier.weight(1f), feed = feed)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FeedInputPanel(modifier = Modifier.fillMaxWidth(), feed = feed, listener = listener)
                        FeedPreviewPanel(modifier = Modifier.fillMaxWidth(), feed = feed)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedInputPanel(
    feed: AdminAccountScreenUiState.Feed.Input,
    listener: AdminAccountScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "RSS/Atom の URL を入れて取得し、登録する。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = feed.url,
            onValueChange = { listener.onFeedUrlChanged(it) },
            enabled = !feed.fetching && !feed.saving,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("フィード URL") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { listener.onClickFetchFeed() },
                enabled = feed.canFetch,
            ) {
                Text(if (feed.fetching) "取得中" else "取得")
            }
        }

        val preview = feed.preview
        if (preview != null) {
            Text(
                text = if (preview.itemCount > 0) {
                    "このフィードには記事が ${preview.itemCount} 件ある。"
                } else {
                    "このフィードには記事が無い。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { listener.onClickSaveFeed() },
                enabled = feed.canSave,
            ) {
                Text(if (feed.saving) "登録中" else "登録する")
            }
        }

        if (feed.previewError != null) {
            Text(
                text = feed.previewError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (feed.saveError != null) {
            Text(
                text = feed.saveError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FeedPreviewPanel(
    feed: AdminAccountScreenUiState.Feed.Input,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "プレビュー",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            feed.fetching -> {
                Text(text = "フィードを取ってきている。", style = MaterialTheme.typography.bodyMedium)
            }

            feed.preview != null -> {
                val preview = feed.preview
                preview.title?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(text = preview.format, style = MaterialTheme.typography.bodySmall)
                preview.siteUrl?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                preview.description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(text = "記事 ${preview.itemCount} 件", style = MaterialTheme.typography.bodyMedium)
                preview.sampleItems.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.title ?: "(題名なし)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val meta = listOfNotNull(item.publishedAt, item.link).joinToString("  ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = "取得ボタンを押すとここに表示される。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
