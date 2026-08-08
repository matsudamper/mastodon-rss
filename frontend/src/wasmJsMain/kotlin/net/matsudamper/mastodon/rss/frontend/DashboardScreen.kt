package net.matsudamper.mastodon.rss.frontend

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.admin.api.AdminSessionResponse

/**
 * ログイン後の画面。
 *
 * フィードの管理は Phase 5 以降に足す。いまはログインできていることと、
 * パスワードの作り直しの導線だけ。
 */
@Composable
internal fun DashboardScreen(
    api: AdminApiClient,
    onSessionChanged: (AdminSessionResponse) -> Unit,
    onOpenPasswordHash: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AdminPage(title = "mastodon-rss 管理画面") {
        Text("ログイン中。")
        Text(
            text = "フィードの登録と配信状況はここに足していく。",
            style = MaterialTheme.typography.bodyMedium,
        )

        val currentError = errorMessage
        if (currentError != null) {
            Text(text = currentError, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = onOpenPasswordHash) {
            Text("パスワードハッシュを作り直す")
        }

        Button(
            enabled = !busy,
            onClick = {
                busy = true
                errorMessage = null
                scope.launch {
                    when (val result = api.logout()) {
                        is AdminResult.Success -> onSessionChanged(result.value)
                        is AdminResult.Failure -> errorMessage = result.message
                    }
                    busy = false
                }
            },
        ) {
            Text("ログアウト")
        }
    }
}
