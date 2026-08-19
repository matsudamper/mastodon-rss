package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
fun AdminAccountScreen(
    username: String,
    onNavigate: (Screen) -> Unit,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) {
        AdminAccountScreenViewModel(username = username, viewModelScope = viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(username) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminAccountScreen(uiState = uiState, onNavigate = onNavigate)
}

@Composable
private fun AdminAccountScreen(
    uiState: AdminAccountScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = uiState.acct,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminAccountScreenUiState.Content.Loading -> {
                SectionCard(title = "読み込み中") {
                    Text(text = "アカウントを取ってきている。", style = MaterialTheme.typography.bodyMedium)
                }
            }

            AdminAccountScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onNavigate = onNavigate)
            }

            AdminAccountScreenUiState.Content.NotFound -> {
                SectionCard(title = "このアカウントは無い") {
                    Text(
                        text = "この名前では Mastodon からも見つからない。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextLink(
                        text = "アカウントの一覧に戻る",
                        onClick = { onNavigate(Screen.AdminAccounts) },
                    )
                }
            }

            is AdminAccountScreenUiState.Content.Error -> {
                SectionCard(title = "この画面を出せない") {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { uiState.listener.onClickReload() }) {
                            Text("もう一度試す")
                        }
                    }
                }
            }

            is AdminAccountScreenUiState.Content.Loaded -> {
                AccountCard(account = content.account, onNavigate = onNavigate)

                // TODO: 新しい投稿の入力欄と、配信した投稿の一覧は Phase 4 でここに足す
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AdminAccountScreenUiState.Account,
    onNavigate: (Screen) -> Unit,
) {
    SectionCard(title = "このアカウント") {
        Text(
            text = "フォロワー ${account.followerCount} 人",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = account.actorUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (account.createdAt != null) {
            Text(
                text = "追加: ${account.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextLink(
            text = "公開されているアカウント画面を開く",
            onClick = { onNavigate(Screen.Account(account.username)) },
        )
    }
}
