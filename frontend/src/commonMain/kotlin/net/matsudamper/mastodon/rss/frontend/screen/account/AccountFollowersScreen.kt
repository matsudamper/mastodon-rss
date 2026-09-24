package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AccountAvatar

@Composable
internal fun AccountFollowersScreen(
    username: String,
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope, username) {
        AccountFollowersScreenViewModel(
            username = username,
            viewModelScope = viewModelScope,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController, platform) {
        viewModel.eventHandler.collect(
            object : AccountFollowersScreenViewModel.Event {
                override suspend fun close() {
                    navController.back()
                }

                override suspend fun openExternalLink(url: String) {
                    platform.openExternalLink(url)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AccountFollowersContent(uiState = uiState)
}

@Composable
internal fun AccountFollowersContent(
    uiState: AccountFollowersScreenUiState,
) {
    AlertDialog(
        onDismissRequest = uiState.listener::onClickClose,
        title = { Text("フォロワー") },
        text = {
            when (val content = uiState.content) {
                AccountFollowersScreenUiState.Content.Loading -> {
                    CircularProgressIndicator()
                }

                AccountFollowersScreenUiState.Content.Empty -> {
                    Text("まだフォロワーがいません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AccountFollowersScreenUiState.Content.NotFound -> {
                    Text("アカウントが見つかりません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                is AccountFollowersScreenUiState.Content.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(content.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = content.listener::onClickReload) {
                            Text("もう一度試す")
                        }
                    }
                }

                is AccountFollowersScreenUiState.Content.Loaded -> {
                    FollowerList(content = content)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = uiState.listener::onClickClose) {
                Text("閉じる")
            }
        },
    )
}

@Composable
private fun FollowerRow(
    follower: AccountFollowersScreenUiState.Follower,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = follower.listener::onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            username = follower.name,
            iconUrl = follower.iconUrl,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = follower.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (follower.acct != null) {
                Text(
                    text = follower.acct,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FollowerList(
    content: AccountFollowersScreenUiState.Content.Loaded,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = content.followers) { follower ->
            FollowerRow(follower = follower)
        }

        item(key = "footer") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val loadMore = content.loadMore) {
                    AccountFollowersScreenUiState.LoadMore.Hidden -> Unit

                    AccountFollowersScreenUiState.LoadMore.LoadOnVisible -> {
                        // LazyColumn の item なので、画面に入って初めて組まれる。
                        // 1 ページ足された後も枠が見えたままなら、件数が変わったのを合図にもう 1 ページ取る
                        LaunchedEffect(content.followers.size) {
                            content.listener.onLoadMore()
                        }
                        CircularProgressIndicator()
                    }

                    AccountFollowersScreenUiState.LoadMore.Loading -> {
                        CircularProgressIndicator()
                    }

                    is AccountFollowersScreenUiState.LoadMore.Error -> {
                        Text(loadMore.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = content.listener::onLoadMore) {
                            Text("もう一度試す")
                        }
                    }
                }
            }
        }
    }
}
