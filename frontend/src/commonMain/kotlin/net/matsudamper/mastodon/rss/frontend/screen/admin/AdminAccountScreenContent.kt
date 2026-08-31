package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
internal fun AdminAccountScreenContent(
    username: String,
    uiState: AdminAccountScreenUiState,
    wide: Boolean,
    onClickOpenAccount: () -> Unit,
    onClickLogin: () -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    Column {
        Text(
            text = uiState.acct,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminAccountScreenUiState.Content.Loading -> AdminSectionCard(title = "読み込み中") {
                Text("アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
            }

            AdminAccountScreenUiState.Content.RequireLogin -> AdminRequireLoginCard(onClickLogin)

            AdminAccountScreenUiState.Content.NotFound -> AdminSectionCard(title = "このアカウントは無い") {
                Text("この名前では Mastodon からも見つからない。", style = MaterialTheme.typography.bodyMedium)
                AdminTextLink(text = "アカウントの一覧に戻る", onClick = onClickLogin)
            }

            is AdminAccountScreenUiState.Content.Error -> AdminSectionCard(title = "この画面を出せない") {
                Text(content.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { uiState.listener.onClickReload() }) { Text("もう一度試す") }
            }

            is AdminAccountScreenUiState.Content.Loaded -> {
                AccountCard(content.account, onClickOpenAccount)
                FeedCard(content.feed, uiState.listener, wide)
                PostCard(content.post, uiState.listener)
                NotesCard(content, uiState.listener, noteContent)
                content.deleteNoteDialog?.let { DeleteNoteDialog(it, uiState.listener) }
            }
        }
    }
}

@Composable
private fun AdminSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun AdminRequireLoginCard(onClickLogin: () -> Unit) {
    AdminSectionCard(title = "ログインが要る") {
        Text("管理画面のトップでログインしてから開く。", style = MaterialTheme.typography.bodyMedium)
        AdminTextLink(text = "管理画面のトップへ", onClick = onClickLogin)
    }
}

@Composable
private fun AccountCard(account: AdminAccountScreenUiState.Account, onClickOpenAccount: () -> Unit) {
    AdminSectionCard(title = "このアカウント") {
        Text("フォロワー ${account.followerCount} 人", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(account.actorUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("追加: ${account.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdminTextLink(text = "公開されているアカウント画面を開く", onClick = onClickOpenAccount)
    }
}

@Composable
private fun FeedCard(feed: AdminAccountScreenUiState.Feed, listener: AdminAccountScreenUiState.Listener, wide: Boolean) {
    when (feed) {
        is AdminAccountScreenUiState.Feed.Registered -> AdminSectionCard(title = "RSS フィード") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("登録済み", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(feed.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                feed.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                feed.format?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = { listener.onClickPostLatest() }, enabled = !feed.postingUnpublished) {
                    Text(if (feed.postingUnpublished) "投稿中" else "最新情報を投稿")
                }
                feed.postedItems?.let { FeedItemSummary("${it.size} 件投稿しました。", it) }
                if (feed.unpublishedItems.isNotEmpty()) FeedItemSummary("未投稿の記事が ${feed.unpublishedItems.size} 件ある。", feed.unpublishedItems)
                feed.unpublishedError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }

        is AdminAccountScreenUiState.Feed.Input -> AdminSectionCard(title = "RSS フィード") {
            if (wide) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeedInputPanel(feed, listener, Modifier.weight(1f))
                    FeedPreviewPanel(feed, Modifier.weight(1f))
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeedInputPanel(feed, listener)
                    FeedPreviewPanel(feed)
                }
            }
        }
    }
}

@Composable
private fun FeedItemSummary(countText: String, items: List<AdminAccountScreenUiState.UnpublishedItem>) {
    Text(countText, style = MaterialTheme.typography.bodyMedium)
    items.take(5).forEach { item ->
        Text(item.title ?: item.link.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Button(onClick = listener::onClickFetchFeed, enabled = feed.canFetch) { Text(if (feed.fetching) "取得中" else "取得") }
        feed.preview?.let { preview ->
            Text(if (preview.itemCount > 0) "このフィードには記事が ${preview.itemCount} 件ある。" else "このフィードには記事が無い。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = listener::onClickSaveFeed, enabled = feed.canSave) { Text(if (feed.saving) "登録中" else "登録する") }
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
                Text(if (dialog.deleting) "削除中" else if (dialog.hasSourceArticle) "投稿と記事を削除" else "削除")
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
private fun PostCard(post: AdminAccountScreenUiState.Post, listener: AdminAccountScreenUiState.Listener) {
    AdminSectionCard(title = "新しい投稿") {
        Text("このアカウントのフォロワーに配る。プレーンテキストで書くと、段落と改行だけの HTML にして送る。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(post.body, listener::onBodyChanged, enabled = !post.submitting, modifier = Modifier.fillMaxWidth(), label = { Text("本文") }, minLines = 4)
        post.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Button(onClick = listener::onClickPost, enabled = post.canSubmit) { Text(if (post.submitting) "配信中" else "投稿する") }
        post.result?.let { result ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("投稿した。宛先 ${result.targets} 件のうち ${result.delivered} 件に届いた。", style = MaterialTheme.typography.bodyMedium)
                Text(result.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotesCard(content: AdminAccountScreenUiState.Content.Loaded, listener: AdminAccountScreenUiState.Listener, noteContent: @Composable (String, Modifier) -> Unit) {
    AdminSectionCard(title = "配信した投稿") {
        when {
            content.notesLoading && content.notes.isEmpty() -> Text("配信した投稿を取ってきている。", style = MaterialTheme.typography.bodyMedium)
            content.notes.isEmpty() && content.notesError != null -> {
                Text(content.notesError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = listener::onClickReloadNotes) { Text("もう一度試す") }
            }
            content.notes.isEmpty() -> Text("まだ投稿していない。", style = MaterialTheme.typography.bodyMedium)
            else -> {
                content.notes.forEach { note ->
                    key(note.url) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            noteContent(note.contentHtml, Modifier.fillMaxWidth())
                            Text("${note.publishedAt}  ${note.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            note.sourceArticle?.let { article -> NoteSourceArticle(article) }
                            OutlinedButton(onClick = note.listener::onClickDelete) { Text("投稿を削除") }
                        }
                    }
                }
                content.notesError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = listener::onClickReloadNotes) { Text("もう一度試す") }
                }
                if (content.canLoadMore) OutlinedButton(onClick = listener::onClickLoadMore, enabled = !content.loadingMore) { Text(if (content.loadingMore) "読み込み中" else "もっと見る") }
            }
        }
    }
}

@Composable
private fun NoteSourceArticle(article: AdminAccountScreenUiState.SourceArticle) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("元の記事: ${article.title ?: "(題名なし)"}", style = MaterialTheme.typography.bodySmall)
        Text(listOfNotNull(article.publishedAt, article.link).joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = article.listener::onClickDelete, enabled = !article.deleting) { Text(if (article.deleting) "記事を削除中" else "記事を削除") }
    }
}

internal fun previewUiState() = AdminAccountScreenUiState(
    acct = "@rss_news@example.com",
    content = AdminAccountScreenUiState.Content.Loaded(
        account = AdminAccountScreenUiState.Account("rss_news", "@rss_news@example.com", "https://example.com/users/rss_news", "2026-08-31 12:00", 42),
        feed = AdminAccountScreenUiState.Feed.Registered(
            url = "https://example.com/news.xml",
            title = "サンプルニュース",
            format = "RSS 2.0",
            unpublishedItems = listOf(AdminAccountScreenUiState.UnpublishedItem("まだ投稿していない記事", "https://example.com/news/1", "2026-09-01 09:00")),
            postedItems = null,
            postingUnpublished = false,
            unpublishedError = null,
        ),
        post = AdminAccountScreenUiState.Post("新しい記事を投稿する。", submitting = false, result = null, error = null),
        notes = listOf(
            AdminAccountScreenUiState.Note(
                url = "https://example.com/notes/1",
                contentHtml = "<p>配信済みの記事の本文。</p>",
                publishedAt = "2026-09-01 08:00",
                sourceArticle = AdminAccountScreenUiState.SourceArticle("配信済みの記事", "https://example.com/news/0", "2026-09-01 07:30", deleting = false, listener = PreviewSourceArticleListener),
                listener = PreviewNoteListener,
            ),
        ),
        deleteNoteDialog = null,
        notesError = null,
        notesLoading = false,
        canLoadMore = true,
        loadingMore = false,
    ),
    listener = PreviewListener,
)

private object PreviewSourceArticleListener : AdminAccountScreenUiState.SourceArticleListener {
    override fun onClickDelete() = Unit
}

private object PreviewNoteListener : AdminAccountScreenUiState.NoteListener {
    override fun onClickDelete() = Unit
}

private object PreviewListener : AdminAccountScreenUiState.Listener {
    override fun onFeedUrlChanged(text: String) = Unit
    override fun onClickFetchFeed() = Unit
    override fun onClickSaveFeed() = Unit
    override fun onClickPostLatest() = Unit
    override fun onBodyChanged(text: String) = Unit
    override fun onClickPost() = Unit
    override fun onClickLoadMore() = Unit
    override fun onDismissDeleteNote() = Unit
    override fun onConfirmDeleteNote(deleteSourceArticle: Boolean) = Unit
    override fun onClickReloadNotes() = Unit
    override fun onClickReload() = Unit
}
