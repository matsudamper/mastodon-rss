package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls

/**
 * テストで配信側に立つ、こちらのアクター。
 *
 * ルーティングのテストはどれもアクターを 1 つ必要とするので、綴りをここに集める。
 * 相手側は [TestRemoteActor]。
 */
object TestLocalActor {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    val urls: ActorUrls = ActorUrls(domain = DOMAIN, username = USERNAME)

    val directory: ActorDirectory = ActorDirectory(urls)
}
