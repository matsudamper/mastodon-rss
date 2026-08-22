package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink
import net.matsudamper.mastodon.rss.frontend.ui.openExternalLink // pragma: allowlist secret

private const val REPOSITORY_URL = "https://github.com/" + "matsudamper" + "/mastodon-rss" // pragma: allowlist secret

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

            AdminScreenUiState.Content.LoggedIn -> {
                MenuCard(onNavigate = onNavigate)
                LoggedInCard(listener = uiState.listener)
                AboutCard()
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

/**
 * 管理画面の中の各画面への入口。
 *
 * 操作そのものはここに置かない。1 つの画面に並べると、開いた時点で
 * 必要のない問い合わせまで走り、URL でその操作を指せなくなる。
 */
@Composable
private fun MenuCard(onNavigate: (Screen) -> Unit) {
    SectionCard(title = "できること") {
        TextLink(
            text = "アカウントの一覧",
            onClick = { onNavigate(Screen.AdminAccounts) },
        )
        TextLink(
            text = "アカウントの追加",
            onClick = { onNavigate(Screen.AdminAccountNew) },
        )
        Text(
            text = "フィードの登録・削除、フォロワー数と配信エラー、手動での再取得はこれから作る。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = "このソフトウェア") {
        Text(
            text = "ソースコードは GitHub で公開している。",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextLink(
            text = "mastodon-rss",
            onClick = { openExternalLink(REPOSITORY_URL) },
        )
    }
}

@Composable
private fun LoggedInCard(listener: AdminScreenUiState.Listener) {
    SectionCard(title = "ログイン済み") {
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
