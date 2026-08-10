package net.matsudamper.mastodon.rss.inbox

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.httpsignature.SignedRequest

/**
 * inbox エンドポイント。相手のサーバーからアクティビティが POST されてくる。
 *
 * ここでやるのは HTTP との変換だけ。宛先のアクターを引き当て、ボディを受け取り、
 * [InboxService] に渡して、返ってきた結果を status に直す。中身を信用してよいかの
 * 判断と、種類ごとの処理は [InboxService] にある。
 *
 * 返す status は次のとおり。
 *
 * - 202 Accepted: 署名が通った。中身の処理の成否は含めない
 * - 400 Bad Request: ボディが JSON として読めない
 * - 401 Unauthorized: 署名が無い、通らない、`actor` と署名者が違う
 * - 404 Not Found: そのアクターがいない
 * - 413 Content Too Large: ボディが大きすぎる
 *
 * 404 と 413 だけがここでの判断になる。どちらも署名を検証する前、
 * ボディを受け取り切る前に決まるので、[InboxService] には渡せない。
 */
fun Route.inboxRoutes(
    directory: ActorDirectory,
    service: InboxService,
) {
    post("/users/{username}/inbox") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)
        if (urls == null) {
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@post
        }

        // 読む前に長さで弾く。読んでから確かめても、その時点で受け取り終えている
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

        // 落ちた理由は相手に返さない。どこで落ちたかを教えると通る形を探す助けになるので、
        // 理由はサービス側がログに出し、ここには status しか渡ってこない
        when (service.receive(recipient = urls, request = signedRequest)) {
            is InboxResult.Unauthorized -> {
                call.respondText("署名を検証できなかった", status = HttpStatusCode.Unauthorized)
            }

            is InboxResult.BadRequest -> {
                call.respondText("ボディを読めなかった", status = HttpStatusCode.BadRequest)
            }

            is InboxResult.Accepted -> {
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
