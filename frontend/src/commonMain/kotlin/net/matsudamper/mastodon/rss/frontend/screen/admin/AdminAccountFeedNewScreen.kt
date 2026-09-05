package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator

@Composable
internal fun AdminAccountFeedNewScreen(
    username: String,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) {
        AdminAccountFeedNewScreenViewModel(
            username = username,
            viewModelScope = viewModelScope,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminAccountFeedNewScreenViewModel.Event {
                override suspend fun close() {
                    navController.back()
                }
            },
        )
    }

    AdminAccountFeedNewContent(uiState = uiState)
}

@Composable
internal fun AdminAccountFeedNewContent(
    uiState: AdminAccountFeedNewScreenUiState,
) {
    AlertDialog(
        onDismissRequest = { if (uiState.canClose) uiState.listener.onClickClose() },
        title = { Text("RSS フィードを追加") },
        text = {
            // 中身はプレビューの分だけ縦に伸びる。枠に収まらないときは送る
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${uiState.acct} が流す記事の配信元を決める。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = uiState.listener::onUrlChanged,
                    enabled = !uiState.fetching && !uiState.saving,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("フィード URL") },
                    singleLine = true,
                    isError = uiState.errorMessage != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { uiState.listener.onClickFetch() }),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = uiState.listener::onClickFetch, enabled = uiState.canFetch) {
                        Text(if (uiState.fetching) "取得中" else "取得")
                    }
                }

                uiState.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                FeedPreview(uiState = uiState)
            }
        },
        confirmButton = {
            Button(onClick = uiState.listener::onClickSave, enabled = uiState.canSave) {
                Text(if (uiState.saving) "登録中" else "登録する")
            }
        },
        dismissButton = {
            TextButton(onClick = uiState.listener::onClickClose, enabled = uiState.canClose) {
                Text("やめる")
            }
        },
    )
}

@Composable
private fun FeedPreview(uiState: AdminAccountFeedNewScreenUiState, modifier: Modifier = Modifier) {
    val preview = uiState.preview

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            uiState.fetching -> {
                Text("フィードを取ってきている。", style = MaterialTheme.typography.bodyMedium)
            }

            preview != null -> {
                preview.title?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(preview.format, style = MaterialTheme.typography.bodySmall)
                preview.siteUrl?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

            else -> {
                Text(
                    text = "取得を押すと、登録する前にフィードの中身を確かめられる。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
