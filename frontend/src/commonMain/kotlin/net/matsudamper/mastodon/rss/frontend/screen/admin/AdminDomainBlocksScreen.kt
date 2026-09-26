package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.LoadMoreOnScrollEnd
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
internal fun AdminDomainBlocksScreen(
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminDomainBlocksScreenViewModel(viewModelScope, AdminApi())
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminDomainBlocksScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminDomainBlocksContent(uiState = uiState)
}

@Composable
internal fun AdminDomainBlocksContent(
    uiState: AdminDomainBlocksScreenUiState,
) {
    AdminScaffold("配信・受信を止めるドメイン", listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "配信・受信を止めるドメイン",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = uiState.listener::onClickReload) { Text("更新") }
                Button(onClick = uiState.listener::onClickAdd) { Text("追加") }
            }
            when (val content = uiState.content) {
                AdminDomainBlocksScreenUiState.Content.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                AdminDomainBlocksScreenUiState.Content.RequireLogin -> RequireLoginCard(
                    onClickAdmin = uiState.listener::onClickAdmin,
                )

                is AdminDomainBlocksScreenUiState.Content.Error -> SectionCard("一覧を出せない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = uiState.listener::onClickReload) { Text("もう一度試す") }
                }

                is AdminDomainBlocksScreenUiState.Content.Loaded -> Blocks(
                    content = content,
                    listener = uiState.listener,
                )
            }
        }
    }

    uiState.editor?.let { Editor(it) }
}

@Composable
private fun Blocks(
    content: AdminDomainBlocksScreenUiState.Content.Loaded,
    listener: AdminDomainBlocksScreenUiState.Listener,
) {
    content.emptyText?.let {
        SectionCard("止めているドメイン") { Text(it) }
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("止めているドメイン") {
            content.blocks.forEachIndexed { index, block ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Block(block)
            }
        }

        if (content.loadMoreVisible) {
            LoadMoreOnScrollEnd(
                scrollState = scrollState,
                loadMoreOnVisible = content.loadMoreOnVisible,
                itemCount = content.blocks.size,
                onLoadMore = listener::onLoadMore,
            )
            val errorMessage = content.loadMoreErrorMessage
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = listener::onLoadMore) {
                        Text("もう一度試す")
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Block(block: AdminDomainBlocksScreenUiState.Block) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = block.listener::onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(block.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(block.targetText, style = MaterialTheme.typography.bodySmall)
        Text(
            block.reasonText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        block.description?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            block.createdAtText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Editor(editor: AdminDomainBlocksScreenUiState.Editor) {
    AlertDialog(
        onDismissRequest = { if (editor.closeEnabled) editor.listener.onClickClose() },
        title = { Text(editor.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.domain,
                    onValueChange = editor.listener::onDomainChanged,
                    enabled = editor.domainInputEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ドメイン") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                )
                BlockTargetCheckbox(
                    text = "配信を止める",
                    checked = editor.blockDelivery,
                    enabled = editor.inputEnabled,
                    onCheckedChange = editor.listener::onBlockDeliveryChanged,
                )
                BlockTargetCheckbox(
                    text = "受信を止める",
                    checked = editor.blockInbox,
                    enabled = editor.inputEnabled,
                    onCheckedChange = editor.listener::onBlockInboxChanged,
                )
                OutlinedTextField(
                    value = editor.reasonDescription,
                    onValueChange = editor.listener::onReasonDescriptionChanged,
                    enabled = editor.inputEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("理由") },
                    minLines = 2,
                    maxLines = 5,
                )
                editor.noticeText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                editor.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = editor.listener::onClickSave, enabled = editor.saveButtonEnabled) {
                Text(editor.saveButtonText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.deleteButtonVisible) {
                    TextButton(onClick = editor.listener::onClickDelete, enabled = editor.deleteButtonEnabled) {
                        Text(editor.deleteButtonText, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = editor.listener::onClickClose, enabled = editor.closeEnabled) {
                    Text("閉じる")
                }
            }
        },
    )
}

@Composable
private fun BlockTargetCheckbox(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
