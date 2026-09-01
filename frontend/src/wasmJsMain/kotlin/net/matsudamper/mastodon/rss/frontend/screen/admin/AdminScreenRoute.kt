package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminLoginPasswordField
import net.matsudamper.mastodon.rss.frontend.ui.openExternalLink

private const val REPOSITORY_URL = "https://github.com/matsudamper/mastodon-rss"

@Composable
fun AdminScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminScreen(
        uiState = uiState,
        onClickAccounts = { onNavigate(Screen.AdminAccounts) },
        onClickNewAccount = { onNavigate(Screen.AdminAccountNew) },
        onClickRepository = { openExternalLink(REPOSITORY_URL) },
        onClickAdmin = { onNavigate(Screen.Admin) },
        onClickHome = { onNavigate(Screen.Home) },
        passwordField = { content, listener ->
            AdminLoginPasswordField(
                password = content.password,
                onPasswordChange = listener::onPasswordChanged,
                onSubmit = listener::onClickLogin,
                enabled = content.inputEnabled && !content.submitting,
                hasError = content.error != null,
            )
        },
    )
}
