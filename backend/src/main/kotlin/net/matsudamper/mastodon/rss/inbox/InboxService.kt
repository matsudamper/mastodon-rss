package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
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
        val owner =
            when (val verification = verifier.verify(request)) {
                is HttpSignatureResult.Rejected -> {
                    logger.warn("inbox の署名を拒否した: ${recipient.acct} ${verification.reason}")
                    return InboxResult.Unauthorized
                }

                is HttpSignatureResult.Verified -> {
                    verification.owner
                }
            }

        // `Accept` には受け取ったアクティビティを丸ごと入れて返すので、
        // 型に落とした後も元の JSON を捨てずに持っておく
        val json =
            runCatching { AppJson.parseToJsonElement(request.body.decodeToString()) as? JsonObject }
                .getOrNull()
        val activity =
            json?.let {
                runCatching { AppJson.decodeFromJsonElement(InboxActivity.serializer(), it) }.getOrNull()
            }

        if (json == null || activity == null) {
            logger.warn("inbox のボディを読めなかった: ${recipient.acct} 署名者=$owner")
            return InboxResult.BadRequest
        }

        // 署名した鍵の持ち主と、アクティビティの実行者が別なら、なりすまし。
        // 署名だけ通る形は作れるので、ここを見ないと他人の Undo を送り込める
        val actorId = activity.actorId
        if (actorId != null && actorId != owner) {
            logger.warn("inbox の actor が署名者と違う: actor=$actorId 署名者=$owner")
            return InboxResult.Unauthorized
        }

        logger.info(
            "inbox で受信: 宛先=${recipient.acct} type=${activity.type} " +
                "id=${activity.id} actor=$owner",
        )

        // 引き当てられない type は何もしない。未対応のアクティビティに 5xx を返すと
        // 相手は同じものを送り直し続けることになる
        handlersByType[activity.type]?.handle(
            recipient = recipient,
            signer = owner,
            activity = activity,
            raw = json,
        )

        return InboxResult.Accepted
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
