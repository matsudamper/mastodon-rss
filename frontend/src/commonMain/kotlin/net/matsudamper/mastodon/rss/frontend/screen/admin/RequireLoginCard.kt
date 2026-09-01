package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigation
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun RequireLoginCard() {
    val navigation = rememberNavigation()

    SectionCard(title = "ログインが要る") {
        Text("管理画面のトップでログインしてから開く。")
        TextLink(
            text = "管理画面のトップへ",
            onClick = { navigation.navigate { navigateToAdmin() } },
        )
    }
}
