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
import kotlinx.coroutines.runBlocking
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles

// 配信する `Create` に入る画面の URL は、ルーティングが返す JSON と同じものでなければならない。
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
    fun `画面を配信する構成では配信する Create にも画面の URL が入る`() {
        staticSrcDir.resolve(StaticFiles.INDEX_FILE_NAME).writeText("<html></html>")
        val delivery = TestDelivery()
        val deps = testDependencies(
            env = TestServerEnv.of("STATIC_SRC_DIR" to staticSrcDir.toString()),
            delivery = delivery,
        )
        deps.acceptFollower()

        val published = runBlocking {
            deps.notePublisher.publish(
                sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
                contentHtml = "<p>本文</p>",
            )
        }

        assertContains(
            delivery.delivered.single().body,
            """"url":"https://${TestServerEnv.DOMAIN}/@${TestServerEnv.USERNAME}/${published.publicId.value}"""",
        )
    }

    @Test
    fun `画面を配信しない構成では配信する Create に url が入らない`() {
        val delivery = TestDelivery()
        val deps = testDependencies(delivery = delivery)
        deps.acceptFollower()

        runBlocking {
            deps.notePublisher.publish(
                sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
                contentHtml = "<p>本文</p>",
            )
        }

        // 出すと相手のパーマリンクが 404 のページを指す。無ければ相手は id に倒す
        assertFalse(delivery.delivered.single().body.contains(""""url":"""))
    }

    /**
     * 配信先が 1 つある状態にする。フォロワーが 0 だと何も送らないので、
     * 送った中身を見るテストが素通りする
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
                    profileUrl = null,
                    acct = null,
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
