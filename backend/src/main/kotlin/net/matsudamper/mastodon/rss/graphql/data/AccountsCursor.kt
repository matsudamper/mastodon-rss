package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.shared.AccountId

/**
 * 公開アカウント一覧の続きを指す印。
 *
 * 受け渡す形は JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 * 何を目印に切っているかを変えても、クライアントを直さずに済む。
 *
 * 並び順に使っている作成時刻と id をそのまま持つ。名前で位置を指すと、その行が
 * 消えたときに続きを引けず、同じ名前で作り直されたときは新しい行の位置から返してしまう。
 *
 * @param afterEpochSecond この時刻より後のものを返す
 * @param afterNano [afterEpochSecond] の秒未満
 * @param afterId 同じ時刻の中でこの id より後ろを返す
 */
@Serializable
data class AccountsCursor(
    @SerialName("afterEpochSecond")
    val afterEpochSecond: Long,
    @SerialName("afterNano")
    val afterNano: Long,
    @SerialName("afterId")
    val afterId: Long,
) {
    fun toPosition(): AccountPosition = AccountPosition(
        createdAt = Instant.ofEpochSecond(afterEpochSecond, afterNano),
        id = AccountId(afterId),
    )

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(position: AccountPosition): AccountsCursor = AccountsCursor(
            afterEpochSecond = position.createdAt.epochSecond,
            afterNano = position.createdAt.nano.toLong(),
            afterId = position.id.value,
        )

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
