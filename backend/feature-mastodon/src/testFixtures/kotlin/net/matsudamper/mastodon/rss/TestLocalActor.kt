package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.StoredActorNames

/**
 * テストで配信側に立つ、こちらのアクター。
 *
 * ルーティングのテストはどれもアクターを 1 つ必要とするので、綴りをここに集める。
 * 相手側は [TestRemoteActor]。
 */
object TestLocalActor {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    /**
     * 設定ではなく保存されている側のアカウント。引き当ての経路が固定アクターと違う
     */
    const val STORED_USERNAME: String = "feed1"

    val urls: ActorUrls = ActorUrls(domain = DOMAIN, username = USERNAME)

    val directory: ActorDirectory = ActorDirectory(
        fixed = urls,
        stored = FakeStoredActorNames(storedUserNames = listOf(STORED_USERNAME)),
    )
}
