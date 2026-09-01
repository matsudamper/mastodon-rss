package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.navigation.NavigationHandler
import net.matsudamper.mastodon.rss.frontend.navigation.rememberScreenNavigator
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold

@Composable
internal fun AdminAccountScreen(
    username: String,
    platform: ScreenPlatform,
    navigationEvents: EventSender<NavigationHandler>,
) {
    val viewModelScope = rememberCoroutineScope()
    val navigator = rememberScreenNavigator(navigationEvents)
    val viewModel = remember(username, viewModelScope, navigator) {
        AdminAccountScreenViewModel(
            username = username,
            viewModelScope = viewModelScope,
            navigator = navigator,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminAccountContent(
        uiState = uiState,
        username = username,
        platform = platform,
    )
}

@Composable
internal fun AdminAccountContent(
    uiState: AdminAccountScreenUiState,
    username: String,
    platform: ScreenPlatform,
) {
    var showPostDialog by remember(username) { mutableStateOf(false) }
    var autoLoadAttemptedAtItemCount by remember(username) { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    AdminScaffold(
        title = "@$username の管理",
        listener = uiState.listener,
    ) { wide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.acct,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (uiState.content is AdminAccountScreenUiState.Content.Loaded) {
                    Button(onClick = { showPostDialog = true }) { Text("新しい投稿") }
                }
            }

            when (val content = uiState.content) {
                AdminAccountScreenUiState.Content.Loading -> AdminSectionCard(title = "読み込み中") {
                    Text("アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }

                AdminAccountScreenUiState.Content.RequireLogin -> RequireLoginCard(onClickAdmin = uiState.listener::onClickAdmin)

                AdminAccountScreenUiState.Content.NotFound -> AdminSectionCard(title = "このアカウントは無い") {
                    Text("この名前では Mastodon からも見つからない。", style = MaterialTheme.typography.bodyMedium)
                    AdminTextLink(text = "アカウントの一覧に戻る", onClick = uiState.listener::onClickBackToAdmin)
                }

                is AdminAccountScreenUiState.Content.Error -> AdminSectionCard(title = "この画面を出せない") {
                    Text(content.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { uiState.listener.onClickReload() }) { Text("もう一度試す") }
                }

                is AdminAccountScreenUiState.Content.Loaded -> {
                    AutoLoadMoreNotes(
                        content = content,
                        scrollState = scrollState,
                        attemptedAtItemCount = autoLoadAttemptedAtItemCount,
                        onAttempted = { autoLoadAttemptedAtItemCount = it },
                        onLoadMore = uiState.listener::onClickLoadMore,
                    )
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            NotesSection(
                                content = content,
                                listener = uiState.listener,
                                noteContent = platform::NoteContent,
                                modifier = Modifier.weight(3f),
                            )
                            Column(
                                modifier = Modifier.weight(2f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AccountCard(
                                    account = content.account,
                                    onClickOpenAccount = uiState.listener::onClickOpenAccount,
                                )
                                FeedCard(content.feed, uiState.listener)
                            }
                        }
                    } else {
                        AccountCard(
                            account = content.account,
                            onClickOpenAccount = uiState.listener::onClickOpenAccount,
                        )
                        FeedCard(content.feed, uiState.listener)
                        NotesSection(content, uiState.listener, platform::NoteContent)
                    }
                    if (showPostDialog) {
                        PostDialog(
                            post = content.post,
                            listener = uiState.listener,
                            onDismissRequest = { showPostDialog = false },
                        )
                    }
                    content.deleteNoteDialog?.let { DeleteNoteDialog(it, uiState.listener) }
                }
            }
        }
    }
}

@Composable
private fun AutoLoadMoreNotes(
    content: AdminAccountScreenUiState.Content.Loaded,
    scrollState: androidx.compose.foundation.ScrollState,
    attemptedAtItemCount: Int?,
    onAttempted: (Int?) -> Unit,
    onLoadMore: () -> Unit,
) {
    val loadMoreThreshold = with(LocalDensity.current) { 240.dp.roundToPx() }

    LaunchedEffect(content.notesLoading) {
        if (content.notesLoading) onAttempted(null)
    }
    LaunchedEffect(
        scrollState,
        content.notes.size,
        content.canLoadMore,
        content.loadingMore,
        content.notesLoading,
        attemptedAtItemCount,
    ) {
        snapshotFlow { scrollState.value >= scrollState.maxValue - loadMoreThreshold }
            .collect { nearBottom ->
                val itemCount = content.notes.size
                if (
                    nearBottom &&
                    itemCount > 0 &&
                    content.canLoadMore &&
                    !content.loadingMore &&
                    !content.notesLoading &&
                    attemptedAtItemCount != itemCount
                ) {
                    onAttempted(itemCount)
                    onLoadMore()
                }
            }
    }
}

@Composable
private fun AdminSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AdminTextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
private fun AccountCard(account: AdminAccountScreenUiState.Account, onClickOpenAccount: () -> Unit) {
    AdminSectionCard(title = "このアカウント") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("フォロワー", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${account.followerCount} 人", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onClickOpenAccount) { Text("公開画面") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabeledValue(label = "Actor URL", value = account.actorUrl)
        LabeledValue(label = "追加日時", value = account.createdAt)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeedCard(feed: AdminAccountScreenUiState.Feed, listener: AdminAccountScreenUiState.Listener) {
    when (feed) {
        is AdminAccountScreenUiState.Feed.Registered -> AdminSectionCard(title = "RSS フィード") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    feed.title ?: "登録済みフィード",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                feed.format?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Text(feed.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                feed.postedItems?.let { FeedItemSummary("今回投稿した記事 ${it.size} 件", it) }
                if (feed.unpublishedItems.isNotEmpty()) FeedItemSummary("未投稿の記事 ${feed.unpublishedItems.size} 件", feed.unpublishedItems)
                feed.unpublishedError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { listener.onClickPostLatest() }, enabled = !feed.postingUnpublished) {
                        Text(if (feed.postingUnpublished) "投稿中" else "最新情報を投稿")
                    }
                }
            }
        }

        is AdminAccountScreenUiState.Feed.Input -> AdminSectionCard(title = "RSS フィード") {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeedInputPanel(feed, listener)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FeedPreviewPanel(feed)
            }
        }
    }
}

@Composable
private fun FeedItemSummary(countText: String, items: List<AdminAccountScreenUiState.UnpublishedItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(countText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        items.take(5).forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title ?: "(題名なし)", style = MaterialTheme.typography.bodySmall)
                listOfNotNull(item.publishedAt, item.link).joinToString("  ").takeIf(String::isNotEmpty)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FeedInputPanel(feed: AdminAccountScreenUiState.Feed.Input, listener: AdminAccountScreenUiState.Listener, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RSS/Atom の URL を入れて取得し、登録する。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = feed.url,
            onValueChange = listener::onFeedUrlChanged,
            enabled = !feed.fetching && !feed.saving,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("フィード URL") },
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = listener::onClickFetchFeed, enabled = feed.canFetch) { Text(if (feed.fetching) "取得中" else "取得") }
        }
        feed.preview?.let { preview ->
            Text(if (preview.itemCount > 0) "このフィードには記事が ${preview.itemCount} 件ある。" else "このフィードには記事が無い。", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = listener::onClickSaveFeed, enabled = feed.canSave) { Text(if (feed.saving) "登録中" else "登録する") }
            }
        }
        feed.previewError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        feed.saveError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FeedPreviewPanel(feed: AdminAccountScreenUiState.Feed.Input, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("プレビュー", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        when {
            feed.fetching -> Text("フィードを取ってきている。", style = MaterialTheme.typography.bodyMedium)

            feed.preview != null -> {
                val preview = feed.preview
                preview.title?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
                Text(preview.format, style = MaterialTheme.typography.bodySmall)
                preview.siteUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                preview.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("記事 ${preview.itemCount} 件", style = MaterialTheme.typography.bodyMedium)
                preview.sampleItems.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title ?: "(題名なし)", style = MaterialTheme.typography.bodyMedium)
                        listOfNotNull(item.publishedAt, item.link).joinToString("  ").takeIf(String::isNotEmpty)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            else -> Text("取得ボタンを押すとここに表示される。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeleteNoteDialog(dialog: AdminAccountScreenUiState.DeleteNoteDialog, listener: AdminAccountScreenUiState.Listener) {
    AlertDialog(
        onDismissRequest = listener::onDismissDeleteNote,
        title = { Text("投稿を削除する") },
        text = {
            Text(
                if (dialog.hasSourceArticle) "フォロワーのサーバーにも削除を配る。届かなかった相手には残る。\n元の記事も消すと、最新情報を投稿したときに取り込み直してもう一度流れる。投稿だけ消すと、その記事はもう流れない。" else "フォロワーのサーバーにも削除を配る。届かなかった相手には残る。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { listener.onConfirmDeleteNote(dialog.hasSourceArticle) }, enabled = !dialog.deleting) {
                Text(
                    if (dialog.deleting) {
                        "削除中"
                    } else if (dialog.hasSourceArticle) {
                        "投稿と記事を削除"
                    } else {
                        "削除"
                    },
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (dialog.hasSourceArticle) TextButton(onClick = { listener.onConfirmDeleteNote(false) }, enabled = !dialog.deleting) { Text("投稿だけ削除") }
                TextButton(onClick = listener::onDismissDeleteNote, enabled = !dialog.deleting) { Text("やめる") }
            }
        },
    )
}

@Composable
private fun PostDialog(
    post: AdminAccountScreenUiState.Post,
    listener: AdminAccountScreenUiState.Listener,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!post.submitting) onDismissRequest() },
        title = { Text("新しい投稿") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("このアカウントのフォロワーに配る。段落と改行は投稿用の HTML に変換される。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = post.body,
                    onValueChange = listener::onBodyChanged,
                    enabled = !post.submitting,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本文") },
                    minLines = 5,
                    maxLines = 12,
                )
                post.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                post.result?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("投稿した。宛先 ${result.targets} 件のうち ${result.delivered} 件に届いた。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(result.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = listener::onClickPost, enabled = post.canSubmit) {
                Text(if (post.submitting) "配信中" else "投稿する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !post.submitting) { Text("閉じる") }
        },
    )
}

@Composable
private fun NotesSection(
    content: AdminAccountScreenUiState.Content.Loaded,
    listener: AdminAccountScreenUiState.Listener,
    noteContent: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("配信した投稿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        when {
            content.notesLoading && content.notes.isEmpty() -> NotesMessageCard {
                Text("配信した投稿を取ってきている。", style = MaterialTheme.typography.bodyMedium)
            }

            content.notes.isEmpty() && content.notesError != null -> NotesMessageCard {
                Text(content.notesError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = listener::onClickReloadNotes) { Text("もう一度試す") }
                }
            }

            content.notes.isEmpty() -> NotesMessageCard {
                Text("まだ投稿していない。", style = MaterialTheme.typography.bodyMedium)
            }

            else -> {
                content.notes.forEach { note ->
                    key(note.url) {
                        NoteCard(note = note, noteContent = noteContent)
                    }
                }
                content.notesError?.let {
                    NotesMessageCard {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = listener::onClickReloadNotes) { Text("もう一度試す") }
                        }
                    }
                }
                if (content.loadingMore) {
                    Text(
                        "続きを読み込んでいる。",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else if (!content.canLoadMore && content.notesError == null) {
                    Text(
                        "これ以上投稿はない。",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesMessageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun NoteCard(
    note: AdminAccountScreenUiState.Note,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledValue(label = "配信日時", value = note.publishedAt)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            noteContent(note.contentHtml, Modifier.fillMaxWidth())
            note.sourceArticle?.let { article -> NoteSourceArticle(article) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LabeledValue(label = "投稿URL", value = note.url)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = note.listener::onClickDelete) { Text("投稿を削除") }
            }
        }
    }
}

@Composable
private fun NoteSourceArticle(article: AdminAccountScreenUiState.SourceArticle) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("元の記事", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(article.title ?: "(題名なし)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            listOfNotNull(article.publishedAt, article.link).joinToString("  ").takeIf(String::isNotEmpty)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = article.listener::onClickDelete, enabled = !article.deleting) {
                    Text(if (article.deleting) "記事を削除中" else "記事を削除")
                }
            }
        }
    }
}
