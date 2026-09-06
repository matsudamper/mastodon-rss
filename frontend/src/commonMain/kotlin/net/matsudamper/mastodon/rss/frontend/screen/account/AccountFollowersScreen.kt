package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

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
                        TextButton(onClick = uiState.listener::onClickReload) {
                            Text("もう一度試す")
                        }
                    }
                }

                is AccountFollowersScreenUiState.Content.Loaded -> {
                    FollowerList(content = content, listener = uiState.listener)
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

/**
 * 人数の分だけ縦に伸びるので、画面に収まらない。続きを足すたびに増えるので、
 * 見えている分だけ配置する
 */
@Composable
private fun FollowerList(
    content: AccountFollowersScreenUiState.Content.Loaded,
    listener: AccountFollowersScreenUiState.Listener,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = content.followers,
            key = FollowerUiState::actorUrl,
        ) { follower ->
            TextLink(
                text = follower.actorUrl,
                modifier = Modifier.fillMaxWidth(),
                onClick = follower.listener::onClick,
            )
        }

        item(key = "footer") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (content.loadMoreErrorMessage != null) {
                    Text(content.loadMoreErrorMessage, color = MaterialTheme.colorScheme.error)
                }

                when (content.loadMore) {
                    AccountFollowersScreenUiState.LoadMore.Hidden -> Unit

                    AccountFollowersScreenUiState.LoadMore.Loading -> {
                        CircularProgressIndicator()
                    }

                    AccountFollowersScreenUiState.LoadMore.Button -> {
                        TextButton(onClick = listener::onClickLoadMore) {
                            Text("もっと見る")
                        }
                    }
                }
            }
        }
    }
}
