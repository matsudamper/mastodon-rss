package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.browser.window
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.LocalSnackbarEvents
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent
import net.matsudamper.mastodon.rss.frontend.ui.copyToClipboard
import net.matsudamper.mastodon.rss.frontend.ui.openExternalLink

@Composable
fun AccountScreen(username: String, onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val snackbarEvents = LocalSnackbarEvents.current
    val viewModel = remember(viewModelScope, username, snackbarEvents) {
        AccountScreenViewModel(
            username = username,
            host = window.location.host,
            viewModelScope = viewModelScope,
            copyToClipboard = ::copyToClipboard,
            snackbarEvents = snackbarEvents,
        )
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AccountScreen(
        username = username,
        uiState = uiState,
        onClickHome = { onNavigate(Screen.Home) },
        onClickAdmin = { onNavigate(Screen.Admin) },
        onClickOperator = { onNavigate(Screen.Account(it)) },
        onOpenExternal = ::openExternalLink,
        noteContent = { html, modifier -> NoteContent(html, modifier) },
    )
}
