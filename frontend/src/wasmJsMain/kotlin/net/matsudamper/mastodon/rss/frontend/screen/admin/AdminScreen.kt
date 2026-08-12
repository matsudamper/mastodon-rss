package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.OutlinedBox
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
fun AdminScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    DisposableEffect(viewModel) {
        onDispose { viewModel.onDispose() }
    }

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
                LoggedInCard(listener = uiState.listener)
            }

            AdminScreenUiState.Content.NotConfigured -> {
                NotConfiguredCard()
            }

            is AdminScreenUiState.Content.Unavailable -> {
                UnavailableCard(content = content, listener = uiState.listener)
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    SectionCard(title = "確認中") {
        Text(
            text = "ログインしているかをサーバーに聞いている。",
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
        Text(
            text = "管理画面のパスワードを入れる。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = content.password,
            onValueChange = { listener.onPasswordChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスワード") },
            singleLine = true,
            enabled = !content.submitting,
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
            enabled = !content.submitting && content.password.isNotEmpty(),
        ) {
            Text(if (content.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun LoggedInCard(listener: AdminScreenUiState.Listener) {
    SectionCard(title = "ログイン済み") {
        Text(
            text =
            "ここに入るのはフィードの登録・削除、アクターごとのフォロワー数と配信エラー、" +
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
private fun NotConfiguredCard() {
    SectionCard(title = "ログインできない") {
        Text(
            text =
            "サーバーに ADMIN_PASSWORD_HASH が設定されていないので、ログインする手段が無い。" +
                "パスワードのハッシュを作って環境変数に入れ、サーバーを起動し直すこと。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedBox {
            Text(
                text = "形式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 中身が英字なのは、等幅を指定すると日本語のグリフが無く豆腐になるため
            SelectionContainer {
                Text(
                    text = "pbkdf2-sha256:<iterations>:<salt>:<hash>",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun UnavailableCard(
    content: AdminScreenUiState.Content.Unavailable,
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
