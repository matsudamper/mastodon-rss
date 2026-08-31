package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreen
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreenViewModel

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { HomeScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    HomeScreen(
        uiState = uiState,
        onClickAccount = { onNavigate(Screen.Account(it)) },
        onClickHome = { onNavigate(Screen.Home) },
        onClickAdmin = { onNavigate(Screen.Admin) },
    )
}
