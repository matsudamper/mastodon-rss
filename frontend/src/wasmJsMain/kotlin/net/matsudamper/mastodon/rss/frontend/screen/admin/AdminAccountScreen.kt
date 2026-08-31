package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent

@Composable
fun AdminAccountScreen(
    username: String,
    onNavigate: (Screen) -> Unit,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(username, viewModelScope) {
        AdminAccountScreenViewModel(username = username, viewModelScope = viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(username) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminScaffold(title = "@$username の管理", onNavigate = onNavigate) { wide ->
        AdminAccountScreenContent(
            uiState = uiState,
            wide = wide,
            onClickOpenAccount = { onNavigate(Screen.Account(username)) },
            onClickLogin = { onNavigate(Screen.Admin) },
            noteContent = { contentHtml, modifier ->
                NoteContent(contentHtml = contentHtml, modifier = modifier)
            },
        )
    }
}
