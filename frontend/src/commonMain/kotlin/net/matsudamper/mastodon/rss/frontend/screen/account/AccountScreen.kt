
package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.NotFoundContent
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AppBadge
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.LabeledValue
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.OutlinedBox
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.SnackbarHostState
import net.matsudamper.mastodon.rss.frontend.ui.StatusDot
import net.matsudamper.mastodon.rss.frontend.ui.TextLink
import net.matsudamper.mastodon.rss.frontend.ui.dividerColor
import net.matsudamper.mastodon.rss.frontend.ui.rememberSnackbarHostState

@Composable
internal fun AccountScreen(
    username: String,
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope, username, platform) {
        AccountScreenViewModel(
            username = username,
            host = platform.host,
            viewModelScope = viewModelScope,
            copyToClipboard = platform::copyToClipboard,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    val snackbarHostState = rememberSnackbarHostState()
    LaunchedEffect(viewModel.eventHandler, navController, snackbarHostState) {
        viewModel.eventHandler.collect(
            object : AccountScreenViewModel.Event {
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

    AccountContent(
        uiState = uiState,
        username = username,
        platform = platform,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun AccountContent(
    uiState: AccountScreenUiState,
    username: String,
    platform: ScreenPlatform,
    snackbarHostState: SnackbarHostState = rememberSnackbarHostState(),
) {
    PublicScaffold(
        listener = uiState.listener,
        snackbarHostState = snackbarHostState,
    ) { wide ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(if (wide) 24.dp else 12.dp),
        ) {
            when (val content = uiState.content) {
                AccountScreenUiState.Content.Loading -> {
                    SectionCard(title = "読み込み中") {
                        Text(
                            text = "アカウントを取ってきている。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                AccountScreenUiState.Content.NotFound -> {
                    NotFoundContent(
                        requestedPath = "/@$username",
                        description = "ユーザーが存在しません",
                    )
                }

                is AccountScreenUiState.Content.Error -> {
                    SectionCard(title = "アカウントを出せない") {
                        Text(
                            text = content.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        OutlinedButton(onClick = { uiState.listener.onClickReload() }) {
                            Text("もう一度試す")
                        }
                    }
                }

                is AccountScreenUiState.Content.Loaded -> {
                    LoadedAccountContent(
                        content = content,
                        wide = wide,
                        onOpenExternal = platform::openExternalLink,
                        noteContent = ::NoteContent,
                        listener = uiState.listener,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedAccountContent(
    content: AccountScreenUiState.Content.Loaded,
    wide: Boolean,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
    listener: AccountScreenUiState.Listener,
) {
    val state = content.account

    if (!wide) {
        CompactLoadedAccountContent(
            content = content,
            listener = listener,
            onOpenExternal = onOpenExternal,
            noteContent = noteContent,
        )
        return
    }

    WideLoadedAccountContent(
        content = content,
        listener = listener,
        onOpenExternal = onOpenExternal,
        noteContent = noteContent,
    )
}

@Composable
private fun CompactLoadedAccountContent(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    val state = content.account

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.placeholder) {
            item(key = "placeholder") {
                PlaceholderNotice()
            }
        }
        item(key = "profile") {
            ProfileHeader(
                state = state,
                wide = false,
                listener = listener,
                onOpenExternal = onOpenExternal,
            )
        }
        item(key = "feed") {
            FeedSection(state, onOpenExternal)
        }
        item(key = "follow") {
            FollowSection(
                state = state,
                onOpenExternal = onOpenExternal,
                listener = listener,
            )
        }
        notesItems(content, listener, onOpenExternal, noteContent)
        item(key = "delivery") {
            DeliverySection(state)
        }
    }
}

@Composable
private fun WideLoadedAccountContent(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    val state = content.account
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val leftListState = rememberLazyListState()
    val rightScrollState = rememberScrollState()
    val headerInputScrollState = rememberScrollableState { 0f }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    val previous = headerOffsetPx
                    headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx.toFloat(), 0f)
                    return Offset(x = 0f, y = headerOffsetPx - previous)
                }

                if (available.y > 0f && panesAtTop(leftListState, rightScrollState)) {
                    val previous = headerOffsetPx
                    headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx.toFloat(), 0f)
                    return Offset(x = 0f, y = headerOffsetPx - previous)
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f || !panesAtTop(leftListState, rightScrollState)) return Offset.Zero

                val previous = headerOffsetPx
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx.toFloat(), 0f)
                return Offset(x = 0f, y = headerOffsetPx - previous)
            }
        }
    }

    CoordinatedTwoPaneLayout(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        headerOffsetPx = headerOffsetPx,
        onHeaderHeightChange = { height ->
            headerHeightPx = height
            headerOffsetPx = headerOffsetPx.coerceIn(-height.toFloat(), 0f)
        },
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(nestedScrollConnection)
                    .scrollable(
                        state = headerInputScrollState,
                        orientation = Orientation.Vertical,
                    )
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.placeholder) {
                    PlaceholderNotice()
                }
                ProfileHeader(
                    state = state,
                    wide = true,
                    listener = listener,
                    onOpenExternal = onOpenExternal,
                )
            }
        },
        panes = {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .nestedScroll(nestedScrollConnection),
                    state = leftListState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    notesItems(content, listener, onOpenExternal, noteContent)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(rightScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FeedSection(state, onOpenExternal)
                    DeliverySection(state)
                    FollowSection(
                        state = state,
                        onOpenExternal = onOpenExternal,
                        listener = listener,
                    )
                }
            }
        },
    )
}

private fun panesAtTop(
    leftListState: LazyListState,
    rightScrollState: ScrollState,
): Boolean =
    leftListState.firstVisibleItemIndex == 0 &&
        leftListState.firstVisibleItemScrollOffset == 0 &&
        rightScrollState.value == 0

@Composable
private fun CoordinatedTwoPaneLayout(
    headerOffsetPx: Float,
    onHeaderHeightChange: (Int) -> Unit,
    header: @Composable () -> Unit,
    panes: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .layoutId("header")
                    .onSizeChanged { onHeaderHeightChange(it.height) },
            ) {
                header()
            }
            Box(modifier = Modifier.layoutId("panes")) {
                panes()
            }
        },
    ) { measurables, constraints ->
        val headerPlaceable = measurables.first { it.layoutId == "header" }.measure(
            constraints.copy(
                minWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )
        val visibleHeaderHeight = (headerPlaceable.height + headerOffsetPx)
            .roundToInt()
            .coerceIn(0, headerPlaceable.height)
        val panesHeight = (constraints.maxHeight - visibleHeaderHeight).coerceAtLeast(0)
        val panesPlaceable = measurables.first { it.layoutId == "panes" }.measure(
            Constraints.fixed(
                width = constraints.maxWidth,
                height = panesHeight,
            ),
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.place(x = 0, y = headerOffsetPx.roundToInt())
            panesPlaceable.place(x = 0, y = visibleHeaderHeight)
        }
    }
}

/**
 * 仮の値であることの断り。
 *
 * 画面を先に作っているので、繋ぐ先がまだ無い。断りが無いと、フォロワー数や
 * 最終取得を実際の値だと思って運用の判断に使われる
 */
@Composable
private fun PlaceholderNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "この画面の数値とフィード情報は仮のもの",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "実際の値になるのは、フィードの取り込み（Phase 5）と管理 API（Phase 8）を繋いでから。" +
                    "ユーザー名と acct と配信した投稿は本物。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * プロフィール。ヘッダー画像・アイコン・表示名・acct・説明・数値。
 *
 * 画像はまだ持っていない（Phase 6 の項目）ので、ユーザー名から決まる色で描く。
 * 空の枠を置くより、アカウントごとに見分けが付く方が検証で役に立つ。
 */
@Composable
private fun ProfileHeader(
    state: AccountUiState,
    wide: Boolean,
    listener: AccountScreenUiState.Listener,
    onOpenExternal: (String) -> Unit,
) {
    val avatarSize = if (wide) 88.dp else 68.dp
    val colors = avatarColors(state.username)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (wide) 132.dp else 88.dp)
                    .background(Brush.linearGradient(colors)),
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .height(IntrinsicSize.Max),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Layout(
                    {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(RoundedCornerShape(avatarSize / 4))
                                .background(Brush.linearGradient(colors.reversed())),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.initial,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val placeable = measurables.first().measure(
                        Constraints.fixed(
                            avatarSize.roundToPx(),
                            avatarSize.roundToPx(),
                        ),
                    )
                    layout(placeable.width, 0) {
                        println("constraints.maxHeight=${constraints.maxHeight}, placeable.measuredHeight=${placeable.measuredHeight}")
                        placeable.place(
                            x = 0,
                            y = constraints.maxHeight - placeable.measuredHeight,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = state.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.acct,
                            modifier = Modifier,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = { listener.onClickCopyAcct() },
                    ) {
                        Icon(
                            modifier = Modifier.padding(8.dp),
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "コピー",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppBadge(
                        text = "bot",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppBadge(
                        text = "フィード",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(value = state.followers, label = "フォロワー")
                    Stat(value = state.deliveredCount, label = "配信した記事")
                    Stat(value = state.lastDeliveredAt, label = "最終配信")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onOpenExternal(state.feed.feedUrl) }) {
                        Text("フィードを開く")
                    }
                    OutlinedButton(onClick = { onOpenExternal(state.actorUrl) }) {
                        Text("Actor JSON")
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 配信元のフィード。このアカウントが何を流すものなのかを示す部分。
 */
@Composable
private fun FeedSection(state: AccountUiState, onOpenExternal: (String) -> Unit) {
    val feed = state.feed

    SectionCard(title = "配信元のフィード") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(color = statusColor(feed.status))
            Text(
                text = feed.status.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LabeledValue(
            label = "フィード",
            value = feed.feedUrl,
            onClick = { onOpenExternal(feed.feedUrl) },
        )
        if (feed.siteUrl != null) {
            LabeledValue(
                label = "サイト",
                value = feed.siteUrl,
                onClick = { onOpenExternal(feed.siteUrl) },
            )
        }
        LabeledValue(label = "形式", value = feed.format)
        LabeledValue(label = "取得間隔", value = feed.interval)
        LabeledValue(label = "最終取得", value = feed.lastFetchedAt)
        LabeledValue(label = "次回取得", value = feed.nextFetchAt)
    }
}

/**
 * 配信の状況。届いていないときに、どこで止まっているかを見る部分。
 */
@Composable
private fun DeliverySection(state: AccountUiState) {
    SectionCard(title = "配信の状況") {
        LabeledValue(label = "フォロワー", value = state.followers)
        LabeledValue(label = "未配信", value = state.delivery.queued)
        LabeledValue(label = "失敗", value = state.delivery.failed)

        val lastError = state.delivery.lastError
        if (lastError == null) {
            Text(
                text = "直近の配信エラーは無い",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * フォローの仕方。
 *
 * ここにフォローボタンは置けない。フォローは相手のインスタンス側で始まる操作で、
 * このサーバーには押した人のアカウントが無いため。acct を貼ってもらうのが確実。
 */
@Composable
private fun FollowSection(
    state: AccountUiState,
    onOpenExternal: (String) -> Unit,
    listener: AccountScreenUiState.Listener,
) {
    SectionCard(title = "フォローする") {
        Text(
            text = "使っている Mastodon の検索窓にこの文字列を貼ると、このアカウントが出る。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.acct,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                IconButton(onClick = listener::onClickCopyAcct) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "コピー",
                    )
                }
            }
        }

        LabeledValue(
            label = "Actor",
            value = state.actorUrl,
            onClick = { onOpenExternal(state.actorUrl) },
        )

        HorizontalDivider(color = dividerColor())

        Text(
            text = "このアカウントについての問い合わせ先",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextLink(
            text = state.operatorAcct,
            onClick = { listener.onClickOperator(state.operatorUsername) },
        )
    }
}

/**
 * 配信した投稿。1 件ずつカードに分け、続きはページングで取る。
 */
private fun LazyListScope.notesItems(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    item(key = "notes-title") {
        Text(
            text = "配信した投稿",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }

    val notes = content.notes
    val error = content.notesError

    when {
        content.notesLoading && notes.isEmpty() -> {
            item(key = "notes-loading") {
                NoteListPlaceholder {
                    Text(
                        text = "配信した投稿を取ってきている。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        notes.isEmpty() && error != null -> {
            item(key = "notes-error") {
                NoteListPlaceholder {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { listener.onClickReloadNotes() }) {
                        Text("もう一度試す")
                    }
                }
            }
        }

        notes.isEmpty() -> {
            item(key = "notes-empty") {
                NoteListPlaceholder {
                    Text(
                        text = "まだ投稿していない",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> {
            items(
                items = notes,
                key = NoteUiState::url,
            ) { note ->
                NoteCard(note, onOpenExternal, noteContent)
            }
        }
    }

    if (notes.isNotEmpty()) {
        item(key = "notes-footer") {
            NotesPagingFooter(content = content, listener = listener)
        }
    }
}

@Composable
private fun NoteListPlaceholder(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteUiState,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            noteContent(note.contentHtml, Modifier.fillMaxWidth())

            Text(
                text = note.publishedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextLink(
                text = note.url,
                onClick = { onOpenExternal(note.url) },
            )
        }
    }
}

@Composable
private fun NotesPagingFooter(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
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
            OutlinedButton(onClick = { listener.onClickReloadNotes() }) {
                Text("もう一度試す")
            }
        }

        if (content.canLoadMore) {
            if (content.loadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = { listener.onClickLoadMore() }) {
                    Text("もっと見る")
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: FetchStatus): Color =
    when (status) {
        FetchStatus.Ok -> MaterialTheme.colorScheme.secondary
        FetchStatus.Failed -> MaterialTheme.colorScheme.error
        FetchStatus.Unknown -> MaterialTheme.colorScheme.outline
    }

/**
 * ユーザー名から決まる 2 色。アイコンとヘッダーの代わりに使う。
 *
 * 同じ名前なら必ず同じ色になるようにする。開くたびに色が変わると、
 * 名前を変えながら検証しているときに見分けが付かない。
 */
private fun avatarColors(username: String): List<Color> {
    val palette = listOf(
        Color(0xFF4A3FD1) to Color(0xFF7B6FF0),
        Color(0xFF1E7A6F) to Color(0xFF3FB8A6),
        Color(0xFFB05A1E) to Color(0xFFE79A4B),
        Color(0xFF8C2F6B) to Color(0xFFD167AC),
        Color(0xFF2F5FA8) to Color(0xFF6795DE),
    )

    // hashCode は負にもなるので、剰余を取る前に絶対値にする
    val index = (username.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size
    val (start, end) = palette[index]
    return listOf(start, end)
}
