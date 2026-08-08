package dev.matsudamper.mastodonrss.frontend

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.matsudamper.mastodonrss.admin.api.AdminConfigNames
import dev.matsudamper.mastodonrss.admin.api.AdminSessionResponse
import kotlinx.coroutines.launch

/**
 * ログイン画面。
 *
 * ハッシュが未設定のときはログインする手段が無いので、代わりにハッシュ生成へ誘導する。
 * 「パスワードが違う」と表示し続けても運用側は原因に辿り着けないため、
 * 状態をそのまま出す。
 */
@Composable
internal fun LoginScreen(
    api: AdminApiClient,
    session: AdminSessionResponse,
    onSessionChanged: (AdminSessionResponse) -> Unit,
    onOpenPasswordHash: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AdminPage(title = "mastodon-rss 管理画面") {
        if (!session.loginConfigured) {
            Text("${AdminConfigNames.ENV_PASSWORD_HASH} が設定されていないのでログインできない。")
            Text(
                text =
                    "パスワードハッシュを作り、環境変数 ${AdminConfigNames.ENV_PASSWORD_HASH} に入れて" +
                        "サーバーを起動し直すと、この画面からログインできるようになる。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenPasswordHash) {
                Text("パスワードハッシュを作る")
            }
            return@AdminPage
        }

        val submit = {
            if (!busy) {
                busy = true
                errorMessage = null
                scope.launch {
                    when (val result = api.login(password)) {
                        is AdminResult.Success -> {
                            password = ""
                            onSessionChanged(result.value)
                        }

                        is AdminResult.Failure -> {
                            errorMessage = result.message
                        }
                    }
                    busy = false
                }
            }
            Unit
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("パスワード") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        val currentError = errorMessage
        if (currentError != null) {
            Text(text = currentError, color = MaterialTheme.colorScheme.error)
        }

        Button(onClick = submit, enabled = !busy && password.isNotEmpty()) {
            Text("ログイン")
        }

        // 設定済みのハッシュを作り直せるのはログインした人だけなので、
        // ここからハッシュ生成には誘導しない。パスワードを忘れた場合は
        // 環境変数を外して起動し直すところからになる
        Text(
            text =
                "パスワードが分からなくなった場合は、${AdminConfigNames.ENV_PASSWORD_HASH} を外して" +
                    "サーバーを起動し直すと、この画面からハッシュを作り直せる。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
