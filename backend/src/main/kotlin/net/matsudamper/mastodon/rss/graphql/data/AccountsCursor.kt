package net.matsudamper.mastodon.rss.graphql.data

import java.util.Base64
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson

/**
 * 公開アカウント一覧の続きを指す印。
 *
 * 受け渡す形は JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 * 何を目印に切っているかを変えても、クライアントを直さずに済む。
 *
 * @param afterUsername この名前の次から返す
 */
@Serializable
data class AccountsCursor(
    val afterUsername: String,
) {
    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない
         */
        fun decode(value: String): AccountsCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            return runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull()
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
