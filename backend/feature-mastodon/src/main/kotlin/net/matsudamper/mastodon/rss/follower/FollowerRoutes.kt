package net.matsudamper.mastodon.rss.follower

import kotlinx.serialization.builtins.serializer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.mastodon.rss.collection.COLLECTION_PAGE_SIZE
import net.matsudamper.mastodon.rss.collection.OrderedCollection
import net.matsudamper.mastodon.rss.collection.OrderedCollectionPage
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * Actor の `followers` が指している先。
 *
 * 集合とページの 2 段構えは ActivityPub の決まりで、Mastodon は総数だけを
 * 見ることも、ページを辿って中身を読むこともある。
 */
fun Route.followerRoutes(
    directory: ActorDirectory,
    followers: FollowerStore,
) {
    get("/users/{username}/followers") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)
        if (urls == null) {
            call.respondText("アカウントが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept))
        val total = followers.count(urls.username)
        val cursor = call.request.queryParameters[COLLECTION_CURSOR_PARAM]

        // パラメータが無ければ集合そのもの。空文字でも付いていれば先頭のページ
        if (cursor == null) {
            call.respondJson(
                serializer = OrderedCollection.serializer(),
                value = OrderedCollection(
                    id = urls.followers,
                    totalItems = total,
                    first = pageUrl(urls, null),
                ),
                contentType = contentType,
            )
            return@get
        }

        val items = followers.list(
            username = urls.username,
            after = cursor.ifEmpty { null },
            limit = COLLECTION_PAGE_SIZE,
        )

        call.respondJson(
            serializer = OrderedCollectionPage.serializer(String.serializer()),
            value = OrderedCollectionPage(
                id = pageUrl(urls, cursor.ifEmpty { null }),
                totalItems = total,
                partOf = urls.followers,
                orderedItems = items,
                next = if (items.size < COLLECTION_PAGE_SIZE) null else pageUrl(urls, items.last()),
            ),
            contentType = contentType,
        )
    }
}

/**
 * @param after 直前のページの最後のアクター URL。null なら先頭のページ
 */
private fun pageUrl(
    urls: ActorUrls,
    after: String?,
): String = "${urls.followers}?$COLLECTION_CURSOR_PARAM=${after?.encodeURLParameter().orEmpty()}"
