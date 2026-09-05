package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureResult
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureVerifier
import net.matsudamper.mastodon.rss.httpsignature.SignedRequest
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory

/**
 * inbox が受け取ったアクティビティを検証して、種類ごとの処理に渡す。
 *
 * Ktor のルーティングからは切り離してある。ここでやるのは「受け取ったものを
 * 信用してよいか」の判断と振り分けで、HTTP の status に直すのは [inboxRoutes] の仕事。
 * 分けておくと、サーバーを立てずに判断だけを確かめられる。
 *
 * 落とした理由は結果に載せずログにだけ出す。相手にどこで落ちたかを教えると、
 * 通る形を総当たりで探す助けになるため。ルート側が理由を知らない形にしておけば、
 * うっかり応答に混ぜることもない。
 */
class InboxService(
    private val verifier: HttpSignatureVerifier,
    handlers: List<InboxActivityHandler>,
) {
    private val logger = LoggerFactory.getLogger(InboxService::class.java)

    private val handlersByType: Map<String, InboxActivityHandler> =
        handlers.associateBy { it.type }.also {
            // 同じ type のハンドラを 2 つ登録しても、黙って片方が捨てられるだけで
            // 気付けない。処理されないアクティビティは相手側からは無反応に見える
            require(it.size == handlers.size) {
                "同じ type のハンドラが複数ある: ${handlers.map { handler -> handler.type }}"
            }
        }

    /**
     * @param recipient 宛先になるこちらのアクター。引き当ては呼び出し側で済ませておく
     * @param request 署名の検証にかけるリクエスト。ボディは受け取り終えたものを渡す
     */
    suspend fun receive(
        recipient: ActorUrls,
        request: SignedRequest,
    ): InboxResult {
        val verifiedSignerActorId =
            when (val verification = verifier.verify(request)) {
                is HttpSignatureResult.Rejected -> {
                    // 消えたアクターからの Delete だけは、検証できないことを理由に
                    // 落とすと相手が送り直し続ける。削除の通知は本人が消えた後に届き、
                    // そのとき鍵はもう取りに行けないので、通しようがない
                    if (isSelfDelete(request.body)) {
                        logger.info("消えたアクターからの Delete として受け流す: ${recipient.acct} ${verification.reason}")
                        return InboxResult.Accepted
                    }

                    logger.warn("inbox の署名を拒否した: ${recipient.acct} ${verification.reason}")
                    return InboxResult.Unauthorized
                }

                is HttpSignatureResult.Verified -> {
                    verification.owner
                }
            }

        val rawActivityJson =
            runCatching { AppJson.parseToJsonElement(request.body.decodeToString()) as? JsonObject }
                .getOrNull()
        val activity =
            rawActivityJson?.let {
                runCatching { AppJson.decodeFromJsonElement(InboxActivity.serializer(), it) }.getOrNull()
            }

        if (rawActivityJson == null || activity == null) {
            logger.warn("inbox のボディを読めなかった: ${recipient.acct} 署名者=$verifiedSignerActorId")
            return InboxResult.BadRequest
        }

        // 署名した鍵の持ち主と、アクティビティの実行者が別なら、なりすまし。
        // 署名だけ通る形は作れるので、ここを見ないと他人の Undo を送り込める
        val actorId = activity.actorId
        if (actorId != null && actorId != verifiedSignerActorId) {
            logger.warn("inbox の actor が署名者と違う: actor=$actorId 署名者=$verifiedSignerActorId")
            return InboxResult.Unauthorized
        }

        logger.info(
            "inbox で受信: 宛先=${recipient.acct} type=${activity.type} " +
                "id=${activity.id} actor=$verifiedSignerActorId",
        )

        // 引き当てられない type は何もしない。未対応のアクティビティに 5xx を返すと
        // 相手は同じものを送り直し続けることになる
        val handled = runCatching {
            handlersByType[activity.type]?.handle(
                recipient = recipient,
                verifiedSignerActorId = verifiedSignerActorId,
                activity = activity,
                rawActivityJson = rawActivityJson,
            )
        }

        // 処理に失敗しても 202 で返す。5xx にすると相手は同じものを送り直し続けるので、
        // こちらが書き込めない間ずっと同じ失敗を繰り返すことになる。
        // 何が起きたかはここに残っているものが唯一の手がかりになる
        handled.onFailure { failure ->
            logger.warn(
                "inbox の処理に失敗した: ${recipient.acct} type=${activity.type} actor=$verifiedSignerActorId",
                failure,
            )
        }

        return InboxResult.Accepted
    }

    /**
     * 署名を検証できなかったボディが、送り主自身の削除の通知かどうか。
     *
     * 検証を通っていないので中身は信用できない。ここで見るのは
     * 「202 を返して黙らせてよいか」だけで、これを見てフォロワーを消すことはしない。
     * 消すのは [DeleteActorHandler] で、そちらには検証を通ったものしか届かない。
     */
    private fun isSelfDelete(body: ByteArray): Boolean {
        val activity = runCatching {
            AppJson.decodeFromString(InboxActivity.serializer(), body.decodeToString())
        }.getOrNull() ?: return false

        if (activity.type != DELETE_TYPE) return false

        val actorId = activity.actorId ?: return false
        return activity.target?.id == actorId
    }

    companion object {
        private const val DELETE_TYPE = "Delete"

        /**
         * 既定の組み合わせで作る。
         *
         * 署名の検証と Follow への `Accept` は ActivityPub として要るもので、
         * 使う側が選ぶものではない。組み立てを呼び出し側に書かせると
         * 「どのハンドラが要るか」がモジュールの外に漏れるので、ここに置く。
         * 種類ごとの処理はハンドラを足す形になっていて、Phase 3 の `Undo` と
         * `Delete` はこの一覧に並ぶ。
         *
         * @param remoteActors 相手のアクターの引き先。署名検証に使う公開鍵と、
         *   `Accept` の宛先になる inbox をここから取る
         * @param delivery こちらから相手の inbox に POST する口
         */
        fun default(
            remoteActors: RemoteActors,
            delivery: ActivityDelivery,
            followers: FollowerStore,
        ): InboxService =
            InboxService(
                verifier = HttpSignatureVerifier(remoteActors),
                handlers = listOf(
                    FollowHandler(remoteActors, delivery, followers),
                    UndoFollowHandler(followers),
                    DeleteActorHandler(followers),
                ),
            )
    }
}

/**
 * [InboxService.receive] の結果。
 *
 * 中身の処理に失敗しても [Accepted] を返す。相手のサーバーは 5xx を見ると
 * 再送を繰り返すので、こちらの都合で溜め込ませない。
 */
sealed interface InboxResult {
    /** 署名が無い、通らない、`actor` と署名者が違う */
    data object Unauthorized : InboxResult

    /** 署名は通ったが、ボディが JSON として読めない */
    data object BadRequest : InboxResult

    /** 署名が通った。中身の処理の成否は含めない */
    data object Accepted : InboxResult
}
