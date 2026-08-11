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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.OutlinedBox
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

/**
 * 管理画面。いまあるのはログインだけ。
 *
 * 読み込みの間に入力欄を出さないのは、出してから消えると入力の途中で消えることになるため。
 */
@Composable
fun AdminScreen(onNavigate: (Screen) -> Unit) {
    val state = rememberAdminScreenState()
    val scope = rememberCoroutineScope()

    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "管理画面",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val uiState = state.uiState) {
            AdminUiState.Loading -> {
                LoadingCard()
            }

            is AdminUiState.Login -> {
                LoginCard(
                    uiState = uiState,
                    onPasswordChange = state::updatePassword,
                    onSubmit = { scope.launch { state.login() } },
                )
            }

            AdminUiState.LoggedIn -> {
                LoggedInCard(onLogout = { scope.launch { state.logout() } })
            }

            AdminUiState.NotConfigured -> {
                NotConfiguredCard()
            }

            is AdminUiState.Unavailable -> {
                UnavailableCard(
                    message = uiState.message,
                    onRetry = { scope.launch { state.reload() } },
                )
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

/** 入れるのはパスワードだけ。運用者ひとりなので、ユーザー名を足しても覚えるものが増える */
@Composable
private fun LoginCard(
    uiState: AdminUiState.Login,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    SectionCard(title = "ログイン") {
        Text(
            text = "管理画面のパスワードを入れる。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスワード") },
            singleLine = true,
            enabled = !uiState.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = uiState.error != null,
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSubmit,
            // 送信中に押せると、1 回ごとに PBKDF2 を回すものを何度も投げることになる
            enabled = !uiState.submitting && uiState.password.isNotEmpty(),
        ) {
            Text(if (uiState.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun LoggedInCard(onLogout: () -> Unit) {
    SectionCard(title = "ログイン済み") {
        Text(
            text =
                "ここに入るのはフィードの登録・削除、アクターごとのフォロワー数と配信エラー、" +
                    "手動での再取得。管理 API（GraphQL）を作ってから繋ぐ。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onLogout) {
                Text("ログアウト")
            }
        }
    }
}

/** 何を入れても通らないので、入力欄は出さない */
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

/** 状態を聞けなかったとき。入力欄を出すと、パスワードの問題だと思って何度も試すことになる */
@Composable
private fun UnavailableCard(
    message: String,
    onRetry: () -> Unit,
) {
    SectionCard(title = "状態が分からない") {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "サーバーが動いているかを確かめてから、もう一度試す。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) {
                Text("もう一度確かめる")
            }
        }
    }
}
