package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun AdminAccountsScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminAccountsScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminAccountsScreen(uiState = uiState, onNavigate = onNavigate)
}

@Composable
private fun AdminAccountsScreen(
    uiState: AdminAccountsScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "アカウント",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminAccountsScreenUiState.Content.Loading -> {
                SectionCard(title = "読み込み中") {
                    Text(
                        text = "一覧を取ってきている。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            AdminAccountsScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onNavigate = onNavigate)
            }

            is AdminAccountsScreenUiState.Content.Error -> {
                SectionCard(title = "一覧を出せない") {
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

            is AdminAccountsScreenUiState.Content.Loaded -> {
                SectionCard(title = "応答するアカウント") {
                    Text(
                        text = "この一覧にある名前が Mastodon から検索できる。名前を選ぶと、" +
                            "そのアカウントとして投稿できる。",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    content.accounts.forEach { account ->
                        AccountRow(account = account, onNavigate = onNavigate)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { uiState.listener.onClickReload() }) {
                            Text("更新")
                        }
                    }
                }

                SectionCard(title = "増やす") {
                    TextLink(
                        text = "アカウントを追加する",
                        onClick = { onNavigate(Screen.AdminAccountNew) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AdminAccountsScreenUiState.Account,
    onNavigate: (Screen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = account.acct,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "フォロワー ${account.followerCount} 人",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text = "このアカウントを管理する",
            onClick = { onNavigate(Screen.AdminAccount(account.username)) },
        )

        TextLink(
            text = "公開されているアカウント画面を開く",
            onClick = { onNavigate(Screen.Account(account.username)) },
        )
    }
}
