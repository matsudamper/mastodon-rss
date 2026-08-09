package net.matsudamper.mastodon.rss.inbox

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureResult
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureVerifier
import net.matsudamper.mastodon.rss.httpsignature.SignedRequest
import net.matsudamper.mastodon.rss.json.AppJson

/**
 * inbox エンドポイント。相手のサーバーからアクティビティが POST されてくる。
 *
 * いま中身に応じた処理をするのは `Follow` だけで、[FollowHandler] が `Accept` を
 * 返す。`Undo` や `Delete` はログに出すだけ。フォロワーを永続化して解除まで
 * 扱うのは TODO.md の Phase 3。
 *
 * 返す status は次のとおり。
 *
 * - 202 Accepted: 署名が通った。中身の処理の成否は含めない
 * - 400 Bad Request: ボディが JSON として読めない
 * - 401 Unauthorized: 署名が無い、通らない、`actor` と署名者が違う
 * - 404 Not Found: そのアクターがいない
 * - 413 Content Too Large: ボディが大きすぎる
 *
 * 中身の処理に失敗しても 202 を返す方針にする。相手のサーバーは 5xx を見ると
 * 再送を繰り返すので、こちらの都合で溜め込ませない。
 */
fun Route.inboxRoutes(
    directory: ActorDirectory,
    verifier: HttpSignatureVerifier,
    followHandler: FollowHandler,
) {
    post("/users/{username}/inbox") {
        val log = application.log

        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)
        if (urls == null) {
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@post
        }

        // 読む前に長さで弾く。読んでから確かめても、その時点で受け取り終わっている
        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        // Digest はバイト列に対して計算されているので、文字列にせずそのまま受ける
        val body = call.receive<ByteArray>()
        if (body.size > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        val signedRequest =
            SignedRequest(
                method = call.request.httpMethod.value,
                // 送信側が署名したのはリクエストラインの綴りそのもの。
                // パスを組み直すと末尾やクエリの差で合わなくなる
                requestTarget = call.request.uri,
                headers = call.request.headers,
                body = body,
            )

        when (val verification = verifier.verify(signedRequest)) {
            is HttpSignatureResult.Rejected -> {
                // 理由は相手に返さない。どこで落ちたかを教えると通る形を探す助けになる
                log.warn("inbox の署名を拒否した: ${urls.acct} ${verification.reason}")
                call.respondText("署名を検証できなかった", status = HttpStatusCode.Unauthorized)
            }

            is HttpSignatureResult.Verified -> {
                // `Accept` には受け取ったアクティビティを丸ごと入れて返すので、
                // 型に落とした後も元の JSON を捨てずに持っておく
                val json =
                    runCatching { AppJson.parseToJsonElement(body.decodeToString()) as? JsonObject }
                        .getOrNull()
                val activity =
                    json?.let {
                        runCatching { AppJson.decodeFromJsonElement(InboxActivity.serializer(), it) }.getOrNull()
                    }

                if (json == null || activity == null) {
                    log.warn("inbox のボディを読めなかった: ${urls.acct} 署名者=${verification.owner}")
                    call.respondText("ボディを読めなかった", status = HttpStatusCode.BadRequest)
                    return@post
                }

                // 署名した鍵の持ち主と、アクティビティの実行者が別なら、なりすまし。
                // 署名だけ通る形は作れるので、ここを見ないと他人の Undo を送り込める
                val actorId = activity.actorId
                if (actorId != null && actorId != verification.owner) {
                    log.warn("inbox の actor が署名者と違う: actor=$actorId 署名者=${verification.owner}")
                    call.respondText("署名を検証できなかった", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                log.info(
                    "inbox で受信: 宛先=${urls.acct} type=${activity.type} " +
                        "id=${activity.id} actor=${verification.owner}",
                )

                if (activity.type == TYPE_FOLLOW) {
                    acceptFollow(urls, verification.owner, activity, json, followHandler)
                }

                // 中身の処理が失敗していても 202 で返す。ここで 5xx にすると
                // 相手は同じ Follow を送り直し続けることになる
                call.respondText("", status = HttpStatusCode.Accepted)
            }
        }
    }
}

/**
 * `Follow` に `Accept` を返す。
 *
 * 送信は inbox の応答を返す前に行う。配信キューが無いので、ここで送らないと
 * 送る機会が無い。相手のサーバーが応答しない場合に備えて、
 * HTTP クライアント側にタイムアウトを入れてある。
 */
private suspend fun RoutingContext.acceptFollow(
    recipient: ActorUrls,
    follower: String,
    activity: InboxActivity,
    json: JsonObject,
    followHandler: FollowHandler,
) {
    val log = call.application.log

    // 宛先の異なる Follow をこちらの inbox に投げ込むことはできる。
    // 中身を見ずに Accept を返すと、フォローしていないアクターの
    // フォローが成立したように相手に見える
    val target = activity.target?.id
    if (target != recipient.actorId) {
        log.warn("Follow の宛先が違うので Accept を返さない: object=$target 宛先=${recipient.actorId}")
        return
    }

    when (val result = followHandler.accept(recipient, follower, json)) {
        is DeliveryResult.Delivered -> {
            log.info("Follow に Accept を返した: ${recipient.acct} ← $follower")
        }

        is DeliveryResult.Failed -> {
            // 相手から見るとフォローが保留のまま残る。再送はしないので、
            // 何が起きたのかはここに残っているものが唯一の手がかりになる
            log.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $follower ${result.reason}")
        }
    }
}

/** 相手がフォローしようとしているときのアクティビティの `type` */
private const val TYPE_FOLLOW = "Follow"

/**
 * 受け取るボディの上限。
 *
 * アクティビティは大きくても数十 KB にしかならない。上限が無いと、
 * 署名を検証する前の段階でメモリを食い潰させられる。
 */
private const val MAX_BODY_BYTES = 1024 * 1024
