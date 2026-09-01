package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

@Composable
fun AdminAccountsScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminAccountsScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminAccountsScreen(
        uiState = uiState,
        onClickNewAccount = { onNavigate(Screen.AdminAccountNew) },
        onClickPublicAccount = { onNavigate(Screen.Account(it)) },
        onClickAdminAccount = { onNavigate(Screen.AdminAccount(it)) },
        onClickAdmin = { onNavigate(Screen.Admin) },
        onClickHome = { onNavigate(Screen.Home) },
    )
}
