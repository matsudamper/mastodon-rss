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
 * 引き当ては [ActorDirectory] に任せる。固定アクターと `test-` の使い捨て
 * アクターのどちらでもなければ 404。
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
        // 使い捨てアクターは Mastodon 側の表示でもそれと分かるようにしておく。
        // 検証で作ったものが残っていても、見れば消していいものだと判断できる
        summary = if (ActorUsername.isTest(urls.username)) TEST_SUMMARY else SUMMARY,
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
private const val TEST_SUMMARY = "動作確認用のアカウント。フォローしても何も流れない"
