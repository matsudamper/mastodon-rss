package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.FeedItemId
import net.matsudamper.mastodon.rss.repository.FeedItemPosition

/**
 * 取り込んだ記事の一覧の続きを指す印。
 *
 * [NotesCursor] と同じく JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 *
 * @param afterEpochSecond この日時より古いものを返す。日時を持たない記事を指すときは null
 * @param afterNano [afterEpochSecond] の秒未満
 * @param afterId 同じ日時の中でこの id より後ろを返す
 */
@Serializable
data class FeedItemsCursor(
    val afterEpochSecond: Long?,
    val afterNano: Long?,
    val afterId: Long,
) {
    fun toPosition(): FeedItemPosition = FeedItemPosition(
        publishedAt = afterEpochSecond?.let { Instant.ofEpochSecond(it, afterNano ?: 0) },
        id = FeedItemId(afterId),
    )

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(position: FeedItemPosition): FeedItemsCursor = FeedItemsCursor(
            afterEpochSecond = position.publishedAt?.epochSecond,
            afterNano = position.publishedAt?.nano?.toLong(),
            afterId = position.id.value,
        )

        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない
         */
        fun decode(value: String): FeedItemsCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            return runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull()
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
