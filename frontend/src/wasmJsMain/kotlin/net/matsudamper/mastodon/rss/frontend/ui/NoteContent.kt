package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement

/**
 * 配信した投稿の本文を表示する。
 *
 * [androidx.compose.ui.viewinterop.HtmlElementView] は Compose のレイアウト上の
 * サイズに合わせて HTML を重ねるため、高さを指定しないと本文が描画されない。
 * ここではサニタイズ済みの単純な HTML だけを扱うので、プレーンテキストに直して
 * [Text] で出す。
 */
@Composable
fun NoteContent(
    contentHtml: String,
    modifier: Modifier = Modifier,
) {
    val text = remember(contentHtml) { htmlToPlainText(contentHtml) }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

internal fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""

    val div = document.createElement("div") as HTMLDivElement
    div.innerHTML = html
    return div.innerText.trim()
}
