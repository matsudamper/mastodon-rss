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
import net.matsudamper.mastodon.rss.activitypub.ActorAttachment
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
    feedLinks: StoredFeedLinks,
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
            value = actorDocument(urls, actorKey, feedLinks.find(urls.username)),
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
    feedLinks: FeedLinks,
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
        attachment = feedAttachments(feedLinks),
        showFeatured = false,
        publicKey =
        ActorPublicKey(
            id = urls.publicKeyId,
            owner = urls.actorId,
            publicKeyPem = actorKey.publicKeyPem,
        ),
    )

/**
 * フィードの URL をプロフィールのリンク集にする。
 *
 * フィードを持たないアカウントは空になる。空の項目を出すと、Mastodon の
 * プロフィールに見出しだけの行が並ぶ。
 */
private fun feedAttachments(feedLinks: FeedLinks): List<ActorAttachment> =
    buildList {
        feedLinks.siteUrl?.let { add(linkAttachment(name = SITE_ATTACHMENT_NAME, url = it)) }
        feedLinks.feedUrl?.let { add(linkAttachment(name = FEED_ATTACHMENT_NAME, url = it)) }
    }

private fun linkAttachment(
    name: String,
    url: String,
): ActorAttachment {
    val escaped = escapeHtml(url)
    // rel は Mastodon 側でも付け直されるが、そのまま表示する実装もあるので入れておく
    return ActorAttachment(
        name = name,
        value = """<a href="$escaped" rel="nofollow noopener" target="_blank">$escaped</a>""",
    )
}

private fun escapeHtml(raw: String): String =
    raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private const val SUMMARY = "RSS/Atom フィードを ActivityPub で配信するアカウント"
private const val SITE_ATTACHMENT_NAME = "サイト"
private const val FEED_ATTACHMENT_NAME = "フィード"
