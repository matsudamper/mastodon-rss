package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

/**
 * 管理画面。
 *
 * 中身はこれから（Phase 8）。ここに置くものが決まっているので、
 * 空でも画面としては切っておく。以前は管理画面がパスに関係なく出ていて、
 * どこからが管理画面なのかがコード上も分からなかった。
 */
@Composable
fun AdminScreen(onNavigate: (Screen) -> Unit) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "管理画面",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionCard(title = "まだ何も無い") {
            Text(
                text =
                "ここに入るのはフィードの登録・削除、アクターごとのフォロワー数と配信エラー、" +
                    "手動での再取得。管理 API（GraphQL）を作ってから繋ぐ。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "この画面は認証を掛ける対象になる。inbox と違って外に開けてはいけない。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
