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
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun AccountNoteScreen(
    username: String,
    noteId: String,
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope, username, noteId) {
        AccountNoteScreenViewModel(
            username = username,
            noteId = noteId,
            viewModelScope = viewModelScope,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController, platform) {
        viewModel.eventHandler.collect(
            object : AccountNoteScreenViewModel.Event {
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

    AccountNoteContent(uiState = uiState)
}

@Composable
internal fun AccountNoteContent(
    uiState: AccountNoteScreenUiState,
) {
    AlertDialog(
        onDismissRequest = uiState.listener::onClickClose,
        title = { Text("投稿") },
        text = {
            // 本文の長さで縦に伸びるので、画面に収まらないことがある
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val content = uiState.content) {
                    AccountNoteScreenUiState.Content.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    AccountNoteScreenUiState.Content.NotFound -> {
                        Text("投稿が見つかりません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is AccountNoteScreenUiState.Content.Error -> {
                        Text(content.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = uiState.listener::onClickReload) {
                            Text("もう一度試す")
                        }
                    }

                    is AccountNoteScreenUiState.Content.Loaded -> {
                        NoteContent(content.contentHtml, Modifier.fillMaxWidth())
                        Text(
                            text = content.publishedAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextLink(
                            text = "ActivityPub の投稿を開く",
                            onClick = uiState.listener::onClickActivityPub,
                        )
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
