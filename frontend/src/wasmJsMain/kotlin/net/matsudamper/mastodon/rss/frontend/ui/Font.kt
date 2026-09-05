package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

/**
 * 画面で使う日本語フォントを、配信元から取ってきて組み立てる。
 *
 * Compose Multiplatform for Web は canvas に描くので、ブラウザが持っているフォントは
 * 使われない。何も読み込まないと日本語が豆腐（□）になる。
 *
 * やり方は [kake-bo](https://github.com/matsudamper/kake-bo) と同じで、
 * フォントのファイルを静的ファイルと一緒に配信し、起動後に取ってきて
 * [FontFamily] を組み立てる。`index.html` の `@font-face` では canvas に効かない。
 *
 * ファイルは `frontend/src/wasmJsMain/resources/fonts/` に置いてある。
 * 成果物に入るので `STATIC_SRC_DIR` 配下に出て、`:backend` が `/fonts/...` で返す。
 *
 * 太さは 3 つだけ入れている。1 ファイル 5MB 台で、参考にした kake-bo のように
 * 9 つ全部入れると 50MB になる。無い太さは Compose が近いものに寄せるので、
 * 実際に使っている W400 / W500 / W700 があれば足りる。増やすなら [FONTS] に足す。
 */
@Stable
private class AppFontState {
    /** 読み込めたぶんだけを持つ。1 つも読めていない間は既定のフォント */
    var fontFamily: FontFamily by mutableStateOf(FontFamily.Default)
        private set

    private var started = false
    private val loaded = mutableListOf<Font>()

    /**
     * 取得を始める。2 回目以降は何もしない。
     *
     * 1 つ読めるたびに [fontFamily] を差し替える。全部揃うまで待つと、
     * 最初の描画から数秒間ずっと豆腐のままになる。
     */
    suspend fun load() {
        if (started) return
        started = true

        HttpClient(Js).use { client ->
            FONTS.forEach { spec ->
                val fontBytes =
                    runCatching { client.get("$FONT_DIRECTORY/${spec.fileName}").readRawBytes() }
                        .getOrNull()
                        // 読めなくても画面は出す。日本語が豆腐になるだけで、操作はできる
                        ?: return@forEach

                loaded +=
                    Font(
                        identity = spec.fileName,
                        data = fontBytes,
                        weight = spec.weight,
                        style = FontStyle.Normal,
                    )
                fontFamily = FontFamily(loaded.toList())
            }
        }
    }

    private data class FontSpec(
        val fileName: String,
        val weight: FontWeight,
    )

    private companion object {
        const val FONT_DIRECTORY = "/fonts"

        val FONTS =
            listOf(
                FontSpec("NotoSansJP-Regular.ttf", FontWeight.W400),
                FontSpec("NotoSansJP-Medium.ttf", FontWeight.W500),
                FontSpec("NotoSansJP-Bold.ttf", FontWeight.W700),
            )
    }
}

/**
 * 日本語フォントの [FontFamily]。読み込みが終わるまでは既定のフォントを返す。
 */
@Composable
fun rememberAppFontFamily(): FontFamily {
    val state = remember { AppFontState() }

    LaunchedEffect(state) {
        state.load()
    }

    return state.fontFamily
}
