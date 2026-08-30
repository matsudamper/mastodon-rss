package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.entity.ActivityPubId

/**
 * 消した投稿を表す `Tombstone`（墓標）。[DeleteNote] の `object` に入れて相手の inbox へ送る。
 *
 * 送り出すためだけの形で、保存もしないし受け取ったものをこれに読むこともしない。
 * 消えたことだけを表すので本文は持たない。id だけを送る実装もあるが、
 * 型を付けておくと相手側が何を消せばよいか一意に決まる。
 *
 * 名前は ActivityStreams 2.0 の語彙で、`type` に出る値もこれ。別の語にすると相手が削除と分からない。
 */
@Serializable
data class Tombstone(
    /**
     * 消す対象の id
     */
    val id: ActivityPubId,
) {
    /**
     * 相手はこの値を見て、消えたオブジェクトだと判断する。`Tombstone` 以外は入らない
     */
    val type: String = "Tombstone"
}
