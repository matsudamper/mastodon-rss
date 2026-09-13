package net.matsudamper.mastodon.rss.graphql.data

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId

/**
 * 諦めた配信の一覧の続きを指す印。
 *
 * [FollowersCursor] と同じく JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 *
 * @param afterId この id より古いものを返す
 */
@Serializable
data class FailedDeliveriesCursor(
    @SerialName("afterId")
    val afterId: Long,
) {
    fun toDeliveryId(): DeliveryId = DeliveryId(afterId)

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(id: DeliveryId): FailedDeliveriesCursor = FailedDeliveriesCursor(afterId = id.value)

        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない
         */
        fun decode(value: String): FailedDeliveriesCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            return runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull()
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
