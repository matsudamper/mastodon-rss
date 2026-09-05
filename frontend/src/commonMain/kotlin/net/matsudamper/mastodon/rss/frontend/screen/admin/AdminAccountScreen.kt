package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.CoordinatedTwoPaneLayout
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.SnackbarHostState
import net.matsudamper.mastodon.rss.frontend.ui.TwoPaneScrollState
import net.matsudamper.mastodon.rss.frontend.ui.rememberCoordinatedTwoPaneScrollableModifier
import net.matsudamper.mastodon.rss.frontend.ui.rememberSnackbarHostState

@Composable
internal fun AdminAccountScreen(
    username: String,
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) {
        AdminAccountScreenViewModel(
            username = username,
            viewModelScope = viewModelScope,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = rememberSnackbarHostState()

    LaunchedEffect(viewModel.eventHandler, navController, snackbarHostState) {
        viewModel.eventHandler.collect(
            object : AdminAccountScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }

                override fun showSnackbar(message: String) {
                    snackbarHostState.show(message)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminAccountContent(
        uiState = uiState,
        username = username,
        platform = platform,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun AdminAccountContent(
    uiState: AdminAccountScreenUiState,
    username: String,
    platform: ScreenPlatform,
    snackbarHostState: SnackbarHostState = rememberSnackbarHostState(),
) {
    var showPostDialog by remember(username) { mutableStateOf(false) }

    AdminScaffold(
        title = "@$username の管理",
        listener = uiState.listener,
        snackbarHostState = snackbarHostState,
    ) { wide ->
        val edgePadding = if (wide) 24.dp else 12.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth()
                .padding(horizontal = edgePadding),
        ) {
            when (val content = uiState.content) {
                is AdminAccountScreenUiState.Content.Loaded -> {
                    LoadedAdminAccountContent(
                        uiState = uiState,
                        content = content,
                        wide = wide,
                        verticalPadding = edgePadding,
                        onOpenPostDialog = { showPostDialog = true },
                        noteContent = ::NoteContent,
                    )
                    if (showPostDialog) {
                        PostDialog(
                            post = content.post,
                            listener = uiState.listener,
                            onDismissRequest = { showPostDialog = false },
                        )
                    }
                    content.profileDialog?.let { ProfileDialog(it) }
                    content.deleteNoteDialog?.let { DeleteNoteDialog(it, uiState.listener) }
                }

                else -> {
                    AdminAccountNonLoadedContent(
                        content = content,
                        verticalPadding = edgePadding,
                        listener = uiState.listener,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAccountNonLoadedContent(
    content: AdminAccountScreenUiState.Content,
    verticalPadding: Dp,
    listener: AdminAccountScreenUiState.Listener,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (content) {
            AdminAccountScreenUiState.Content.Loading -> {
                AdminSectionCard(title = "読み込み中") {
                    Text("アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }
            }

            AdminAccountScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onClickAdmin = listener::onClickAdmin)
            }

            AdminAccountScreenUiState.Content.NotFound -> {
                AdminSectionCard(title = "このアカウントは無い") {
                    Text("この名前では Mastodon からも見つからない。", style = MaterialTheme.typography.bodyMedium)
                    AdminTextLink(text = "アカウントの一覧に戻る", onClick = listener::onClickBackToAdmin)
                }
            }

            is AdminAccountScreenUiState.Content.Error -> {
                AdminSectionCard(title = "この画面を出せない") {
                    Text(content.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { listener.onClickReload() }) { Text("もう一度試す") }
                }
            }

            is AdminAccountScreenUiState.Content.Loaded -> Unit
        }
    }
}

@Composable
private fun LoadedAdminAccountContent(
    uiState: AdminAccountScreenUiState,
    content: AdminAccountScreenUiState.Content.Loaded,
    wide: Boolean,
    verticalPadding: Dp,
    onOpenPostDialog: () -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    if (!wide) {
        CompactLoadedAdminAccountContent(
            uiState = uiState,
            content = content,
            verticalPadding = verticalPadding,
            onOpenPostDialog = onOpenPostDialog,
            noteContent = noteContent,
        )
        return
    }

    WideLoadedAdminAccountContent(
        uiState = uiState,
        content = content,
        verticalPadding = verticalPadding,
        onOpenPostDialog = onOpenPostDialog,
        noteContent = noteContent,
    )
}

@Composable
private fun CompactLoadedAdminAccountContent(
    uiState: AdminAccountScreenUiState,
    content: AdminAccountScreenUiState.Content.Loaded,
    verticalPadding: Dp,
    onOpenPostDialog: () -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            AdminAccountHeaderRow(
                acct = uiState.acct,
                onOpenPostDialog = onOpenPostDialog,
            )
        }
        item(key = "account") {
            AccountCard(
                account = content.account,
                onClickOpenAccount = uiState.listener::onClickOpenAccount,
                onClickEditProfile = uiState.listener::onClickEditProfile,
            )
        }
        item(key = "feed") {
            FeedCard(content.feed, uiState.listener)
        }
        adminNotesItems(content, uiState.listener, noteContent)
    }
}

@Composable
private fun WideLoadedAdminAccountContent(
    uiState: AdminAccountScreenUiState,
    content: AdminAccountScreenUiState.Content.Loaded,
    verticalPadding: Dp,
    onOpenPostDialog: () -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    val notesListState = rememberLazyListState()
    val pageScrollState = remember { TwoPaneScrollState() }
    val coordinatedScrollModifier = rememberCoordinatedTwoPaneScrollableModifier(
        pageScrollState = pageScrollState,
        notesListState = notesListState,
    )

    LaunchedEffect(content.notes.size) {
        pageScrollState.resyncNotesOverflowAfterAppend(notesListState)
    }

    CoordinatedTwoPaneLayout(
        modifier = Modifier
            .fillMaxSize()
            .then(coordinatedScrollModifier),
        headerCollapsePx = pageScrollState.headerCollapsePx,
        onHeaderHeightChange = pageScrollState::updateHeaderHeight,
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = verticalPadding, bottom = 16.dp),
            ) {
                AdminAccountHeaderRow(
                    acct = uiState.acct,
                    onOpenPostDialog = onOpenPostDialog,
                )
            }
        },
        panes = {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .offset { IntOffset(x = 0, y = -pageScrollState.notesShiftPx()) },
                    state = notesListState,
                    contentPadding = PaddingValues(bottom = verticalPadding),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    adminNotesItems(content, uiState.listener, noteContent)
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .onSizeChanged { pageScrollState.updateSideHeight(it.height) }
                            .offset { IntOffset(x = 0, y = -pageScrollState.sideShiftPx()) }
                            .padding(bottom = verticalPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AccountCard(
                            account = content.account,
                            onClickOpenAccount = uiState.listener::onClickOpenAccount,
                            onClickEditProfile = uiState.listener::onClickEditProfile,
                        )
                        FeedCard(content.feed, uiState.listener)
                    }
                }
            }
        },
    )
}

@Composable
private fun AdminAccountHeaderRow(
    acct: String,
    onOpenPostDialog: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = acct,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Button(onClick = onOpenPostDialog) { Text("新しい投稿") }
    }
}

private fun LazyListScope.adminNotesItems(
    content: AdminAccountScreenUiState.Content.Loaded,
    listener: AdminAccountScreenUiState.Listener,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    item(key = "notes-title") {
        Text(
            text = "配信した投稿",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }

    val notes = content.notes
    val error = content.notesError

    when {
        content.notesLoading && notes.isEmpty() -> {
            item(key = "notes-loading") {
                NotesMessageCard {
                    Text("配信した投稿を取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        notes.isEmpty() && error != null -> {
            item(key = "notes-error") {
                NotesMessageCard {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = listener::onClickReloadNotes) { Text("もう一度試す") }
                    }
                }
            }
        }

        notes.isEmpty() -> {
            item(key = "notes-empty") {
                NotesMessageCard {
                    Text("まだ投稿していない。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        else -> {
            items(
                items = notes,
                key = AdminAccountScreenUiState.Note::url,
            ) { note ->
                NoteCard(note = note, noteContent = noteContent)
            }
        }
    }

    if (notes.isNotEmpty()) {
        item(key = "notes-footer") {
            AdminNotesPagingFooter(content = content, listener = listener)
        }
    }
}

@Composable
private fun AdminNotesPagingFooter(
    content: AdminAccountScreenUiState.Content.Loaded,
    listener: AdminAccountScreenUiState.Listener,
) {
    if (!content.canLoadMore && content.notesError == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val error = content.notesError
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = listener::onClickReloadNotes) {
                Text("もう一度試す")
            }
        }

        if (content.canLoadMore) {
            if (content.loadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = listener::onClickLoadMore) {
                    Text("もっと見る")
                }
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
private fun AccountCard(
    account: AdminAccountScreenUiState.Account,
    onClickOpenAccount: () -> Unit,
    onClickEditProfile: () -> Unit,
) {
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
        LabeledValue(label = "表示名", value = account.displayName ?: "未設定（@${account.username} が出る）")
        LabeledValue(label = "説明文", value = account.summary ?: "未設定（既定の文言が出る）")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onClickEditProfile) { Text("プロフィールを編集") }
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
                feed.postedItems?.takeIf { it.isNotEmpty() }?.let { FeedItemSummary("今回投稿した記事 ${it.size} 件", it) }
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
private fun ProfileDialog(dialog: AdminAccountScreenUiState.ProfileDialog) {
    val listener = dialog.listener

    AlertDialog(
        onDismissRequest = { if (!dialog.saving) listener.onDismiss() },
        title = { Text("プロフィールの編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mastodon のプロフィールに出る。空にすると未設定に戻る。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = dialog.displayName,
                    onValueChange = listener::onDisplayNameChanged,
                    enabled = !dialog.busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表示名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dialog.summary,
                    onValueChange = listener::onSummaryChanged,
                    enabled = !dialog.busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("説明文") },
                    minLines = 4,
                    maxLines = 10,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = listener::onClickApplyFeed, enabled = dialog.canApplyFeed && !dialog.busy) {
                        Text(if (dialog.applyingFeed) "取得中" else "フィードから追加")
                    }
                }
                Text(
                    if (dialog.canApplyFeed) {
                        "「フィードから追加」を押すと、登録済みフィードの題名と説明で入力を上書きする。"
                    } else {
                        "フィードを登録すると、その題名と説明で入力を上書きできる。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dialog.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = listener::onClickSave, enabled = !dialog.busy) {
                Text(if (dialog.saving) "保存中" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = listener::onDismiss, enabled = !dialog.saving) { Text("閉じる") }
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
                        Text("投稿した。宛先 ${result.deliveryAttemptCount} 件のうち ${result.delivered} 件に届いた。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
