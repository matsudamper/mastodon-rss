package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * フォロワーの読み書き。
 *
 * `remote_actors` と `followers` の 2 テーブルにまたがるが、口は 1 つにする。
 * 呼び出し側が欲しいのは「この相手がこのアカウントをフォローしている」という事実だけで、
 * 行を採番した id は外に出しても使い道が無い。分けると、フォロー 1 件を記録するのに
 * 呼び出し側が順番と id の受け渡しを組み立てることになり、トランザクションの
 * 境界もそちらに漏れる。
 *
 * こちらのアカウントは名前で指す。引き当ての正は `ActorDirectory` で、
 * `ACTOR_USERNAME` で決まる組み込みアカウントは `accounts` に行を持たない。
 *
 * 状態（`pending` / `accepted`）は外に出さない。`Follow` を受けただけの相手は
 * まだフォロワーではないので、[list] と [count] と [deliveryTargets] は
 * `Accept` を返せたものだけを対象にする。
 */
interface FollowerRepository {
    /**
     * `Follow` を受けたことを記録する。`Accept` を返す前の状態で入る。
     *
     * 同じ相手からの `Follow` が既に記録されていれば何もしない。`Accept` を返し損ねると
     * 相手は同じ `Follow` を送り直してくるので、二重に受けても行が増えない形にする。
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
     * @param followActivityUri 消す対象を元の `Follow` の id で絞る。`Undo` の `object` に
     *   アクティビティが埋め込まれておらず id だけが来た場合、それが本当に `Follow` の id
     *   だったのかはこちらの記録と突き合わせるしか確かめようが無い。埋め込まれていて
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
     * 相手が消えた以上、フォローの相手として残しておく意味が無い。
     *
     * @return 消えたフォローの数
     */
    fun removeRemoteActor(actorUri: String): Int

    /**
     * フォロワーのアクター URL を返す。`followers` コレクションに使う。
     *
     * 並びは URL 順。位置を件数で数えず、直前のページの最後の 1 件で指す。
     * 件数で数えると、読んでいる間にフォローや解除が入るたびに位置がずれて、
     * 同じ相手が 2 回出たり抜けたりする。
     *
     * 記録した順ではなく URL 順にしているのは、位置を指す鍵が返す値そのもので
     * 済むため。記録した順にすると、採番した id を外に出すことになる。
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
     * 名前でまとめて数える。
     *
     * 返すマップのキーは渡された名前。フォロワーがいない名前は 0 が入る
     */
    fun counts(usernames: Set<String>): Map<String, Long>

    /**
     * 投稿を配る先の inbox を返す。
     *
     * `sharedInbox` を持つ相手はそちらにまとめる。同じインスタンスに 10 人いても
     * 1 通で済む。同じ宛先は 1 つにまとめて返すので、呼び出し側は返ってきたものに
     * そのまま送ればよい。
     */
    fun deliveryTargets(username: String): List<String>

    /**
     * アカウントを問わず、フォローの記録が 1 件でもあるか。
     *
     * 鍵を失ったまま新しい鍵を生成して起動してしまう事故を止めるために使う。
     * `Accept` を返せていないものも数える。相手に届いていないだけで、
     * こちらのアクターが外から見えていたことに変わりはないため。
     */
    fun hasAny(): Boolean
}

/**
 * 受け取った `Follow` を記録するのに要るもの。
 *
 * 相手のアクターの中身を一緒に受け取るのは、`Follow` を処理する時点で
 * アクター文書を取りに行っているため。フォロワーとして記録するなら、
 * そのとき読めた inbox と公開鍵もその場で残しておくのが取りに行く回数が少なくて済む。
 *
 * @param username フォローされたこちらのアカウントの名前
 * @param followActivityUri 受け取った `Follow` の id
 * @param receivedAt 受け取った時刻。相手のアクター文書を読んだ時刻としても記録する
 */
data class IncomingFollow(
    val username: String,
    val follower: NewRemoteActor,
    val followActivityUri: String,
    val receivedAt: Instant,
)

/**
 * 相手のアクターのうち保存する部分。
 *
 * アクター文書を読んだ結果（`:backend:feature-mastodon` 側の型）とは別に定義する。
 * 同じ型を使い回すと、スキーマを変えるたびに取得側を触ることになる。
 *
 * @param actorUri 相手のアクター文書の URL。相手を指す識別子
 * @param sharedInbox 同じインスタンス宛をまとめて送れる inbox。持たない実装もある
 */
data class NewRemoteActor(
    val actorUri: String,
    val inbox: String,
    val sharedInbox: String?,
    val publicKeyPem: String,
)
