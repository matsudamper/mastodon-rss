package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreenUiState
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreenViewModel
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { HomeScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    HomeScreen(
        uiState = uiState,
        onNavigate = onNavigate,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { wide ->
        Text(
            text = "RSS/AtomをActivityPubで配信中",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            HomeScreenUiState.Content.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is HomeScreenUiState.Content.Error -> {
                SectionCard(title = "一覧を出せない") {
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

            is HomeScreenUiState.Content.Loaded -> {
                if (content.accounts.isEmpty()) {
                    SectionCard(title = "アカウント") {
                        Text(
                            text = "公開されているアカウントはありません。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val columnCount = if (wide) 2 else 1
                    val rows = content.accounts.chunked(columnCount)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rows.forEach { rowAccounts ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                rowAccounts.forEach { account ->
                                    AccountCard(
                                        account = account,
                                        onClick = { onNavigate(Screen.Account(account.username)) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowAccounts.size < columnCount) {
                                    repeat(columnCount - rowAccounts.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (content.hasMore) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (content.loadMoreErrorMessage != null) {
                                    Text(
                                        text = content.loadMoreErrorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                if (content.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Button(onClick = { uiState.listener.onClickLoadMore() }) {
                                        Text(if (content.loadMoreErrorMessage != null) "もう一度試す" else "もっと見る")
                                    }
                                }
                            }
                        }
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
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                val colors = avatarColors(account.username)
                Box(
                    modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = account.username.firstOrNull()?.uppercase() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onClick) {
                    Text("詳細を見る")
                }
            }
        }
    }
}

private fun avatarColors(username: String): List<Color> {
    val palette =
        listOf(
            Color(0xFF4A3FD1) to Color(0xFF7B6FF0),
            Color(0xFF1E7A6F) to Color(0xFF3FB8A6),
            Color(0xFFB05A1E) to Color(0xFFE79A4B),
            Color(0xFF8C2F6B) to Color(0xFFD167AC),
            Color(0xFF2F5FA8) to Color(0xFF6795DE),
        )

    val index = (username.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size
    val (start, end) = palette[index]
    return listOf(start, end)
}
