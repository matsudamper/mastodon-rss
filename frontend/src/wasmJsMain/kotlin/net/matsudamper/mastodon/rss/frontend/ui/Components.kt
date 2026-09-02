@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.mastodon.rss.frontend.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window

fun openExternalLink(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
}

fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) {
    window.navigator.clipboard.writeText(text).then(
        onFulfilled = {
            onResult(true)
            null
        },
        onRejected = {
            onResult(false)
            null
        },
    )
}
