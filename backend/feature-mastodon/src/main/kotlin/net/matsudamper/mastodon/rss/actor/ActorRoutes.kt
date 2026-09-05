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
    profiles: StoredActorProfiles,
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
            value = actorDocument(urls, actorKey, feedLinks.find(urls.username), profiles.find(urls.username)),
            // Accept を見ずに application/json で返すとアクターとして認識されない
            contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept)),
        )
    }
}

/**
 * Actor JSON を組み立てる。
 *
 * 表示名と説明文は管理画面から設定できる。設定していなければ名前から決まる。
 */
internal fun actorDocument(
    urls: ActorUrls,
    actorKey: ActorKey,
    feedLinks: FeedLinks,
    profile: ActorProfile,
): Actor =
    Actor(
        id = urls.actorId,
        preferredUsername = urls.username,
        name = profile.displayName ?: urls.username,
        summary = profile.summary?.let { summaryHtml(it) } ?: SUMMARY,
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
        val siteUrl = feedLinks.siteUrl
        if (siteUrl != null) add(linkAttachment(name = SITE_ATTACHMENT_NAME, url = siteUrl))

        val feedUrl = feedLinks.feedUrl
        if (feedUrl != null) add(linkAttachment(name = FEED_ATTACHMENT_NAME, url = feedUrl))
    }

private fun linkAttachment(
    name: String,
    url: String,
): ActorAttachment {
    val escaped = escapeHtml(url)
    // rel は Mastodon 側でも付け直されるが、そのまま表示する実装もあるので入れておく
    return ActorAttachment(
        name = name,
        htmlContent = """<a href="$escaped" rel="nofollow noopener" target="_blank">$escaped</a>""",
    )
}

/**
 * 説明文のプレーンテキストを `summary` に入れる HTML にする。
 *
 * 空行で段落に分け、行の切れ目は `<br>` にする。Mastodon が許可するのは
 * この程度のタグで、それ以外は相手側で落とされる。
 */
private fun summaryHtml(text: String): String = text
    .replace("\r\n", "\n")
    .split(Regex("\n{2,}"))
    .filter { it.isNotBlank() }
    .joinToString("") { paragraph ->
        val escaped = paragraph.trim().split("\n").joinToString("<br>") { escapeHtml(it) }
        "<p>$escaped</p>"
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
