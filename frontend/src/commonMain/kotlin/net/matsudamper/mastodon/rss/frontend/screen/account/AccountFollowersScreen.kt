package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            // 人数の分だけ縦に伸びるので、画面に収まらないことがある
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val content = uiState.content) {
                    AccountFollowersScreenUiState.Content.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    AccountFollowersScreenUiState.Content.Empty -> {
                        Text("まだフォロワーがいません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is AccountFollowersScreenUiState.Content.Error -> {
                        Text(content.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = uiState.listener::onClickReload) {
                            Text("もう一度試す")
                        }
                    }

                    is AccountFollowersScreenUiState.Content.Loaded -> {
                        for (follower in content.followers) {
                            TextLink(
                                text = follower.actorUrl,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = follower.listener::onClick,
                            )
                        }

                        if (content.loadMoreButtonVisible) {
                            if (content.loadMoreButtonLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            } else {
                                TextButton(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    onClick = uiState.listener::onClickLoadMore,
                                ) {
                                    Text("もっと見る")
                                }
                            }
                        }
                    }
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
