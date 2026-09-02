package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.AdminLoginPasswordField
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

private const val REPOSITORY_URL = "https://github.com/matsudamper/mastodon-rss"

@Composable
internal fun AdminScreen(
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    val eventReceiver = remember(navController) {
        object : AdminScreenViewModel.Event {
            override suspend fun navigate(screen: Screen) {
                navController.navigate(screen)
            }
        }
    }
    LaunchedEffect(viewModel.eventHandler, eventReceiver) {
        viewModel.eventHandler.collect(eventReceiver)
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminContent(
        uiState = uiState,
        platform = platform,
    )
}

@Composable
internal fun AdminContent(
    uiState: AdminScreenUiState,
    platform: ScreenPlatform,
) {
    AdminScaffold(title = null, listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("管理画面", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (val content = uiState.content) {
                AdminScreenUiState.Content.Loading -> SectionCard(title = "確認中") {
                    Text("状態を確かめている。")
                }

                is AdminScreenUiState.Content.Login -> LoginCard(content, uiState.listener, ::AdminLoginPasswordField)

                AdminScreenUiState.Content.LoggedIn -> {
                    MenuCard(listener = uiState.listener)
                    SectionCard(title = "ログイン済み") {
                        OutlinedButton(onClick = uiState.listener::onClickLogout) { Text("ログアウト") }
                    }
                    SectionCard(title = "このソフトウェア") {
                        Text("ソースコードは GitHub で公開している。")
                        TextLink(
                            text = "mastodon-rss",
                            onClick = { platform.openExternalLink(REPOSITORY_URL) },
                        )
                    }
                }

                is AdminScreenUiState.Content.Error -> SectionCard(title = "状態が分からない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    Text(
                        "サーバーが動いているかを確かめてから、もう一度試す。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = uiState.listener::onClickRetry) { Text("もう一度確かめる") }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    content: AdminScreenUiState.Content.Login,
    listener: AdminScreenUiState.Listener,
    passwordField: @Composable (String, (String) -> Unit, () -> Unit, Boolean, Boolean) -> Unit,
) {
    SectionCard(title = "ログイン") {
        Text(
            when (val input = content.input) {
                AdminScreenUiState.Content.Login.Input.Enabled -> "管理画面のパスワードを入れる。"
                is AdminScreenUiState.Content.Login.Input.Disabled -> input.message
            },
        )
        passwordField(
            content.password,
            listener::onPasswordChanged,
            listener::onClickLogin,
            content.inputEnabled && !content.submitting,
            content.error != null,
        )
        content.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = listener::onClickLogin,
            enabled = content.inputEnabled && !content.submitting && content.password.isNotEmpty(),
        ) {
            Text(if (content.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun MenuCard(listener: AdminScreenUiState.Listener) {
    SectionCard(title = "できること") {
        TextLink("アカウントの一覧", listener::onClickAccounts)
        TextLink("アカウントの追加", listener::onClickNewAccount)
        Text("投稿とフォロワー数は、一覧からアカウントを選んだ先にある。")
        Text(
            "フィードの登録・削除、配信エラーの確認、手動での再取得はこれから作る。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
