package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

/**
 * 知らないパスを開いたとき。
 *
 * サーバーは画面のパスを判別できないので、ここに来ても HTTP は 200 のまま。
 * どのパスを開いたのかを出しておかないと、綴りの間違いなのか
 * 消えたアカウントなのかが分からない。
 */
@Composable
fun NotFoundScreen(
    requestedPath: String,
    onNavigate: (Screen) -> Unit,
) {
    AppScaffold(onNavigate = onNavigate) { _ ->
        Text(
            text = "このパスの画面は無い",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionCard(title = "開こうとしたパス") {
            Text(
                text = requestedPath,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text =
                "アカウントの画面は「/@ユーザー名」。ActivityPub の Actor JSON は「/users/ユーザー名」で、" +
                    "こちらはブラウザ向けの画面ではない。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextLink(
                text = "トップへ",
                onClick = { onNavigate(Screen.Home) },
            )
        }
    }
}
