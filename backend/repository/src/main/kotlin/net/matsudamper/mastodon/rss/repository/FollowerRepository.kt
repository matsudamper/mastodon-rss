package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * フォロワーの読み書き。
 *
 * `remote_actors` と `followers` の 2 テーブルにまたがるが、トランザクションの境界を
 * 呼び出し側に漏らさないよう口は 1 つにする。
 *
 * こちらのアカウントを名前で指すのは、`ACTOR_USERNAME` で決まる組み込みアカウントが
 * `accounts` に行を持たないため。
 *
 * `Follow` を受けただけの相手はまだフォロワーではないので、[list] と [count] と
 * [deliveryTargets] は `Accept` を返せたものだけを対象にする。
 */
interface FollowerRepository {
    /**
     * `Follow` を受けたことを記録する。`Accept` を返す前の状態で入る。
     *
     * `Accept` を返し損ねると相手は同じ `Follow` を送り直してくるので、
     * 二重に受けても行は増やさない。
     */
    fun record(follow: IncomingFollow)

    /**
     * `Accept` を返せたことを記録して、フォロワーとして数えられるようにする。
     *
     * @param followActivityUri `Accept` を返した `Follow` の id。同じ相手から続けて
     *   `Follow` が届くと、記録されている id は後から来た方に差し替わっている。
     *   まだ `Accept` を返せていない方を数えてしまわないよう、id まで見て絞る
     * @return 対象の行があれば true
     */
    fun markAccepted(
        username: String,
        followerActorUri: String,
        followActivityUri: String,
        acceptedAt: Instant,
    ): Boolean

    /**
     * フォローを消す。`Undo{Follow}` で呼ぶ。
     *
     * @param followActivityUri 消す対象を元の `Follow` の id で絞る。`Undo` に id だけが
     *   来た場合、それが `Follow` の id だったのかは記録と突き合わせるしか確かめようが無い。
     *   `Follow` だと分かる場合は null を渡して id を問わずに消す
     * @return 消したら true
     */
    fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean

    /**
     * 相手のアクターごと消す。`Delete{Actor}` で呼ぶ。
     *
     * こちらのどのアカウントをフォローしていたかに関わらず全部消える。
     *
     * @return 消えたフォローの数
     */
    fun removeRemoteActor(actorUri: String): Int

    /**
     * フォロワーのアクター URL を URL 順に返す。
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
     * 投稿を配る先の inbox を返す。
     *
     * `sharedInbox` を持つ相手はそちらにまとめ、重複した宛先は 1 つにする
     */
    fun deliveryTargets(username: String): List<String>

    /**
     * アカウントを問わず、フォローの記録が 1 件でもあるか。
     *
     * 鍵の自動生成を止める判断に使うので、`Accept` を返せていないものも数える
     */
    fun hasAny(): Boolean
}

/**
 * @param receivedAt 受け取った時刻。相手のアクター文書を読んだ時刻としても記録する
 */
data class IncomingFollow(
    val username: String,
    val follower: NewRemoteActor,
    val followActivityUri: String,
    val receivedAt: Instant,
)

/**
 * @param sharedInbox 同じインスタンス宛をまとめて送れる inbox。持たない実装もある
 */
data class NewRemoteActor(
    val actorUri: String,
    val inbox: String,
    val sharedInbox: String?,
    val publicKeyPem: String,
)
