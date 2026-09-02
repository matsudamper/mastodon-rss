package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun RequireLoginCard(onClickAdmin: () -> Unit) {
    SectionCard(title = "ログインが要る") {
        Text("管理画面のトップでログインしてから開く。")
        TextLink("管理画面のトップへ", onClickAdmin)
    }
}
