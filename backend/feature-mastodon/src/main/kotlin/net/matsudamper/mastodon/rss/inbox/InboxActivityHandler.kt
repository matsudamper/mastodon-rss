package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls

/**
 * inbox が受け取ったアクティビティ 1 種類ぶんの処理。
 *
 * [InboxService] が `type` で引き当てて呼ぶ。種類が増えるたびに分岐を足すのではなく
 * 実装を足す形にしてあるのは、Phase 3 で `Undo` と `Delete` が、Phase 6 以降で
 * さらに別の種類が乗るため。引き当てられなかった種類は何もせずに 202 で返るので、
 * 未対応のアクティビティを受けても相手に再送させない。
 *
 * 呼ばれる時点で、署名の検証と「署名した鍵の持ち主とアクティビティの実行者が同じか」の
 * 確認は済んでいる。実装は [signer] を本人として扱ってよい。
 */
interface InboxActivityHandler {
    /** 処理するアクティビティの `type`。`"Follow"` のように綴りそのものを返す */
    val type: String

    /**
     * 中身の処理をする。
     *
     * 戻り値を持たないのは、inbox が処理の成否に関わらず 202 を返すため。
     * 失敗しても相手に伝える口が無いので、何が起きたかを残すのは実装側の責任になる。
     *
     * @param recipient 受け取ったこちらのアクター
     * @param signer 送ってきた相手のアクター id。署名を検証した結果の持ち主で、自称ではない
     * @param activity 型に落としたアクティビティ
     * @param raw 受け取った JSON そのもの。`Accept` の `object` のように、
     *   相手に返すときに元の形のまま入れる必要があるものがあるので捨てずに渡す
     */
    suspend fun handle(
        recipient: ActorUrls,
        signer: String,
        activity: InboxActivity,
        raw: JsonObject,
    )
}
