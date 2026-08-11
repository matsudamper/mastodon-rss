package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AppScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

/**
 * トップ。
 *
 * ここに管理画面を出さない。以前は全てのパスで管理画面が出ていたので、
 * アカウント画面のつもりで開いても管理画面が表示されていた。
 */
@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    AppScaffold(onNavigate = onNavigate) { wide ->
        Text(
            text = "RSS/Atom フィードを ActivityPub で配信するサーバー",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccountCard(onNavigate = onNavigate, modifier = Modifier.weight(1f))
                AdminCard(onNavigate = onNavigate, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AccountCard(onNavigate = onNavigate)
                AdminCard(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun AccountCard(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = "アカウント画面", modifier = modifier) {
        Text(
            text =
            "配信しているアカウントの画面は「/@ユーザー名」で開く。" +
                "フィードの取得状況と、直近で配信した記事が見られる。",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextLink(
            text = "/@test-1（動作確認用のアカウント）",
            onClick = { onNavigate(Screen.Account("test-1")) },
        )
    }
}

@Composable
private fun AdminCard(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = "管理画面", modifier = modifier) {
        Text(
            text = "フィードの登録と配信の状況を見るところ。中身は Phase 8 で作る。",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextLink(
            text = "/admin",
            onClick = { onNavigate(Screen.Admin) },
        )
    }
}
