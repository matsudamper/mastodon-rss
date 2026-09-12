package net.matsudamper.mastodon.rss

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles

// 投函する `Create` に入る画面の URL は、ルーティングが返す JSON と同じものでなければならない。
// 片方だけに入っていると、相手のタイムラインからは JSON のパスが開くのに、
// 直接引くと画面のパスが返る、という外からは追いにくい形になる。
class AppDependenciesTest {
    private val staticSrcDir: Path = Files.createTempDirectory("app-dependencies-test")

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        staticSrcDir.deleteRecursively()
    }

    @Test
    fun `画面を配信する構成では投函する Create にも画面の URL が入る`() {
        staticSrcDir.resolve(StaticFiles.INDEX_FILE_NAME).writeText("<html></html>")
        val deps = testDependencies(env = TestServerEnv.of("STATIC_SRC_DIR" to staticSrcDir.toString()))
        deps.acceptFollower()

        val queued = assertNotNull(
            deps.notePoster.post(
                sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
                contentHtml = "<p>本文</p>",
                feedItemId = null,
            ),
        )

        assertContains(
            deps.queuedBody(),
            """"url":"https://${TestServerEnv.DOMAIN}/@${TestServerEnv.USERNAME}/${queued.publicId.value}"""",
        )
    }

    @Test
    fun `画面を配信しない構成では投函する Create に url が入らない`() {
        val deps = testDependencies()
        deps.acceptFollower()

        deps.notePoster.post(
            sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
            contentHtml = "<p>本文</p>",
            feedItemId = null,
        )

        // 出すと相手のパーマリンクが 404 のページを指す。無ければ相手は id に倒す
        assertFalse(deps.queuedBody().contains(""""url":"""))
    }

    /**
     * 投函した 1 件の中身。ワーカーは動かさないので、キューから直に取り出して見る
     */
    private fun AppDependencies.queuedBody(): String =
        repositories.deliveryQueue.claim(now = Instant.now(), limit = 10).single().body

    /**
     * 配信先が 1 つある状態にする。フォロワーが 0 だと 1 件も投函されないので、
     * 投函した中身を見るテストが素通りする
     */
    private fun AppDependencies.acceptFollower() {
        val followerActorUri = "https://remote.example/users/alice"
        repositories.followers.record(
            IncomingFollow(
                username = TestServerEnv.USERNAME,
                follower = NewRemoteActor(
                    actorUri = followerActorUri,
                    inbox = "https://remote.example/users/alice/inbox",
                    sharedInbox = null,
                    publicKeyPem = "",
                ),
                followActivityUri = "https://remote.example/activities/follow-1",
                receivedAt = Instant.now(),
            ),
        )
        repositories.followers.markAccepted(
            username = TestServerEnv.USERNAME,
            followerActorUri = followerActorUri,
            acceptedAt = Instant.now(),
        )
    }
}
