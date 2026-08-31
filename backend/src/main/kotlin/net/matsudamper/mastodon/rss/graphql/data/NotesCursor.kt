package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NotePosition

/**
 * 配信した投稿の一覧の続きを指す印。
 *
 * [AccountsCursor] と同じく JSON を base64 にしたもので、外からは中身の無い文字列として扱う。
 *
 * @param afterEpochSecond この時刻より古いものを返す
 * @param afterNano [afterEpochSecond] の秒未満
 * @param afterPublicId 同じ時刻の中でこの id より後ろを返す
 */
@Serializable
data class NotesCursor(
    val afterEpochSecond: Long,
    val afterNano: Long,
    val afterPublicId: String,
) {
    fun toPosition(): NotePosition = NotePosition(
        publishedAt = Instant.ofEpochSecond(afterEpochSecond, afterNano),
        publicId = PublicNoteId(afterPublicId),
    )

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(position: NotePosition): NotesCursor = NotesCursor(
            afterEpochSecond = position.publishedAt.epochSecond,
            afterNano = position.publishedAt.nano.toLong(),
            afterPublicId = position.publicId.value,
        )

        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない
         */
        fun decode(value: String): NotesCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            return runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull()
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
