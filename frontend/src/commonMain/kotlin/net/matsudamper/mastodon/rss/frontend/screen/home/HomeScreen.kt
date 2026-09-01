package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.ui.AccountAvatar
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
internal fun HomeScreen(
    onClickAccount: (String) -> Unit,
    onClickHome: () -> Unit,
    onClickAdmin: () -> Unit,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { HomeScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    PublicScaffold(onClickHome = onClickHome, onClickAdmin = onClickAdmin) { wide ->
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "RSS/AtomをActivityPubで配信中",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            when (val content = uiState.content) {
                HomeScreenUiState.Content.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is HomeScreenUiState.Content.Error -> SectionCard(title = "一覧を出せない") {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = uiState.listener::onClickReload) {
                        Text("もう一度試す")
                    }
                }

                is HomeScreenUiState.Content.Loaded -> LoadedContent(
                    content = content,
                    listener = uiState.listener,
                    wide = wide,
                    onClickAccount = onClickAccount,
                )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    content: HomeScreenUiState.Content.Loaded,
    listener: HomeScreenUiState.Listener,
    wide: Boolean,
    onClickAccount: (String) -> Unit,
) {
    if (content.accounts.isEmpty()) {
        SectionCard(title = "アカウント") {
            Text(
                text = "公開されているアカウントはありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val columnCount = if (wide) 2 else 1
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content.accounts.chunked(columnCount).forEach { rowAccounts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowAccounts.forEach { account ->
                    AccountCard(
                        account = account,
                        onClick = { onClickAccount(account.username) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnCount - rowAccounts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (content.hasMore) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content.loadMoreErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (content.isLoadingMore) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(onClick = listener::onClickLoadMore) {
                        Text(if (content.loadMoreErrorMessage != null) "もう一度試す" else "もっと見る")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: HomeScreenUiState.Account,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountAvatar(username = account.username)
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SelectionContainer {
                        Text(
                            text = account.acct,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onClick) { Text("詳細を見る") }
            }
        }
    }
}
