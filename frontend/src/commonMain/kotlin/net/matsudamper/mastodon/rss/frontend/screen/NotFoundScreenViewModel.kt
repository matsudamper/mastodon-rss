package net.matsudamper.mastodon.rss.frontend.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.ScreenNavigator
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffoldListener

class NotFoundScreenViewModel(
    private val navigator: ScreenNavigator,
) {

    val uiStateFlow: StateFlow<NotFoundScreenUiState> =
        MutableStateFlow(
            NotFoundScreenUiState(
                listener =
                object : NotFoundScreenUiState.Listener {
                    override fun onClickHome() {
                        navigator.navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigator.navigate(Screen.Admin)
                    }
                },
            ),
        ).asStateFlow()
}

data class NotFoundScreenUiState(
    val listener: Listener,
) {
    interface Listener : PublicScaffoldListener
}
