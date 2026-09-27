package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.repository.LatestNoteAccountPosition
import net.matsudamper.mastodon.rss.shared.AccountId

/**
 * 公開アカウント一覧の続きを指す印。並び順ごとに鍵が違うので、どの並び順のものかも持つ。
 *
 * 受け渡す形は [AccountsCursor] と同じく JSON を base64 にしたもの。
 *
 * @param order 作ったときの並び順。違う並び順の続きとしては読まない
 * @param epochSecond 並び順の鍵の時刻。最後に投稿した順で投稿が無ければ null
 * @param nano [epochSecond] の秒未満
 * @param id 同じ時刻の中での位置
 * @param notesUpToId 最後に投稿した順で、並べるときに見る投稿の id の上限
 */
@Serializable
data class PublicAccountsCursor(
    @SerialName("order")
    val order: Order,
    @SerialName("epochSecond")
    val epochSecond: Long?,
    @SerialName("nano")
    val nano: Long?,
    @SerialName("id")
    val id: Long,
    @SerialName("notesUpToId")
    val notesUpToId: Long?,
) {
    @Serializable
    enum class Order {
        @SerialName("addedNewest")
        ADDED_NEWEST,

        @SerialName("latestNote")
        LATEST_NOTE,
    }

    /**
     * [Order.ADDED_NEWEST] でなければ null
     */
    fun toAccountPosition(): AccountPosition? {
        if (order != Order.ADDED_NEWEST) return null
        val instant = instant() ?: return null
        return AccountPosition(createdAt = instant, id = AccountId(id))
    }

    /**
     * [Order.LATEST_NOTE] でなければ null
     */
    fun toLatestNoteAccountPosition(): LatestNoteAccountPosition? {
        if (order != Order.LATEST_NOTE) return null
        return LatestNoteAccountPosition(
            notesUpToId = notesUpToId ?: return null,
            latestNoteAt = instant(),
            id = AccountId(id),
        )
    }

    private fun instant(): Instant? {
        val epochSecond = epochSecond ?: return null
        return Instant.ofEpochSecond(epochSecond, nano ?: 0)
    }

    fun encode(): String =
        ENCODER.encodeToString(
            AppJson.encodeToString(serializer(), this).encodeToByteArray(),
        )

    companion object {
        fun of(position: AccountPosition): PublicAccountsCursor = PublicAccountsCursor(
            order = Order.ADDED_NEWEST,
            epochSecond = position.createdAt.epochSecond,
            nano = position.createdAt.nano.toLong(),
            id = position.id.value,
            notesUpToId = null,
        )

        fun of(position: LatestNoteAccountPosition): PublicAccountsCursor = PublicAccountsCursor(
            order = Order.LATEST_NOTE,
            epochSecond = position.latestNoteAt?.epochSecond,
            nano = position.latestNoteAt?.nano?.toLong(),
            id = position.id.value,
            notesUpToId = position.notesUpToId,
        )

        /**
         * 読めなければ null を返す。外から来る値なので、壊れていても投げない
         */
        fun decode(value: String): PublicAccountsCursor? {
            val json = runCatching { DECODER.decode(value).decodeToString() }.getOrNull() ?: return null
            val cursor = runCatching { AppJson.decodeFromString(serializer(), json) }.getOrNull() ?: return null

            // 秒の値は任意の数を入れられる。時刻に直せなければ読めなかったのと同じ扱いにする
            if (runCatching { cursor.instant() }.isFailure) return null

            return cursor
        }

        // URL に載せても壊れない字だけにする。付ける必要が無いので詰め物は落とす
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
    }
}
