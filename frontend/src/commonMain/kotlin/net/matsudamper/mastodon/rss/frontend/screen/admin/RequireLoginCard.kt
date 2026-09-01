package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.navigation.NavigatorReceiver
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigation
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun RequireLoginCard(
    navigationEvents: EventSender<NavigatorReceiver>,
) {
    val navigation = rememberNavigation(navigationEvents)

    SectionCard(title = "ログインが要る") {
        Text("管理画面のトップでログインしてから開く。")
        TextLink(
            text = "管理画面のトップへ",
            onClick = { navigation.navigate(Screen.Admin) },
        )
    }
}
