// js(...) を書くのに要る。ブラウザの Date を使うのはここだけ
@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.mastodon.rss.frontend.format

import kotlin.js.ExperimentalWasmJsInterop

actual fun formatUnixTime(epochSeconds: Long): String = toLocaleString(epochSeconds.toDouble() * 1000)

/** 書式はブラウザに決めさせる。自分で組み立てると、見る人の地域の書き方から外れる */
private fun toLocaleString(epochMilliseconds: Double): String = js("new Date(epochMilliseconds).toLocaleString()")
