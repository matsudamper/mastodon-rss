package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activity.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.follower.FollowAcceptResult
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.FollowBackfillPublisher
import org.slf4j.LoggerFactory

/**
 * `Follow` を受けたときの処理。
 *
 * ActivityPub のフォローは、相手が `Follow` を送ってきた時点では成立していない。
 * こちらが `Accept` を相手の inbox に返して初めて確定する。返さないと
 * Mastodon の画面ではフォローボタンが保留のまま戻らない。
 *
 * `Accept` は配信キューに投函する。ここで送らずに済むので、相手のサーバーが遅くても
 * inbox の応答は待たされない。送れなかった分は配信ワーカーが送り直す。
 *
 * 記録してから `Accept` を投函する。逆にすると、記録に失敗したときに相手だけが
 * フォローできたつもりになり、こちらには送り先が残らない。記録できなければ
 * `Accept` も投函しないので、相手からは保留のまま見える。
 *
 * 初めて成立したときだけ、フォローより前の投稿を新しいフォロワーにだけ配る。詳しくは
 * [net.matsudamper.mastodon.rss.note.FollowBackfillPublisher] にある。
 * 投函は `Accept` の後に行う。同じ宛先へは投函した順に送られる。
 */
class FollowHandler(
    private val remoteActors: RemoteActors,
    private val queue: ActivityQueue,
    private val followers: FollowerStore,
    private val backfill: FollowBackfillPublisher,
) : InboxActivityHandler {
    override val type: String = "Follow"

    private val logger = LoggerFactory.getLogger(FollowHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
        rawActivityJson: JsonObject,
    ) {
        // 宛先の異なる Follow をこちらの inbox に投げ込むことはできる。
        // 中身を見ずに Accept を返すと、フォローしていないアクターの
        // フォローが成立したように相手に見える
        val followTargetActorId = activity.target?.id
        if (followTargetActorId != recipient.actorId) {
            logger.warn("Follow の宛先が違うので Accept を返さない: object=$followTargetActorId 宛先=${recipient.actorId}")
            return
        }

        // 相手が `id` を付けずに送ってくると、同じ `Follow` の送り直しと
        // 新しい `Follow` を区別できない。区別できないものを記録すると、
        // 送り直しのたびに行が増えるか、別のフォローを取り違えて消すことになる
        val followActivityUri = activity.id
        if (followActivityUri == null) {
            logger.warn("Follow に id が無いので受け付けない: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val follower = remoteActors.findActor(verifiedSignerActorId)
        if (follower == null) {
            logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $verifiedSignerActorId フォロワーのアクターを引けない")
            return
        }

        val recorded = runCatching {
            followers.record(
                username = recipient.username,
                follower = follower,
                followActivityUri = followActivityUri,
                receivedAt = Instant.now(),
            )
        }
        if (recorded.isFailure) {
            logger.warn(
                "Follow を記録できなかったので Accept を投函しない: ${recipient.acct} ← $verifiedSignerActorId",
                recorded.exceptionOrNull(),
            )
            return
        }

        val accept =
            OutgoingActivity(
                id = acceptId(recipient),
                type = OutgoingActivity.TYPE_ACCEPT,
                actor = recipient.actorId,
                target = LinkOrObject.Embedded(rawActivityJson),
            )

        val enqueued = runCatching {
            queue.enqueue(
                kind = QueuedActivityKind.ACCEPT_FOLLOW,
                sender = recipient,
                inboxes = listOf(follower.inbox),
                body = AppJson.encodeToString(OutgoingActivity.serializer(), accept),
                notePublicId = null,
            )
        }
        if (enqueued.isFailure) {
            // 相手から見るとフォローが保留のまま残る。記録は残るが `Accept` 前の状態なので
            // フォロワーには数えない。相手が `Follow` を送り直せばやり直しになる
            logger.warn(
                "Follow に Accept を投函できなかった: ${recipient.acct} ← $verifiedSignerActorId",
                enqueued.exceptionOrNull(),
            )
            return
        }

        // 投函できた時点でフォロワーとして数える。送れたかどうかは配信ワーカーが決めるので、
        // ここで待つと inbox の応答が相手のタイムアウトに掛かる
        val accepted = markAccepted(
            recipient = recipient,
            verifiedSignerActorId = verifiedSignerActorId,
            acceptedAt = Instant.now(),
        )

        logger.info("Follow に Accept を投函した: ${recipient.acct} ← $verifiedSignerActorId")

        // 送り直しでは配らない。相手のタイムラインには既に並んでいて、
        // 同じものをもう一度署名付きで送りつけるだけになる
        if (accepted != FollowAcceptResult.FirstAccept) return

        // 通常の配信はフォロワーとして数えられてから始まるので、境目は
        // 記録が済んだ後に取る。先に取ると、記録を待っている間の投稿が
        // どちらからも漏れる。境目が重なって二重に投函しても、
        // 同じ id なので相手側で落ちる
        val backfillUntil = Instant.now()

        runCatching {
            backfill.enqueueRecentNotes(
                sender = recipient,
                inbox = follower.inbox,
                publishedBefore = backfillUntil,
            )
        }.onFailure { failure ->
            // 配れなくてもフォローは成立している。次の新着からは普通に届く
            logger.warn("過去の投稿を投函できなかった: ${recipient.acct} → $verifiedSignerActorId", failure)
        }
    }

    /**
     * `Accept` を投函できたことを記録する。
     *
     * 投函した時点で相手には `Accept` が届く前提になり、`Follow` は送り直されない。
     * 記録に失敗したまま終えると、相手の画面ではフォロー中なのに投稿が 1 つも
     * 届かない状態が残り続ける。書き込みが一時的に通らないだけのこともあるので、
     * 何度か試してから諦める。
     *
     * 諦めた場合に直す手立ては無いので、運用者が気付けるようにログに残す。
     *
     * @return 記録できなかった場合は [FollowAcceptResult.NotFound]
     */
    private suspend fun markAccepted(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        acceptedAt: Instant,
    ): FollowAcceptResult {
        repeat(MARK_ACCEPTED_ATTEMPTS) { attempt ->
            val accepted = try {
                followers.markAccepted(
                    username = recipient.username,
                    followerActorUri = verifiedSignerActorId,
                    acceptedAt = acceptedAt,
                )
            } catch (e: Exception) {
                if (attempt == MARK_ACCEPTED_ATTEMPTS - 1) {
                    logger.error(
                        "Accept は投函できたがフォロワーとして記録できなかった。" +
                            "相手にはフォロー中と見えるが投稿は届かない: ${recipient.acct} ← $verifiedSignerActorId",
                        e,
                    )
                } else {
                    delay(MARK_ACCEPTED_RETRY_INTERVAL * (attempt + 1))
                }
                return@repeat
            }
            if (accepted != FollowAcceptResult.NotFound) return accepted

            if (attempt == MARK_ACCEPTED_ATTEMPTS - 1) {
                logger.error(
                    "Accept は投函できたがフォロワーとして記録できなかった。" +
                        "相手にはフォロー中と見えるが投稿は届かない: ${recipient.acct} ← $verifiedSignerActorId",
                )
            } else {
                delay(MARK_ACCEPTED_RETRY_INTERVAL * (attempt + 1))
            }
        }

        return FollowAcceptResult.NotFound
    }

    private companion object {
        /**
         * 記録を試す回数
         */
        const val MARK_ACCEPTED_ATTEMPTS: Int = 3

        /**
         * 試す間隔。書き込みが詰まっているだけなら、待てば通る
         */
        val MARK_ACCEPTED_RETRY_INTERVAL: Duration = 200.milliseconds

        /**
         * `Accept` 自身の id。
         *
         * アクター id にフラグメントを付けた形にする。相手はこの URL を取りに来ないが、
         * 独立したパスにすると「GET できる文書がある」と読める形になり、
         * 実際には返せないものを配ることになる。Mastodon も同じ作りで送ってくる。
         */
        fun acceptId(recipient: ActorUrls): String = "${recipient.actorId}#accepts/follows/${UUID.randomUUID()}"
    }
}
