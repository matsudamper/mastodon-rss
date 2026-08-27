package net.matsudamper.mastodon.rss.actor

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.activitypub.Actor
import net.matsudamper.mastodon.rss.activitypub.ActorPublicKey
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * Actor エンドポイント。WebFinger から辿り着く 2 ホップ目。
 *
 * 引き当ては [ActorDirectory] に任せる。知らない名前は 404。
 */
fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorKey,
) {
    get("/users/{username}") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)

        if (urls == null) {
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
 * Actor JSON を組み立てる。
 *
 * 表示名と説明文は Phase 6 でアクターごとに DB から引くようになる。
 * それまでは名前から決まる。
 */
internal fun actorDocument(
    urls: ActorUrls,
    actorKey: ActorKey,
): Actor =
    Actor(
        id = urls.actorId,
        preferredUsername = urls.username,
        name = urls.username,
        summary = SUMMARY,
        inbox = urls.inbox,
        outbox = urls.outbox,
        featured = urls.featured,
        followers = urls.followers,
        following = urls.following,
        url = urls.actorId,
        showFeatured = false,
        publicKey =
        ActorPublicKey(
            id = urls.publicKeyId,
            owner = urls.actorId,
            publicKeyPem = actorKey.publicKeyPem,
        ),
    )

private const val SUMMARY = "RSS/Atom フィードを ActivityPub で配信するアカウント"
