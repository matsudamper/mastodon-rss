package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.httpsignature.PublicKeys

/**
 * 相手のサーバーのアクターを引く口。
 *
 * 署名の検証だけを見る側からは [PublicKeys] として渡す
 */
interface RemoteActors : PublicKeys {
    /**
     * アクター文書から、配信と記録に要る部分をまとめて引く。引けなければ null。
     *
     * 別々に取りに行くと、間に鍵が入れ替わったときに記録した鍵と inbox が
     * 別の版のものになる。
     */
    suspend fun findActor(actorId: String): RemoteActor?
}

/**
 * @param inbox 配信先。https で、[actorId] と同じホストのものに限る。緩めると、
 *   相手がアクター文書に他所の URL を書くだけで任意の宛先に POST させられる
 * @param sharedInbox 同じインスタンス宛をまとめて送れる inbox。[inbox] と同じ制限がかかる
 */
data class RemoteActor(
    val actorId: String,
    val inbox: String,
    val sharedInbox: String?,
    val publicKeyPem: String,
)
