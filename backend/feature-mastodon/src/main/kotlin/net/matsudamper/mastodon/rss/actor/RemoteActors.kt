package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.httpsignature.PublicKeys

/**
 * 相手のサーバーのアクターを引く口。
 *
 * 公開鍵も inbox も同じアクター文書の中にあり、取りに行く手順も
 * 安全のための決まりも共通なので 1 つの口にまとめる。
 *
 * 署名の検証だけを見る側からは [PublicKeys] として渡す。検証は
 * 「鍵をどこから持ってくるか」と関係が無いので、そちらの口は狭いままにしておく。
 */
interface RemoteActors : PublicKeys {
    /**
     * 配信先の inbox を引く。引けなければ null。
     *
     * 返す URL は https で、アクター id と同じホストのものに限る。
     * ここを緩めると、相手が自分のアクター文書に他所の URL を書くだけで、
     * こちらのサーバーから任意の宛先に POST させられる。
     */
    suspend fun findInbox(actorId: String): String?
}
