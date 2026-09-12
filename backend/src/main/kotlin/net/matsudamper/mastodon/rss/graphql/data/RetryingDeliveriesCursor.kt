package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.RetryingDeliveryPosition
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId

/**
 * 送り直し待ちの配信の一覧の続きを指す印。
 *
 * [NotesCursor] と同じく JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 *
 * @param afterEpochSecond この時刻より後ろを返す
 * @param afterNano [afterEpochSecond] の秒未満
 * @param afterId 同じ時刻の中でこの id より後ろを返す
 */
@Serializable
data class RetryingDeliveriesCursor(
    @SerialName("afterEpochSecond")
    val afterEpochSecond: Long,
    @SerialName("afterNano")
    val afterNano: Long,
    @SerialName("afterId")
    val afterId: Long,
) {
    fun toPosition(): RetryingDeliveryPosition = RetryingDeliveryPosition(
        nextAttemptAt = Instant.ofEpochSecond(afterEpochSecond, afterNano),
        id = DeliveryId(afterId),
    )

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(position: RetryingDeliveryPosition): RetryingDeliveriesCursor = RetryingDeliveriesCursor(
            afterEpochSecond = position.nextAttemptAt.epochSecond,
            afterNano = position.nextAttemptAt.nano.toLong(),
            afterId = position.id.value,
        )

        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない。
         * 時刻にできない数値も読めなかったものとして扱う
         */
        fun decode(value: String): RetryingDeliveriesCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            val cursor = runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull() ?: return null
            return cursor.takeIf { runCatching { it.toPosition() }.isSuccess }
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
