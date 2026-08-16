package net.matsudamper.mastodon.rss.actor

/**
 * 固定アクター以外に応答するアクターの名前の引き先。
 *
 * どこに保存されているかは [ActorDirectory] の関心ではないので、
 * 名前を渡して引けることだけを決めておく。
 */
fun interface StoredActorNames {
    /**
     * 保存されている名前を返す。無ければ null。
     *
     * 大文字小文字の揺れは実装側で吸収し、返すのは保存されている綴り。
     * 要求された綴りをそのまま返すと、`Feed1` と `feed1` が別のアクター ID になり、
     * 相手側に両方がキャッシュされる。
     */
    fun find(username: String): String?
}
