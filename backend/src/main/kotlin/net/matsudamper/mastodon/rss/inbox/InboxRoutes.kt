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
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureResult
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureVerifier
import net.matsudamper.mastodon.rss.httpsignature.SignedRequest
import net.matsudamper.mastodon.rss.json.AppJson

/**
 * inbox エンドポイント。相手のサーバーからアクティビティが POST されてくる。
 *
 * 今は署名を検証して中身をログに出すところまで。`Follow` に `Accept` を返す
 * ところから先は TODO.md の Phase 2 に項目がある。フォローボタンを押しても
 * 保留のまま戻らないのはそのため。
 *
 * 返す status は次のとおり。
 *
 * - 202 Accepted: 署名が通った。中身の処理はこの後（今は何もしない）
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

        when (val result = verifier.verify(signedRequest)) {
            is HttpSignatureResult.Rejected -> {
                // 理由は相手に返さない。どこで落ちたかを教えると通る形を探す助けになる
                log.warn("inbox の署名を拒否した: ${urls.acct} ${result.reason}")
                call.respondText("署名を検証できなかった", status = HttpStatusCode.Unauthorized)
            }

            is HttpSignatureResult.Verified -> {
                val activity =
                    runCatching { AppJson.decodeFromString(InboxActivity.serializer(), body.decodeToString()) }
                        .getOrNull()

                if (activity == null) {
                    log.warn("inbox のボディを読めなかった: ${urls.acct} 署名者=${result.owner}")
                    call.respondText("ボディを読めなかった", status = HttpStatusCode.BadRequest)
                    return@post
                }

                // 署名した鍵の持ち主と、アクティビティの実行者が別なら、なりすまし。
                // 署名だけ通る形は作れるので、ここを見ないと他人の Undo を送り込める
                val actorId = activity.actorId
                if (actorId != null && actorId != result.owner) {
                    log.warn("inbox の actor が署名者と違う: actor=$actorId 署名者=${result.owner}")
                    call.respondText("署名を検証できなかった", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                // 種類ごとの処理はまだ無い。何が届いているのかを見るためのログ
                log.info(
                    "inbox で受信: 宛先=${urls.acct} type=${activity.type} " +
                        "id=${activity.id} actor=${result.owner}",
                )
                call.respondText("", status = HttpStatusCode.Accepted)
            }
        }
    }
}

/**
 * 受け取るボディの上限。
 *
 * アクティビティは大きくても数十 KB にしかならない。上限が無いと、
 * 署名を検証する前の段階でメモリを食い潰させられる。
 */
private const val MAX_BODY_BYTES = 1024 * 1024
