package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator

@Composable
internal fun AdminAccountProfileEditScreen(username: String, navController: Navigator) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) { AdminAccountProfileEditScreenViewModel(username, viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(object : AdminAccountProfileEditScreenViewModel.Event {
            override suspend fun close() = navController.back()
        })
    }
    AlertDialog(
        onDismissRequest = { if (uiState.closeEnabled) uiState.listener.onClickClose() },
        title = { Text("プロフィールを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mastodon のプロフィールに出る。空にすると未設定に戻る。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = uiState.displayName, onValueChange = uiState.listener::onDisplayNameChanged, enabled = uiState.inputEnabled, modifier = Modifier.fillMaxWidth(), label = {
                    Text("表示名")
                }, singleLine = true)
                OutlinedTextField(value = uiState.summary, onValueChange = uiState.listener::onSummaryChanged, enabled = uiState.inputEnabled, modifier = Modifier.fillMaxWidth(), label = {
                    Text("説明文")
                }, minLines = 4, maxLines = 10)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = uiState.listener::onClickApplyFeed, enabled = uiState.applyFeedButtonEnabled) {
                        Text(if (uiState.applyingFeed) "取得中" else "フィードから反映")
                    }
                }
                uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(uiState.listener::onClickSave, enabled = uiState.saveButtonEnabled) { Text(if (uiState.saving) "保存中" else "保存") } },
        dismissButton = { TextButton(uiState.listener::onClickClose, enabled = uiState.closeEnabled) { Text("閉じる") } },
    )
}
