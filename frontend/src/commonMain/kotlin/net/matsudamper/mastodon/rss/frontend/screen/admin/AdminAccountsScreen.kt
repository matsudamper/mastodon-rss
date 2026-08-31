package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.ui.AccountAvatar
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
internal fun AdminAccountsScreen(
    uiState: AdminAccountsScreenUiState,
    onClickNewAccount: () -> Unit,
    onClickPublicAccount: (String) -> Unit,
    onClickAdminAccount: (String) -> Unit,
    onClickAdmin: () -> Unit,
    onClickHome: () -> Unit,
) {
    AdminScaffold("アカウント", onClickAdmin, onClickHome) { wide ->
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("アカウント一覧", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onClickNewAccount) { Text("追加") }
            }
            when (val content = uiState.content) {
                AdminAccountsScreenUiState.Content.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                AdminAccountsScreenUiState.Content.RequireLogin -> RequireLoginCard(onClickAdmin)
                is AdminAccountsScreenUiState.Content.Error -> SectionCard("一覧を出せない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = uiState.listener::onClickReload) { Text("もう一度試す") }
                }
                is AdminAccountsScreenUiState.Content.Loaded -> Accounts(content.accounts, wide, onClickPublicAccount, onClickAdminAccount)
            }
        }
    }
}

@Composable
private fun Accounts(
    accounts: List<AdminAccountsScreenUiState.Account>,
    wide: Boolean,
    onClickPublicAccount: (String) -> Unit,
    onClickAdminAccount: (String) -> Unit,
) {
    if (accounts.isEmpty()) {
        SectionCard("アカウント") { Text("まだアカウントはありません。下のリンクから追加できます。") }
        return
    }
    val columns = if (wide) 2 else 1
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        accounts.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { account ->
                    AccountCard(account, { onClickPublicAccount(account.username) }, { onClickAdminAccount(account.username) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AdminAccountsScreenUiState.Account, onPublic: () -> Unit, onAdmin: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountAvatar(account.username)
                Column(Modifier.weight(1f)) {
                    SelectionContainer { Text(account.acct, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Text("フォロワー ${account.followerCount} 人", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SelectionContainer {
                Text(account.actorUrl, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("追加: ${account.createdAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onPublic) { Text("公開画面") }
                Button(onClick = onAdmin) { Text("管理画面") }
            }
        }
    }
}
