package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AccountAvatar
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffold
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun HomeScreen(
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        HomeScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : HomeScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    HomeContent(
        uiState = uiState,
        onOpenExternal = platform::openExternalLink,
    )
}

@Composable
internal fun HomeContent(
    uiState: HomeScreenUiState,
    onOpenExternal: (String) -> Unit,
) {
    PublicScaffold(listener = uiState.listener) { wide ->
        val edgePadding = if (wide) 24.dp else 12.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = edgePadding),
        ) {
            if (wide) {
                WideHomeContent(
                    uiState = uiState,
                    verticalPadding = edgePadding,
                    onOpenExternal = onOpenExternal,
                )
            } else {
                CompactHomeContent(
                    uiState = uiState,
                    verticalPadding = edgePadding,
                    onOpenExternal = onOpenExternal,
                )
            }
        }
    }
}

/**
 * 左にタイムライン、右にアカウントを縦一列。
 *
 * アカウントの列は数を絞っているので、タイムラインと一緒には流さず右に留める
 */
@Composable
private fun WideHomeContent(
    uiState: HomeScreenUiState,
    verticalPadding: Dp,
    onOpenExternal: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            timelineItems(
                timeline = uiState.timeline,
                listener = uiState.listener,
                onOpenExternal = onOpenExternal,
            )
        }
        Column(
            modifier = Modifier
                .width(WideAccountsColumnWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = verticalPadding),
        ) {
            AccountsSection(
                accounts = uiState.accounts,
                listener = uiState.listener,
            )
        }
    }
}

@Composable
private fun CompactHomeContent(
    uiState: HomeScreenUiState,
    verticalPadding: Dp,
    onOpenExternal: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "accounts") {
            AccountsSection(
                accounts = uiState.accounts,
                listener = uiState.listener,
            )
        }
        timelineItems(
            timeline = uiState.timeline,
            listener = uiState.listener,
            onOpenExternal = onOpenExternal,
        )
    }
}

private fun LazyListScope.timelineItems(
    timeline: HomeScreenUiState.Timeline,
    listener: HomeScreenUiState.Listener,
    onOpenExternal: (String) -> Unit,
) {
    item(key = "timeline-title") {
        Text(
            text = "タイムライン",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }

    when (timeline) {
        HomeScreenUiState.Timeline.Loading -> {
            item(key = "timeline-loading") {
                CardPlaceholder {
                    Text(
                        text = "投稿を取ってきている。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        is HomeScreenUiState.Timeline.Error -> {
            item(key = "timeline-error") {
                CardPlaceholder {
                    Text(
                        text = timeline.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = listener::onClickReloadTimeline) {
                        Text("もう一度試す")
                    }
                }
            }
        }

        is HomeScreenUiState.Timeline.Loaded -> {
            if (timeline.notes.isEmpty()) {
                item(key = "timeline-empty") {
                    CardPlaceholder {
                        Text(
                            text = "まだ投稿がない",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = timeline.notes,
                    key = HomeScreenUiState.Note::url,
                ) { note ->
                    TimelineNoteCard(note = note, onOpenExternal = onOpenExternal)
                }
                item(key = "timeline-footer") {
                    TimelinePagingFooter(timeline = timeline, listener = listener)
                }
            }
        }
    }
}

@Composable
private fun TimelineNoteCard(
    note: HomeScreenUiState.Note,
    onOpenExternal: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = note.listener::onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountAvatar(
                    username = note.account.username,
                    iconUrl = note.account.iconUrl,
                    modifier = Modifier.clickable(onClick = note.account.listener::onClick),
                    size = 40.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = note.account.listener::onClick),
                ) {
                    Text(
                        text = note.account.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = note.account.acct,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = note.publishedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NoteContent(contentHtml = note.contentHtml, modifier = Modifier.fillMaxWidth())

            TextLink(
                text = note.url,
                onClick = { onOpenExternal(note.url) },
            )
        }
    }
}

@Composable
private fun TimelinePagingFooter(
    timeline: HomeScreenUiState.Timeline.Loaded,
    listener: HomeScreenUiState.Listener,
) {
    if (timeline.loadMoreVisible) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val errorMessage = timeline.loadMoreErrorMessage
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (timeline.loadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = listener::onClickLoadMore) {
                    Text(if (errorMessage != null) "もう一度試す" else "もっと見る")
                }
            }
        }
    }
}

/**
 * アカウントを縦一列に並べる。全部は出さず、続きは一覧の画面に任せる
 */
@Composable
private fun AccountsSection(
    accounts: HomeScreenUiState.Accounts,
    listener: HomeScreenUiState.Listener,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Text(
                text = "アカウント",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (accounts) {
                HomeScreenUiState.Accounts.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                is HomeScreenUiState.Accounts.Error -> {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = accounts.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = listener::onClickReloadAccounts) {
                            Text("もう一度試す")
                        }
                    }
                }

                is HomeScreenUiState.Accounts.Loaded -> {
                    if (accounts.accounts.isEmpty()) {
                        Text(
                            text = "公開されているアカウントはありません。",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        accounts.accounts.forEach { account ->
                            AccountRow(account = account)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = listener::onClickAllAccounts) {
                    Text("すべて見る")
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: HomeScreenUiState.Account,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = account.listener::onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountAvatar(
            username = account.username,
            iconUrl = account.iconUrl,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.acct,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardPlaceholder(content: @Composable () -> Unit) {
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

/**
 * 右に置くアカウントの列の幅。タイムラインの本文を読める幅を残す
 */
private val WideAccountsColumnWidth: Dp = 300.dp
