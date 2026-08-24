package net.matsudamper.mastodon.rss.delivery

import net.matsudamper.mastodon.rss.actor.ActorUrls

/**
 * アクティビティに署名を付けて相手の inbox に POST する。
 *
 * ネットワークに出る唯一の口なので interface にしておく。フォローに
 * `Accept` を返せているかどうかは、ここを差し替えれば実際に送らずに確かめられる。
 *
 * リトライは持たない。失敗したらその場で諦めて [DeliveryResult.Failed] を返す。
 * 配信キューは投稿を配る Phase 4 で作る。TODO.md に項目がある。
 */
interface ActivityDelivery : AutoCloseable {
    /**
     * @param inbox 宛先。https で、相手のアクターと同じホストであることを
     *   確認済みのものだけを渡すこと。相手が指定した URL をそのまま渡すと、
     *   こちらのサーバーから任意の宛先に POST させられる。確認は
     *   [net.matsudamper.mastodon.rss.actor.RemoteActors.findInbox] で行っている
     * @param sender 送り主になるこちらのアクター。署名の `keyId` はここから決まる
     * @param body 署名対象になる JSON のバイト列。`Digest` はこれに対して計算される
     */
    suspend fun deliver(
        inbox: String,
        sender: ActorUrls,
        body: ByteArray,
    ): DeliveryResult
}

/** [ActivityDelivery.deliver] の結果 */
sealed interface DeliveryResult {
    data object Delivered : DeliveryResult

    /**
     * 送れなかった。
     *
     * @param reason ログに出す理由。相手のサーバーが 4xx を返したのか、
     *   そもそも届かなかったのかで対応が変わるので文字列で残す
     */
    data class Failed(
        val reason: String,
    ) : DeliveryResult
}
