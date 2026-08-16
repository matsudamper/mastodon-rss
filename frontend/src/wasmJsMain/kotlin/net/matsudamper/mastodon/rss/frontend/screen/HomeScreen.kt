package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    AppScaffold(onNavigate = onNavigate) { wide ->
        Text(
            text = "RSS/AtomをActivityPubで配信中",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // TODO アカウント一覧
        //　ページングで
    }
}
