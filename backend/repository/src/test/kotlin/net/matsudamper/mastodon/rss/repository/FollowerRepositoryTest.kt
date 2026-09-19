package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        withRepositories { block(it.followers) }

    private fun <T> withRepositories(block: (Repositories) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it) }

    /**
     * 投函された `Accept` が全部相手に届いた状況を作る。
     *
     * フォローが成立するのは `Accept` を送れたときなので、記録しただけでは数えられない
     */
    private fun Repositories.acceptDelivered() {
        while (true) {
            val claimed = deliveryQueue.claim(now = now, limit = 50)
            if (claimed.isEmpty()) break
            claimed.forEach { deliveryQueue.markDelivered(id = it.id, deliveredAt = now) }
        }
    }

    private fun incomingFollow(
        username: String = "admin",
        actorUri: String = "https://remote.example/users/alice",
        followActivityUri: String = "https://remote.example/activities/1",
        sharedInbox: String? = null,
        profile: RemoteActorProfile = NO_PROFILE,
    ): IncomingFollow = IncomingFollow(
        username = username,
        follower = NewRemoteActor(
            actorUri = actorUri,
            inbox = "$actorUri/inbox",
            sharedInbox = sharedInbox,
            publicKeyPem = "pem",
            profile = profile,
        ),
        followActivityUri = followActivityUri,
        receivedAt = now,
        acceptBody = """{"type":"Accept"}""",
    )

    @Test
    fun `Accept を返すまではフォロワーに数えない`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())

            assertEquals(0, followers.count("admin"), "Accept 前なのに数えている")
            assertEquals(emptyList(), followers.actorUris("admin", after = null, limit = 10))
            assertEquals(emptyList(), followers.deliveryTargets("admin"))

            repositories.acceptDelivered()

            assertEquals(1, followers.count("admin"))
            assertEquals(
                listOf("https://remote.example/users/alice"),
                followers.actorUris("admin", after = null, limit = 10),
            )
        }
    }

    @Test
    fun `Accept を返す前でも公開鍵の PEM を引ける`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())

            // 相手が消えると文書を引けなくなるので、Delete の検証はこの記録が頼りになる
            assertEquals("pem", followers.findPublicKeyPem("https://remote.example/users/alice"))
            assertNull(followers.findPublicKeyPem("https://remote.example/users/bob"))
        }
    }

    @Test
    fun `記録済みの相手だけ公開鍵を読み直せる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())

            followers.rememberPublicKeyPem(
                actorUri = "https://remote.example/users/alice",
                publicKeyPem = "読み直した pem",
                readAt = now,
            )
            assertEquals("読み直した pem", followers.findPublicKeyPem("https://remote.example/users/alice"))

            // フォローしていない相手の鍵は溜めない
            followers.rememberPublicKeyPem(
                actorUri = "https://remote.example/users/bob",
                publicKeyPem = "pem",
                readAt = now,
            )
            assertNull(followers.findPublicKeyPem("https://remote.example/users/bob"))
        }
    }

    @Test
    fun `フォローを解除した相手の公開鍵は返さない`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())
            assertTrue(followers.remove("admin", "https://remote.example/users/alice", followActivityUri = null))

            // 消えたアクターの Delete を通す鍵なので、解除した相手の分を残すと署名を通せる
            assertNull(followers.findPublicKeyPem("https://remote.example/users/alice"))
        }
    }

    @Test
    fun `同じ相手からの Follow を二重に受けても行が増えない`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())
            repositories.acceptDelivered()

            // Accept を返し損ねたと思って送り直してくる形。id は同じ
            followers.record(incomingFollow())
            // フォロー済みの相手が別の Follow を作って送ってくる形
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/2"))

            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `アカウント名の大文字小文字は区別しない`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow(username = "Feed1"))
            repositories.acceptDelivered()

            // ActorDirectory は保存されている綴りを返すが、揺れても同じものを指す
            assertEquals(1, followers.count("FEED1"))
        }
    }

    @Test
    fun `別の Follow が後から届いていても Accept を返せた分は数える`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            // 同じ相手からの 2 通目が先に記録され、1 通目の Accept が後から届く形。
            // どちらか 1 つに Accept が返れば、相手から見て関係は成立している
            followers.record(incomingFollow())
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/2"))

            repositories.acceptDelivered()

            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `Follow の id を指定して解除できる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())
            repositories.acceptDelivered()

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
        withRepositories { repositories ->
            val followers = repositories.followers
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
            repositories.acceptDelivered()

            assertEquals(2, followers.removeRemoteActor("https://remote.example/users/alice"))

            assertEquals(1, followers.count("admin"), "別の相手のフォローまで消えている")
            assertEquals(0, followers.count("feed1"))

            // 同じ相手をもう一度記録できる。remote_actors の行ごと消えているので、
            // 一意制約に引っかかって入らない、という形にならないこと
            followers.record(incomingFollow(followActivityUri = "https://remote.example/activities/4"))
            repositories.acceptDelivered()
            assertEquals(2, followers.count("admin"))
        }
    }

    @Test
    fun `配信先は sharedInbox にまとまる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(
                incomingFollow(
                    actorUri = "https://a.example/users/alice",
                    sharedInbox = "https://a.example/inbox",
                ),
            )
            followers.record(
                incomingFollow(
                    actorUri = "https://a.example/users/bob",
                    followActivityUri = "https://remote.example/activities/2",
                    sharedInbox = "https://a.example/inbox",
                ),
            )
            followers.record(
                incomingFollow(
                    actorUri = "https://b.example/users/carol",
                    followActivityUri = "https://remote.example/activities/3",
                ),
            )
            repositories.acceptDelivered()

            assertEquals(
                listOf("https://a.example/inbox", "https://b.example/users/carol/inbox"),
                followers.deliveryTargets("admin").sorted(),
            )
        }
    }

    @Test
    fun `URL 順に返り、cursor で続きから取れる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            repeat(5) { index ->
                followers.record(
                    incomingFollow(
                        actorUri = "https://remote.example/users/u$index",
                        followActivityUri = "https://remote.example/activities/$index",
                    ),
                )
                repositories.acceptDelivered()
            }

            val first = followers.actorUris("admin", after = null, limit = 2)
            assertEquals(
                listOf("https://remote.example/users/u0", "https://remote.example/users/u1"),
                first,
            )

            val second = followers.actorUris("admin", after = first.last(), limit = 2)
            assertEquals(
                listOf("https://remote.example/users/u2", "https://remote.example/users/u3"),
                second,
            )

            assertEquals(
                listOf("https://remote.example/users/u4"),
                followers.actorUris("admin", after = second.last(), limit = 2),
            )
            assertEquals(
                emptyList(),
                followers.actorUris("admin", after = "https://remote.example/users/u4", limit = 2),
            )
        }
    }

    @Test
    fun `まとめて数えると渡した綴りで返る`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow(username = "Feed1"))
            repositories.acceptDelivered()
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
        withRepositories { repositories ->
            val followers = repositories.followers
            assertFalse(followers.hasAny())

            followers.record(incomingFollow())

            // 鍵の生成を止める判断に使うので、こちらは Accept 前でも数える
            assertTrue(followers.hasAny())
            assertEquals(0, followers.count("admin"))
        }
    }

    @Test
    fun `開き直してもフォロワーが残っている`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())
            repositories.acceptDelivered()
        }

        withRepositories { repositories ->
            val followers = repositories.followers
            assertEquals(1, followers.count("admin"))
        }
    }

    @Test
    fun `アカウントのフォローをまとめて消せる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow())
            repositories.acceptDelivered()
            followers.record(
                incomingFollow(
                    actorUri = "https://remote.example/users/bob",
                    followActivityUri = "https://remote.example/activities/2",
                ),
            )
            followers.record(
                incomingFollow(
                    username = "feed1",
                    followActivityUri = "https://remote.example/activities/3",
                ),
            )
            repositories.acceptDelivered()

            // Accept を返せていないものも消える
            assertEquals(2, followers.removeAccount("admin"))
            assertEquals(0, followers.count("admin"))
            // 同じ相手が他のアカウントをフォローしている分は残る
            assertEquals(1, followers.count("feed1"))
        }
    }

    @Test
    fun `記録が無い相手の操作は false`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            assertFalse(followers.remove("admin", "https://remote.example/users/nobody", null))
            assertEquals(0, followers.removeRemoteActor("https://remote.example/users/nobody"))
        }
    }

    @Test
    fun `相手が名乗ったプロフィールは一覧に出る`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow(profile = ALICE_PROFILE))
            repositories.acceptDelivered()

            assertEquals(
                listOf(
                    StoredFollower(
                        actorUri = "https://remote.example/users/alice",
                        preferredUsername = "alice",
                        displayName = "アリス",
                        profileUrl = "https://remote.example/@alice",
                        iconUrl = "https://files.remote.example/alice.png",
                    ),
                ),
                followers.list("admin", after = null, limit = 10),
            )
        }
    }

    @Test
    fun `プロフィールを受け取り直すと入れ替わる`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow(profile = ALICE_PROFILE))
            repositories.acceptDelivered()

            followers.rememberProfile(
                actorUri = "https://remote.example/users/alice",
                profile = ALICE_PROFILE.copy(displayName = "アリス（改名）", iconUrl = null),
            )

            val stored = followers.list("admin", after = null, limit = 10).single()
            assertEquals("アリス（改名）", stored.displayName)
            assertNull(stored.iconUrl)
            // 名乗り直していない部分はそのまま
            assertEquals("alice", stored.preferredUsername)
        }
    }

    @Test
    fun `フォローが残っていない相手のアイコンは引けない`() {
        withRepositories { repositories ->
            val followers = repositories.followers
            followers.record(incomingFollow(profile = ALICE_PROFILE))
            repositories.acceptDelivered()

            assertEquals(
                "https://files.remote.example/alice.png",
                followers.findIconUrl("https://remote.example/users/alice"),
            )

            followers.remove("admin", "https://remote.example/users/alice", null)

            // remote_actors の行は残るが、フォローしていない相手のアイコンを
            // こちらのドメインから配り続けることはしない
            assertNull(followers.findIconUrl("https://remote.example/users/alice"))
        }
    }

    private companion object {
        val NO_PROFILE: RemoteActorProfile =
            RemoteActorProfile(
                preferredUsername = null,
                displayName = null,
                profileUrl = null,
                iconUrl = null,
            )

        val ALICE_PROFILE: RemoteActorProfile =
            RemoteActorProfile(
                preferredUsername = "alice",
                displayName = "アリス",
                profileUrl = "https://remote.example/@alice",
                iconUrl = "https://files.remote.example/alice.png",
            )

        /**
         * URL だけを見るテスト向け。プロフィールまで書くと、確かめたい並び順が埋もれる
         */
        fun FollowerRepository.actorUris(
            username: String,
            after: String?,
            limit: Int,
        ): List<String> = list(username = username, after = after, limit = limit).map { it.actorUri }
    }
}
