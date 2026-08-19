package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 本物の SQLite に対して確かめる。
// 一意制約や外部キーの効き方はスキーマ側に書いてあるので、
// SQL を通さないと確かめたことにならない。
class FollowerRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-follower-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepository(block: (FollowerRepository) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it.followers) }

    private fun incomingFollow(
        username: String = "admin",
        actorUri: String = "https://remote.example/users/alice",
        followActivityUri: String = "https://remote.example/activities/1",
        sharedInbox: String? = null,
    ): IncomingFollow = IncomingFollow(
        username = username,
        follower = NewRemoteActor(
            actorUri = actorUri,
            inbox = "$actorUri/inbox",
            sharedInbox = sharedInbox,
            publicKeyPem = "pem",
        ),
        followActivityUri = followActivityUri,
        receivedAt = now,
    )

    @Test
    fun `Accept を返すまではフォロワーに数えない`() {
        withRepository { followers ->
            followers.record(incomingFollow())

            assertEquals(0, followers.count("admin"), "Accept 前なのに数えている")
            assertEquals(emptyList(), followers.list("admin", after = null, limit = 10))

            assertTrue(followers.markAccepted("admin", "https://remote.example/users/alice", now))

            assertEquals(1, followers.count("admin"))
            assertEquals(
                listOf("https://remote.example/users/alice"),
                followers.list("admin", after = null, limit = 10),
            )
        }
    }

    @Test
    fun `同じ相手からの Follow を二重に受けても行が増えない`() {
        withRepository { followers ->
            followers.record(incomingFollow())
            followers.markAccepted("admin", "https://remote.example/users/alice", now)

            // Accept を返し損ねたと思って送り直してくる形。id は同じ
            followers.record(incomingFollow())
            // フォロー済みの相手が別の Follow を作って送ってくる形
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/2"))

            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `アカウント名の大文字小文字は区別しない`() {
        withRepository { followers ->
            followers.record(incomingFollow(username = "Feed1"))
            followers.markAccepted("feed1", "https://remote.example/users/alice", now)

            // ActorDirectory は保存されている綴りを返すが、揺れても同じものを指す
            assertEquals(1, followers.count("FEED1"))
        }
    }

    @Test
    fun `別の Follow が後から届いていても Accept を返せた分は数える`() {
        withRepository { followers ->
            // 同じ相手からの 2 通目が先に記録され、1 通目の Accept が後から届く形。
            // どちらか 1 つに Accept が返れば、相手から見て関係は成立している
            followers.record(incomingFollow())
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/2"))

            assertTrue(followers.markAccepted("admin", "https://remote.example/users/alice", now))

            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `Follow の id を指定して解除できる`() {
        withRepository { followers ->
            followers.record(incomingFollow())
            followers.markAccepted("admin", "https://remote.example/users/alice", now)

            // 別のアクティビティの id では消えない。Undo の object が id だけで
            // 来たとき、それが本当に Follow の id だったのかはここで判断する
            assertFalse(
                followers.remove(
                    username = "admin",
                    followerActorUri = "https://remote.example/users/alice",
                    followActivityUri = "https://remote.example/activities/999",
                ),
            )
            assertEquals(1, followers.count("admin"))

            assertTrue(
                followers.remove(
                    username = "admin",
                    followerActorUri = "https://remote.example/users/alice",
                    followActivityUri = "https://remote.example/activities/1",
                ),
            )
            assertEquals(0, followers.count("admin"))
        }
    }

    @Test
    fun `アクターごと消すと全てのフォローが消える`() {
        withRepository { followers ->
            followers.record(incomingFollow(username = "admin"))
            followers.record(
                incomingFollow(username = "feed1", followActivityUri = "https://remote.example/activities/2"),
            )
            followers.record(
                incomingFollow(
                    actorUri = "https://remote.example/users/bob",
                    followActivityUri = "https://remote.example/activities/3",
                ),
            )
            followers.markAccepted("admin", "https://remote.example/users/alice", now)
            followers.markAccepted("feed1", "https://remote.example/users/alice", now)
            followers.markAccepted("admin", "https://remote.example/users/bob", now)

            assertEquals(2, followers.removeRemoteActor("https://remote.example/users/alice"))

            assertEquals(1, followers.count("admin"), "別の相手のフォローまで消えている")
            assertEquals(0, followers.count("feed1"))

            // 同じ相手をもう一度記録できる。remote_actors の行ごと消えているので、
            // 一意制約に引っかかって入らない、という形にならないこと
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/4"))
            followers.markAccepted("admin", "https://remote.example/users/alice", now)
            assertEquals(2, followers.count("admin"))
        }
    }

    @Test
    fun `URL 順に返り、cursor で続きから取れる`() {
        withRepository { followers ->
            repeat(5) { index ->
                followers.record(
                    incomingFollow(
                        actorUri = "https://remote.example/users/u$index",
                        followActivityUri = "https://remote.example/activities/$index",
                    ),
                )
                followers.markAccepted("admin", "https://remote.example/users/u$index", now)
            }

            val first = followers.list("admin", after = null, limit = 2)
            assertEquals(
                listOf("https://remote.example/users/u0", "https://remote.example/users/u1"),
                first,
            )

            val second = followers.list("admin", after = first.last(), limit = 2)
            assertEquals(
                listOf("https://remote.example/users/u2", "https://remote.example/users/u3"),
                second,
            )

            assertEquals(
                listOf("https://remote.example/users/u4"),
                followers.list("admin", after = second.last(), limit = 2),
            )
            assertEquals(
                emptyList(),
                followers.list("admin", after = "https://remote.example/users/u4", limit = 2),
            )
        }
    }

    @Test
    fun `まとめて数えると渡した綴りで返る`() {
        withRepository { followers ->
            followers.record(incomingFollow(username = "Feed1"))
            followers.markAccepted("feed1", "https://remote.example/users/alice", now)
            followers.record(
                incomingFollow(
                    username = "admin",
                    actorUri = "https://remote.example/users/bob",
                    followActivityUri = "https://remote.example/activities/2",
                ),
            )

            assertEquals(
                mapOf("FEED1" to 1L, "admin" to 0L, "other" to 0L),
                // admin は Accept を返せていないので 0。数えない相手も鍵は返す
                followers.counts(setOf("FEED1", "admin", "other")),
            )
            assertEquals(emptyMap(), followers.counts(emptySet()))
        }
    }

    @Test
    fun `Accept を返せていないフォローも hasAny では数える`() {
        withRepository { followers ->
            assertFalse(followers.hasAny())

            followers.record(incomingFollow())

            // 鍵の生成を止める判断に使うので、こちらは Accept 前でも数える
            assertTrue(followers.hasAny())
            assertEquals(0, followers.count("admin"))
        }
    }

    @Test
    fun `開き直してもフォロワーが残っている`() {
        withRepository { followers ->
            followers.record(incomingFollow())
            followers.markAccepted("admin", "https://remote.example/users/alice", now)
        }

        withRepository { followers ->
            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `記録が無い相手の操作は false`() {
        withRepository { followers ->
            assertFalse(followers.remove("admin", "https://remote.example/users/nobody", null))
            assertEquals(0, followers.removeRemoteActor("https://remote.example/users/nobody"))
            assertFalse(followers.markAccepted("admin", "https://remote.example/users/nobody", now))
        }
    }
}
