package net.matsudamper.mastodon.rss.follower

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor

/**
 * フォロワーの記録の置き先。
 *
 * `StoredActorNames` と同じく口だけを決めて、実装は `:backend` が repository に繋ぐ。
 *
 * `Accept` を返す前のフォローも記録する。相手から見ると成立していないので
 * [list] と [count] と [deliveryTargets] には出さないが、送り直されたときに
 * 行を増やさないために覚えておく。
 */
interface FollowerStore {
    /**
     * `Follow` を受けたことを記録する。同じ相手からの記録が既にあれば何もしない
     */
    fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
    )

    /**
     * `Accept` を返せたことを記録して、フォロワーとして数えられるようにする。
     *
     * @param followActivityUri `Accept` を返した `Follow` の id。同じ相手から続けて
     *   `Follow` が届くと、記録されている id は後から来た方に差し替わっている。
     *   まだ `Accept` を返せていない方を数えてしまわないよう、id まで見て絞る
     */
    fun markAccepted(
        username: String,
        followerActorUri: String,
        followActivityUri: String,
        acceptedAt: Instant,
    )

    /**
     * フォローを消す。
     *
     * @param followActivityUri 消す対象を元の `Follow` の id で絞る。null なら id を問わない
     * @return 消したら true
     */
    fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean

    /**
     * 相手のアクターごと消す。こちらのどのアカウントをフォローしていたかに関わらず消える。
     *
     * @return 消えたフォローの数
     */
    fun removeRemoteActor(actorUri: String): Int

    /**
     * フォロワーのアクター URL を URL 順に返す。
     *
     * 位置は件数ではなく直前のページの最後の 1 件で指す。件数で数えると、
     * 読んでいる間にフォローや解除が入るたびに位置がずれる。
     *
     * @param after この URL より後ろを返す。null なら先頭から
     */
    fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String>

    fun count(username: String): Long

    /**
     * 投稿を配る先の inbox。`sharedInbox` があればそちらにまとまっている
     */
    fun deliveryTargets(username: String): List<String>
}
