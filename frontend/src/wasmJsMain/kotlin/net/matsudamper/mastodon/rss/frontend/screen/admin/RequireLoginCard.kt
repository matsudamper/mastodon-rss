package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

/**
 * ログインが要る画面を、ログインしていない状態で開いたとき。
 *
 * 画面を分けた分、URL を直接開いたりセッションが切れたりして
 * ここに来ることが増える。フォームをこの場に出すと、パスワードを入れる場所が
 * 画面ごとに散らばるので、入口はトップの 1 つに寄せる。
 */
@Composable
fun RequireLoginCard(onNavigate: (Screen) -> Unit) {
    SectionCard(title = "ログインが要る") {
        Text(
            text = "管理画面のトップでログインしてから開く。",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextLink(
            text = "管理画面のトップへ",
            onClick = { onNavigate(Screen.Admin) },
        )
    }
}
