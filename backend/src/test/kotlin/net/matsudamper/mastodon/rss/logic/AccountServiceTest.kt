package net.matsudamper.mastodon.rss.logic

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestActorPublisher
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.image.RemoteImageType
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.DeliveryKind
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.RemoteActorProfile
import net.matsudamper.mastodon.rss.shared.AccountProfileLimits
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// 管理画面からアカウントを消す経路。
// 名前で持っているもの（投稿とフォロワー）まで消し切れているかがここの関心になる。
class AccountServiceTest {
    private val iconCacheDir: Path = Files.createTempDirectory("account-icon")

    private val iconStore = FeedIconStore(iconCacheDir)

    @Test
    fun `消すとフォロワーと投稿とフィードと記事が消える`() = runTest {
        val repositories = FakeRepositories()
        val account = repositories.withFullAccount()

        val result = serviceOf(repositories).delete(USERNAME)

        assertIs<AccountService.DeleteResult.Success>(result)
        assertNull(repositories.accounts.findById(account.id))
        assertEquals(0L, repositories.followers.count(USERNAME))
        assertEquals(0L, repositories.notes.count(USERNAME))
        assertNull(repositories.feeds.findByAccountId(account.id))
        assertEquals(emptyList(), repositories.feedItems.items())
    }

    @Test
    fun `消すと置いてあるアイコンのファイルも消える`() = runTest {
        val repositories = FakeRepositories()
        val account = repositories.withFullAccount()
        val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
        val path = iconStore.write(feedId = feed.id, bytes = byteArrayOf(1, 2, 3), imageType = RemoteImageType.PNG)
        repositories.feedIcons.save(
            feedId = feed.id,
            icon = FeedIcon(
                sourceUrl = "https://example.com/icon.png",
                contentType = "image/png",
                path = path,
                fetchedAt = CREATED_AT,
                expiresAt = CREATED_AT.plusSeconds(POLL_INTERVAL_SECONDS),
            ),
        )

        val result = serviceOf(repositories).delete(USERNAME)

        assertIs<AccountService.DeleteResult.Success>(result)
        assertNull(iconStore.read(path))
    }

    @Test
    fun `消すと名前にフィードの id を含まない古いファイルも消える`() = runTest {
        val repositories = FakeRepositories()
        val account = repositories.withFullAccount()
        val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
        // 置き場の名前を変える前に置いたもの。フィードの id だけを名前にしていた
        val path = feed.id.value.toString()
        Files.write(iconCacheDir.resolve(path), byteArrayOf(1, 2, 3))
        repositories.feedIcons.save(
            feedId = feed.id,
            icon = FeedIcon(
                sourceUrl = "https://example.com/icon.png",
                contentType = "image/png",
                path = path,
                fetchedAt = CREATED_AT,
                expiresAt = CREATED_AT.plusSeconds(POLL_INTERVAL_SECONDS),
            ),
        )

        val result = serviceOf(repositories).delete(USERNAME)

        assertIs<AccountService.DeleteResult.Success>(result)
        assertNull(iconStore.read(path))
    }

    @Test
    fun `消したことを伝える Delete を投函する`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()

        val result = serviceOf(repositories).delete(USERNAME)

        assertIs<AccountService.DeleteResult.Success>(result)

        val row = repositories.deliveryQueue.rows().single()
        assertEquals(DeliveryKind.DELETE_ACTOR, row.kind)
        val body = assertNotNull(row.body)
        assertContains(body, "\"type\":\"Delete\"")
        // object がアクター自身でないと、相手はアカウントではなく投稿の削除として扱う
        assertContains(body, "\"object\":\"https://${TestLocalActor.DOMAIN}/users/$USERNAME\"")
    }

    @Test
    fun `消した名前は Delete を送り切るまで作り直せない`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()

        serviceOf(repositories).delete(USERNAME)

        // 作り直せると、送り残した Delete が新しいアカウントのものとして配られる
        assertNull(repositories.accounts.add(username = USERNAME, createdAt = CREATED_AT))
    }

    @Test
    fun `消すとフィードの URL は空く`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()

        serviceOf(repositories).delete(USERNAME)

        val other = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
        assertEquals(FEED_URL, assertNotNull(repositories.feeds.add(newFeed(other))).url)
    }

    @Test
    fun `2 回目の削除は投函しない`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()
        val service = serviceOf(repositories)
        service.delete(USERNAME)

        val result = service.delete(USERNAME)

        val failure = assertIs<AccountService.DeleteResult.Failure>(result)
        assertEquals(AccountService.DeleteFailure.UNKNOWN_ACCOUNT, failure.reason)
        // 1 回目の分だけ。消せた 1 つしか投函まで進まない
        assertEquals(1, repositories.deliveryQueue.rows().size)
    }

    @Test
    fun `知らないアカウントは消せない`() = runTest {
        val repositories = FakeRepositories()

        val result = serviceOf(repositories).delete("nobody")

        val failure = assertIs<AccountService.DeleteResult.Failure>(result)
        assertEquals(AccountService.DeleteFailure.UNKNOWN_ACCOUNT, failure.reason)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `絵文字だけの表示名をコードポイント数で上限まで保存できる`() = runTest {
        val repositories = FakeRepositories()
        repositories.accounts.add(username = USERNAME, createdAt = CREATED_AT)

        val result = serviceOf(repositories).updateProfile(
            username = USERNAME,
            displayName = "😀".repeat(AccountProfileLimits.DISPLAY_NAME_MAX_LENGTH),
            summary = "",
        )

        assertIs<AccountService.UpdateProfileResult.Success>(result)
    }

    @Test
    fun `プロフィールを更新するとフォロワーに Update Actor を配る`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()

        val result = serviceOf(repositories).updateProfile(
            username = USERNAME,
            displayName = "更新後",
            summary = "新しい説明",
        )

        assertIs<AccountService.UpdateProfileResult.Success>(result)
        val body = assertNotNull(repositories.deliveryQueue.rows().single().body)
        assertContains(body, "\"type\":\"Update\"")
        assertContains(body, "\"actor\":\"https://${TestLocalActor.DOMAIN}/users/$USERNAME\"")
        assertContains(body, "\"name\":\"更新後\"")
        assertContains(body, "\"summary\":\"<p>新しい説明</p>\"")
    }

    @Test
    fun `配る Update Actor には保存しているフィードの attachment と画面の url も入る`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()

        val result = serviceOf(repositories).updateProfile(
            username = USERNAME,
            displayName = "更新後",
            summary = "新しい説明",
        )

        assertIs<AccountService.UpdateProfileResult.Success>(result)
        val body = assertNotNull(repositories.deliveryQueue.rows().single().body)
        assertContains(body, "\"inbox\":\"https://${TestLocalActor.DOMAIN}/users/$USERNAME/inbox\"")
        assertContains(body, FEED_URL)
        assertContains(body, "\"url\":\"${TestWebPageUrls.profile(USERNAME)}\"")
    }

    @Test
    fun `表示名も説明文も変わらない保存では配らない`() = runTest {
        val repositories = FakeRepositories()
        repositories.withFullAccount()
        val service = serviceOf(repositories)
        service.updateProfile(username = USERNAME, displayName = "更新後", summary = "新しい説明")

        val result = service.updateProfile(username = USERNAME, displayName = "更新後", summary = "新しい説明")

        assertIs<AccountService.UpdateProfileResult.Success>(result)
        // 送り残した更新は置き換わる。古い方が後から届くと相手の表示が 1 つ前に戻る
        assertEquals(1, repositories.deliveryQueue.rows().size)
    }

    private fun serviceOf(repositories: FakeRepositories): AccountService = AccountService(
        accounts = repositories.accounts,
        followers = repositories.followers,
        actorPublisher = TestActorPublisher.of(repositories),
        actorEnqueuer = TestActorPublisher.enqueuerOf(repositories),
        iconFiles = AccountIconFiles(
            feeds = repositories.feeds,
            icons = repositories.feedIcons,
            store = iconStore,
        ),
        domain = TestLocalActor.DOMAIN,
    )

    /**
     * フォロワーと投稿とフィードと記事が 1 件ずつ付いたアカウントを作る
     */
    private fun FakeRepositories.withFullAccount(): Account {
        val account = assertNotNull(accounts.add(username = USERNAME, createdAt = CREATED_AT))

        followers.record(
            IncomingFollow(
                username = USERNAME,
                follower = NewRemoteActor(
                    actorUri = FOLLOWER_ACTOR_URI,
                    inbox = FOLLOWER_INBOX,
                    sharedInbox = null,
                    publicKeyPem = "pem",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "$FOLLOWER_ACTOR_URI/follows/1",
                receivedAt = CREATED_AT,
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        // Accept が届いて初めてフォロワーになる。投函した行はここで消える
        deliveryQueue.claim(now = CREATED_AT, limit = 10).forEach {
            deliveryQueue.markDelivered(id = it.id, deliveredAt = CREATED_AT)
        }

        notes.add(
            NewNote(
                username = USERNAME,
                publicId = PublicNoteId("note-1"),
                contentHtml = "<p>本文</p>",
                publishedAt = CREATED_AT,
            ),
        )

        val feed = assertNotNull(feeds.add(newFeed(account)))
        feedItems.add(
            NewFeedItem(
                feedId = feed.id,
                itemKey = "item-1",
                title = "1 本目",
                link = "https://example.com/1",
                contentHtml = "<p>記事</p>",
                publishedAt = CREATED_AT,
                importedAt = CREATED_AT,
                state = FeedItemState.PENDING,
            ),
        )

        return account
    }

    private fun newFeed(account: Account): NewFeed = NewFeed(
        accountId = account.id,
        url = FEED_URL,
        title = "サンプル",
        siteUrl = "https://example.com/",
        format = "RSS 2.0",
        iconUrl = null,
        pollIntervalSeconds = POLL_INTERVAL_SECONDS,
    )

    private companion object {
        const val USERNAME = "feed1"
        const val FEED_URL = "https://example.com/feed.xml"
        const val FOLLOWER_ACTOR_URI = "https://remote.example/users/follower"
        const val FOLLOWER_INBOX = "https://remote.example/users/follower/inbox"
        const val POLL_INTERVAL_SECONDS = 900L
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
    }
}
