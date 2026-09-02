package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent as WasmNoteContent

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    WasmNoteContent(contentHtml, modifier)
}
