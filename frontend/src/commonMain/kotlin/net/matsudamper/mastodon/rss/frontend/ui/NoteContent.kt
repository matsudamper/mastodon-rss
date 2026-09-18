package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration

@Composable
internal fun NoteContent(
    contentHtml: String,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val urlLinkifier = remember(linkColor) {
        UrlLinkifier(
            linkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        )
    }
    val text = remember(contentHtml, urlLinkifier) {
        urlLinkifier.linkify(htmlToAnnotatedString(contentHtml).text)
    }
    Text(
        text = text,
        modifier = modifier.withBoundedWidth(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 幅が無制限で測られても落ちないようにする。
 *
 * リンクを持つ [Text] は、入ってきた制約の最大幅をそのまま自分の大きさにする。
 * 無制限のまま渡すと Compose が扱える大きさ（1 辺 16777215）を超えて落ちるので、
 * 画面に出しうる幅で頭打ちにする。幅が決まっているときは何もしない。
 */
private fun Modifier.withBoundedWidth(): Modifier = layout { measurable, constraints ->
    val placeable = if (constraints.hasBoundedWidth) {
        measurable.measure(constraints)
    } else {
        measurable.measure(constraints.copy(maxWidth = ContentMaxWidth.roundToPx()))
    }
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}
