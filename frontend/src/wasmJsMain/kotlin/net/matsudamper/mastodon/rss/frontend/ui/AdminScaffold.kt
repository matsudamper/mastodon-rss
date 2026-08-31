package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

@Composable
fun AdminScaffold(
    title: String?,
    onNavigate: (Screen) -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    AdminScaffold(
        title = title,
        onClickAdmin = { onNavigate(Screen.Admin) },
        onClickHome = { onNavigate(Screen.Home) },
        content = content,
    )
}
