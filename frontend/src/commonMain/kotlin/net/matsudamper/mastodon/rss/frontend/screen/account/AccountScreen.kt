
package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.SnackbarHostState
import net.matsudamper.mastodon.rss.frontend.ui.TextLink
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
        val edgePadding = if (wide) 24.dp else 12.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = edgePadding),
        ) {
            when (val content = uiState.content) {
                AccountScreenUiState.Content.Loading -> {
                    SectionCard(
                        modifier = Modifier.padding(vertical = edgePadding),
                        title = "読み込み中",
                    ) {
                        Text(
                            text = "アカウントを取ってきている。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                AccountScreenUiState.Content.NotFound -> {
                    NotFoundContent(
                        requestedPath = "/@$username",
                        modifier = Modifier.padding(vertical = edgePadding),
                        description = "ユーザーが存在しません",
                    )
                }

                is AccountScreenUiState.Content.Error -> {
                    SectionCard(
                        modifier = Modifier.padding(vertical = edgePadding),
                        title = "アカウントを出せない",
                    ) {
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
                        verticalPadding = edgePadding,
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
    verticalPadding: Dp,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
    listener: AccountScreenUiState.Listener,
) {
    if (!wide) {
        CompactLoadedAccountContent(
            content = content,
            listener = listener,
            verticalPadding = verticalPadding,
            onOpenExternal = onOpenExternal,
            noteContent = noteContent,
        )
        return
    }

    WideLoadedAccountContent(
        content = content,
        listener = listener,
        verticalPadding = verticalPadding,
        onOpenExternal = onOpenExternal,
        noteContent = noteContent,
    )
}

@Composable
private fun CompactLoadedAccountContent(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
    verticalPadding: Dp,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    val state = content.account

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "profile") {
            ProfileHeader(
                state = state,
                wide = false,
                listener = listener,
                onOpenExternal = onOpenExternal,
            )
        }
        state.feed?.let { feed ->
            item(key = "feed") {
                FeedSection(feed, onOpenExternal)
            }
        }
        notesItems(content, listener, onOpenExternal, noteContent)
    }
}

@Composable
private fun WideLoadedAccountContent(
    content: AccountScreenUiState.Content.Loaded,
    listener: AccountScreenUiState.Listener,
    verticalPadding: Dp,
    onOpenExternal: (String) -> Unit,
    noteContent: @Composable (String, Modifier) -> Unit,
) {
    val state = content.account
    val notesListState = rememberLazyListState()
    val pageScrollState = remember { TwoPaneScrollState() }
    val scrollableState = rememberScrollableState { delta ->
        pageScrollState.scrollBy(delta = delta, notesListState = notesListState)
    }

    CoordinatedTwoPaneLayout(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Vertical,
                reverseDirection = ScrollableDefaults.reverseDirection(
                    layoutDirection = LocalLayoutDirection.current,
                    orientation = Orientation.Vertical,
                    reverseScrolling = false,
                ),
            )
            .onSizeChanged { pageScrollState.updateViewportHeight(it.height) },
        headerCollapsePx = pageScrollState.headerCollapsePx,
        onHeaderHeightChange = pageScrollState::updateHeaderHeight,
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = verticalPadding, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                        .offset { IntOffset(x = 0, y = -pageScrollState.notesShiftPx()) },
                    state = notesListState,
                    contentPadding = PaddingValues(bottom = verticalPadding),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    notesItems(content, listener, onOpenExternal, noteContent)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    state.feed?.let { feed ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .onSizeChanged { pageScrollState.updateSideHeight(it.height) }
                                .offset { IntOffset(x = 0, y = -pageScrollState.sideShiftPx()) }
                                .padding(bottom = verticalPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            FeedSection(feed, onOpenExternal)
                        }
                    }
                }
            }
        },
    )
}

/**
 * 2 ペインを 1 枚のページとして動かすスクロール位置。
 *
 * ページの高さは長い方のカラムで決まる。投稿は 1 万件を想定していて全部の高さは測れないので、
 * 投稿側は LazyColumn に送った量として持ち、投稿が尽きた先はカラムごとずらして送る
 */
@Stable
private class TwoPaneScrollState {
    var headerCollapsePx: Float by mutableFloatStateOf(0f)
        private set

    private var contentOffsetPx: Float by mutableFloatStateOf(0f)
    private var notesOverflowPx: Float by mutableFloatStateOf(0f)
    private var headerHeightPx: Int by mutableIntStateOf(0)
    private var sideHeightPx: Int by mutableIntStateOf(0)
    private var viewportHeightPx: Int by mutableIntStateOf(0)

    fun notesShiftPx(): Int = notesOverflowPx.roundToInt()

    fun sideShiftPx(): Int = contentOffsetPx.roundToInt().coerceAtMost(sideHeightPx)

    fun updateHeaderHeight(height: Int) {
        headerHeightPx = height
        headerCollapsePx = headerCollapsePx.coerceIn(0f, height.toFloat())
    }

    fun updateSideHeight(height: Int) {
        sideHeightPx = height
    }

    fun updateViewportHeight(height: Int) {
        viewportHeightPx = height
    }

    fun scrollBy(delta: Float, notesListState: LazyListState): Float {
        return if (delta > 0f) {
            scrollForward(delta, notesListState)
        } else {
            scrollBackward(delta, notesListState)
        }
    }

    /**
     * ヘッダーを畳んでから投稿、投稿が尽きたらカラムごとずらす。
     *
     * ヘッダーを畳むと投稿側の表示領域が広がるが、それが反映されるのは次の計測なので、
     * 送る量は畳む前に測った「画面の下に隠れている高さ」で頭打ちにする
     */
    private fun scrollForward(delta: Float, notesListState: LazyListState): Float {
        val notesBelow = notesBelowViewportPx(notesListState)
        var rest = delta.coerceAtMost(maxOf(notesBelow, sideBelowViewportPx()).coerceAtLeast(0f))
        var consumed = 0f

        val collapsed = collapseHeader(rest)
        consumed += collapsed
        rest -= collapsed

        val scrolled = scrollNotes(notesListState, rest.coerceAtMost((notesBelow - collapsed).coerceAtLeast(0f)))
        consumed += scrolled
        rest -= scrolled

        return consumed + shiftPastNotesEnd(rest)
    }

    private fun scrollBackward(delta: Float, notesListState: LazyListState): Float {
        var rest = delta
        var consumed = 0f

        val unshifted = shiftPastNotesEnd(rest)
        consumed += unshifted
        rest -= unshifted

        val scrolled = scrollNotes(notesListState, rest)
        consumed += scrolled
        rest -= scrolled

        return consumed + collapseHeader(rest)
    }

    private fun collapseHeader(delta: Float): Float {
        val next = (headerCollapsePx + delta).coerceIn(0f, headerHeightPx.toFloat())
        val consumed = next - headerCollapsePx
        headerCollapsePx = next
        return consumed
    }

    private fun scrollNotes(notesListState: LazyListState, delta: Float): Float {
        if (delta == 0f) return 0f
        val consumed = notesListState.dispatchRawDelta(delta)
        contentOffsetPx += consumed
        return consumed
    }

    private fun shiftPastNotesEnd(delta: Float): Float {
        val consumed = if (delta > 0f) {
            delta.coerceAtMost(sideBelowViewportPx().coerceAtLeast(0f))
        } else {
            delta.coerceAtLeast(-notesOverflowPx)
        }
        notesOverflowPx += consumed
        contentOffsetPx += consumed
        return consumed
    }

    private fun sideBelowViewportPx(): Float =
        headerHeightPx - headerCollapsePx - contentOffsetPx + sideHeightPx - viewportHeightPx

    private fun notesBelowViewportPx(notesListState: LazyListState): Float {
        val layoutInfo = notesListState.layoutInfo
        val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return 0f
        if (lastItem.index < layoutInfo.totalItemsCount - 1) return Float.POSITIVE_INFINITY
        return (lastItem.offset + lastItem.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset)
            .toFloat()
    }
}

@Composable
private fun CoordinatedTwoPaneLayout(
    headerCollapsePx: Float,
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
        val visibleHeaderHeight = (headerPlaceable.height - headerCollapsePx)
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
            headerPlaceable.place(x = 0, y = visibleHeaderHeight - headerPlaceable.height)
            panesPlaceable.place(x = 0, y = visibleHeaderHeight)
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
                            text = state.username,
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
                    if (state.feed != null) {
                        AppBadge(
                            text = "フィード",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                if (state.feed != null) {
                    Text(
                        text = "RSS/Atom フィードを ActivityPub で配信するアカウント",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(value = state.followerCount, label = "フォロワー")
                    Stat(value = state.noteCount, label = "配信した投稿")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val feedUrl = state.feed?.feedUrl
                    if (feedUrl != null) {
                        Button(onClick = { onOpenExternal(feedUrl) }) {
                            Text("フィードを開く")
                        }
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
private fun FeedSection(feed: FeedUiState, onOpenExternal: (String) -> Unit) {
    SectionCard(title = "配信元のフィード") {
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
