@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package net.matsudamper.mastodon.rss.frontend.format

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName

actual object UnixTimeUtil {
    // 書式はブラウザに決めさせる。自分で組み立てると、見る人の地域の書き方から外れる
    actual fun format(epochSeconds: Long): String = JsDate(epochSeconds.toDouble() * 1000).toLocaleString()
}

@JsName("Date")
private external class JsDate(milliseconds: Double) : JsAny {
    fun toLocaleString(): String
}
