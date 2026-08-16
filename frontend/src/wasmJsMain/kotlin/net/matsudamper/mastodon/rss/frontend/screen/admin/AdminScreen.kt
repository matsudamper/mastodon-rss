package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppBadge
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
fun AdminScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminScreen(uiState = uiState, onNavigate = onNavigate)
}

@Composable
private fun AdminScreen(
    uiState: AdminScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "管理画面",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminScreenUiState.Content.Loading -> {
                LoadingCard()
            }

            is AdminScreenUiState.Content.Login -> {
                LoginCard(content = content, listener = uiState.listener)
            }

            is AdminScreenUiState.Content.LoggedIn -> {
                AccountsCard(accounts = content.accounts, listener = uiState.listener)
                AddAccountCard(content = content.addAccount, listener = uiState.listener)
                LoggedInCard(listener = uiState.listener)
            }

            is AdminScreenUiState.Content.Error -> {
                ErrorCard(content = content, listener = uiState.listener)
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    SectionCard(title = "確認中") {
        Text(
            text = "状態を確かめている。",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoginCard(
    content: AdminScreenUiState.Content.Login,
    listener: AdminScreenUiState.Listener,
) {
    SectionCard(title = "ログイン") {
        when (val input = content.input) {
            AdminScreenUiState.Content.Login.Input.Enabled -> {
                Text(
                    text = "管理画面のパスワードを入れる。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is AdminScreenUiState.Content.Login.Input.Disabled -> {
                Text(
                    text = input.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = content.password,
            onValueChange = { listener.onPasswordChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスワード") },
            singleLine = true,
            enabled = content.inputEnabled && !content.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { listener.onClickLogin() }),
            isError = content.error != null,
        )

        if (content.error != null) {
            Text(
                text = content.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { listener.onClickLogin() },
            // 送信中に押せると、1 回ごとに PBKDF2 を回すものを何度も投げることになる
            enabled = content.inputEnabled && !content.submitting && content.password.isNotEmpty(),
        ) {
            Text(if (content.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun AccountsCard(
    accounts: AdminScreenUiState.Accounts,
    listener: AdminScreenUiState.Listener,
) {
    SectionCard(title = "アカウント") {
        when (accounts) {
            AdminScreenUiState.Accounts.Loading -> {
                Text(
                    text = "読み込んでいる。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is AdminScreenUiState.Accounts.Error -> {
                Text(
                    text = accounts.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                OutlinedButton(onClick = { listener.onClickReloadAccounts() }) {
                    Text("もう一度読み込む")
                }
            }

            is AdminScreenUiState.Accounts.Loaded -> {
                Text(
                    text = "この一覧にある名前が Mastodon から検索できる。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                accounts.items.forEach { account ->
                    AccountRow(account = account)
                }

                OutlinedButton(onClick = { listener.onClickReloadAccounts() }) {
                    Text("更新")
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AdminScreenUiState.Account) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = account.acct,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (account.fromConfigLabel != null) {
                AppBadge(
                    text = account.fromConfigLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

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
    }
}

@Composable
private fun AddAccountCard(
    content: AdminScreenUiState.AddAccount,
    listener: AdminScreenUiState.Listener,
) {
    SectionCard(title = "アカウントを追加") {
        Text(
            text =
            "名前は後から変えられない。Mastodon は一度取得したアカウントを持ち続けるので、" +
                "別の名前にしたい場合は作り直すことになる。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = content.username,
            onValueChange = { listener.onAddAccountUsernameChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ユーザー名") },
            singleLine = true,
            enabled = !content.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { listener.onClickAddAccount() }),
            isError = content.error != null,
        )

        if (content.error != null) {
            Text(
                text = content.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { listener.onClickAddAccount() },
            enabled = content.canSubmit,
        ) {
            Text(if (content.submitting) "追加中..." else "追加")
        }
    }
}

@Composable
private fun LoggedInCard(listener: AdminScreenUiState.Listener) {
    SectionCard(title = "ログイン済み") {
        Text(
            text =
            "ここに入るのはフィードの登録・削除、アカウントごとのフォロワー数と配信エラー、" +
                "手動での再取得。管理 API（GraphQL）を作ってから繋ぐ。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { listener.onClickLogout() }) {
                Text("ログアウト")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    content: AdminScreenUiState.Content.Error,
    listener: AdminScreenUiState.Listener,
) {
    SectionCard(title = "状態が分からない") {
        Text(
            text = content.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "サーバーが動いているかを確かめてから、もう一度試す。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { listener.onClickRetry() }) {
                Text("もう一度確かめる")
            }
        }
    }
}
