package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * こちらのドメイン以外に置かれた画像。
 *
 * Web では HTML の img を重ねる。canvas に描くと画像はブラウザの fetch で取ることになり、
 * 配信元が CORS を許していないと出ないため。
 *
 * @param onError 出せなかったときに呼ぶ。代わりに出すものは呼び出し側が決める
 */
@Composable
internal expect fun ExternalImage(
    url: String,
    contentDescription: String,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
)
