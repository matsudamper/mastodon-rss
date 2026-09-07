package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorProfile
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.FeedLinks

/**
 * テストで配信側に立つ、こちらのアクター。
 *
 * ルーティングのテストはどれもアクターを 1 つ必要とするので、綴りをここに集める。
 * 相手側は [TestRemoteActor]。
 */
object TestLocalActor {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    /** 別のアカウント。引き当ての対象が複数ある経路を見るときに使う */
    const val STORED_USERNAME: String = "feed1"

    val urls: ActorUrls = ActorUrls(domain = DOMAIN, username = USERNAME)

    val directory: ActorDirectory = ActorDirectory(
        domain = DOMAIN,
        stored = FakeStoredActorNames(storedUserNames = listOf(USERNAME, STORED_USERNAME)),
    )

    /** [feedLinks] が名乗っているアイコンの取得元。URL に付く値の確認に使う */
    const val FEED_ICON_URL: String = "https://feed1.example.org/icon.png"

    /** [feedLinks] が名乗っているヘッダーの取得元 */
    const val FEED_HEADER_URL: String = "https://feed1.example.org/header.jpg"

    /** [STORED_USERNAME] だけがフィードを持つ。持たないアカウントとの差を見るため */
    val feedLinks: FakeStoredFeedLinks = FakeStoredFeedLinks(
        links = mapOf(
            STORED_USERNAME to FeedLinks(
                siteUrl = "https://feed1.example.org/",
                feedUrl = "https://feed1.example.org/rss.xml",
                iconUrl = FEED_ICON_URL,
                headerUrl = FEED_HEADER_URL,
            ),
        ),
    )

    /** [STORED_USERNAME] だけがプロフィールを設定している。未設定との差を見るため */
    val profiles: FakeStoredActorProfiles = FakeStoredActorProfiles(
        profiles = mapOf(
            STORED_USERNAME to ActorProfile(
                displayName = "フィード 1",
                summary = "1 つ目のフィード\n<b>タグ</b>",
            ),
        ),
    )
}
