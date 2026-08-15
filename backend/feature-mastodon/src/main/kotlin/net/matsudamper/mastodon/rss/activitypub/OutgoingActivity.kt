package net.matsudamper.mastodon.rss.activitypub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * こちらから相手の inbox に送るアクティビティ。
 *
 * いま送るのは `Follow` に返す `Accept` だけ。Phase 4 で投稿を配信するときの
 * `Create` も同じ形になるので、`type` と `object` を差し替えられるようにしてある。
 *
 * `@context` に `security/v1` は入れない。[Actor] と違って鍵を含まないため。
 */
@Serializable
data class OutgoingActivity(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = DEFAULT_CONTEXT,
    /**
     * このアクティビティ自身の id。相手側で重複を判定するのに使われるので、
     * 送るたびに違う値にする。
     */
    val id: String,
    val type: String,
    /** 送り主のアクター id。署名に使う鍵の持ち主と一致していないと相手に弾かれる */
    val actor: String,
    /** `object` は Kotlin の予約語なので名前を変えて持つ */
    @SerialName("object")
    val target: LinkOrObject,
) {
    companion object {
        const val TYPE_ACCEPT: String = "Accept"

        val DEFAULT_CONTEXT: List<String> = listOf("https://www.w3.org/ns/activitystreams")
    }
}
