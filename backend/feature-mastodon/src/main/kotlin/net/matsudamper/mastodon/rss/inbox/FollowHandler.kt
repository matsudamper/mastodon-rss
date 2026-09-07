package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activity.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
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
 * 送信は inbox の応答を返す前に行う。配信キューが無いので、ここで送らないと
 * 送る機会が無い。相手のサーバーが応答しない場合に備えて、
 * HTTP クライアント側にタイムアウトを入れてある。
 *
 * 記録してから `Accept` を返す。逆にすると、記録に失敗したときに相手だけが
 * フォローできたつもりになり、こちらには送り先が残らない。記録できなければ
 * `Accept` も返さないので、相手からは保留のまま見える。
 *
 * 初めて成立したときだけ、フォローより前の投稿を新しいフォロワーにだけ配る。詳しくは
 * [net.matsudamper.mastodon.rss.note.FollowBackfillPublisher] にある。
 * 送るのは inbox の応答を返した後で、こちらの応答を待たせない。
 */
class FollowHandler(
    private val remoteActors: RemoteActors,
    private val delivery: ActivityDelivery,
    private val followers: FollowerStore,
    private val backfill: FollowBackfillPublisher,
    private val backfillScope: CoroutineScope,
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
                "Follow を記録できなかったので Accept を返さない: ${recipient.acct} ← $verifiedSignerActorId",
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

        val serializedAcceptBody = AppJson.encodeToString(OutgoingActivity.serializer(), accept).toByteArray()

        when (val result = delivery.deliver(inbox = follower.inbox, sender = recipient, body = serializedAcceptBody)) {
            is DeliveryResult.Delivered -> {
                val accepted = markAccepted(
                    recipient = recipient,
                    verifiedSignerActorId = verifiedSignerActorId,
                    acceptedAt = Instant.now(),
                )

                logger.info("Follow に Accept を返した: ${recipient.acct} ← $verifiedSignerActorId")

                // 送り直しでは配らない。相手のタイムラインには既に並んでいて、
                // 同じものをもう一度署名付きで送りつけるだけになる
                if (accepted == FollowAcceptResult.FirstAccept) {
                    // 通常の配信はフォロワーとして数えられてから始まるので、境目は
                    // 記録が済んだ後に取る。先に取ると、記録を待っている間の投稿が
                    // どちらからも漏れる。境目が重なって二重に送っても、
                    // 同じ id なので相手側で落ちる
                    val backfillUntil = Instant.now()

                    // inbox の応答を待たせない。最大 20 件を順に送るので、
                    // ここで待つと相手のタイムアウトと Follow の再送を招く
                    backfillScope.launch {
                        runCatching {
                            backfill.deliverRecentNotes(
                                sender = recipient,
                                inbox = follower.inbox,
                                publishedBefore = backfillUntil,
                            )
                        }.onFailure { failure ->
                            logger.warn(
                                "過去の投稿を配れなかった: ${recipient.acct} → $verifiedSignerActorId",
                                failure,
                            )
                        }
                    }
                }
            }

            is DeliveryResult.Failed -> {
                // 相手から見るとフォローが保留のまま残る。再送はしないので、
                // 何が起きたのかはここに残っているものが唯一の手がかりになる。
                // 記録は残るが `Accept` 前の状態なのでフォロワーには数えない
                logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $verifiedSignerActorId ${result.reason}")
            }
        }
    }

    /**
     * `Accept` を返せたことを記録する。
     *
     * ここまで来た時点で相手はフォローできたつもりなので、`Follow` は送り直されない。
     * 記録に失敗したまま終えると、相手の画面ではフォロー中なのに投稿が 1 つも
     * 届かない状態が残り続ける。書き込みが一時的に通らないだけのこともあるので、
     * 何度か試してから諦める。
     *
     * 諦めた場合に直す手立ては無いので、運用者が気付けるようにログに残す。
     * 取りこぼしを溜めて後から流す仕組みは、配信キューを入れるときに一緒に考える。
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
                        "Accept は返せたがフォロワーとして記録できなかった。" +
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
                    "Accept は返せたがフォロワーとして記録できなかった。" +
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
