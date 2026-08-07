package dev.matsudamper.mastodonrss.actor

import dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes
import dev.matsudamper.mastodonrss.activitypub.Actor
import dev.matsudamper.mastodonrss.activitypub.ActorPublicKey
import dev.matsudamper.mastodonrss.json.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Actor エンドポイント。WebFinger から辿り着く 2 ホップ目。
 *
 * Phase 6 で複数アクターにするまではユーザー名が 1 つしか無いので、
 * パスの変数と [urls] のユーザー名を突き合わせて一致しなければ 404 を返す。
 */
fun Route.actorRoutes(urls: ActorUrls, actorKey: ActorKey) {
    get("/users/{username}") {
        val requested = call.parameters["username"]

        // WebFinger 側も大文字小文字を区別しないので合わせる。
        // 返す id は要求された綴りではなく常に正規の形にする
        if (!requested.equals(urls.username, ignoreCase = true)) {
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondJson(
            serializer = Actor.serializer(),
            value = actorDocument(urls, actorKey),
            // Accept を見ずに application/json で返すとアクターとして認識されない
            contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept)),
        )
    }
}

/**
 * 固定アクターの Actor JSON を組み立てる。
 *
 * 表示名と説明文は Phase 6 でアクターごとに DB から引くようになる。
 * それまでは 1 つしか無いので定数で持つ。
 */
internal fun actorDocument(urls: ActorUrls, actorKey: ActorKey): Actor =
    Actor(
        id = urls.actorId,
        preferredUsername = urls.username,
        name = urls.username,
        summary = SUMMARY,
        inbox = urls.inbox,
        outbox = urls.outbox,
        followers = urls.followers,
        following = urls.following,
        url = urls.actorId,
        publicKey =
            ActorPublicKey(
                id = urls.publicKeyId,
                owner = urls.actorId,
                publicKeyPem = actorKey.publicKeyPem,
            ),
    )

private const val SUMMARY = "RSS/Atom フィードを ActivityPub で配信するアカウント"
