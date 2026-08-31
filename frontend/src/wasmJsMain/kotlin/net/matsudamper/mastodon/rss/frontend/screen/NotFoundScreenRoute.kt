package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

@Composable
fun NotFoundScreen(
    requestedPath: String,
    onNavigate: (Screen) -> Unit,
) {
    NotFoundScreen(
        requestedPath = requestedPath,
        onClickHome = { onNavigate(Screen.Home) },
        onClickAdmin = { onNavigate(Screen.Admin) },
    )
}
