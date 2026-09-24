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
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.sqlite.SQLiteDataSource

// 本物の SQLite に対して確かめる。
// 相手は同じお気に入りを送り直してくるので、増えないことと、取り消しで減ることが要件になる。
class NoteFavouriteRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-favourite-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    private val notePublicId = PublicNoteId("note1")

    private val actorUri = "https://remote.example/users/alice"

    private val otherActorUri = "https://remote.example/users/bob"

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /**
     * お気に入りは投稿を指すので、先に投稿を入れておく
     */
    private fun <T> withRepositories(block: (Repositories) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.notes.add(
                NewNote(
                    username = "admin",
                    publicId = notePublicId,
                    contentHtml = "<p>本文</p>",
                    publishedAt = now,
                ),
            )
            block(repositories)
        }

    /**
     * 押した相手。鍵は消えた後の `Delete` を検証するために残るので、
     * 相手ごとに違うものを入れて取り違えが分かるようにする
     */
    private fun actor(actorUri: String): NewRemoteActor = NewRemoteActor(
        actorUri = actorUri,
        inbox = "$actorUri/inbox",
        sharedInbox = null,
        publicKeyPem = "pem of $actorUri",
        profile = RemoteActorProfile(
            preferredUsername = null,
            displayName = null,
            profileUrl = null,
            iconUrl = null,
        ),
    )

    private fun favourite(
        activityUri: String,
        actorUri: String,
    ): NewNoteFavourite = NewNoteFavourite(
        notePublicId = notePublicId,
        actor = actor(actorUri),
        activityUri = activityUri,
        receivedAt = now,
    )

    @Test
    fun `投稿ごとにお気に入りを数えられる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))
            favourites.add(favourite(activityUri = "https://remote.example/likes/2", actorUri = otherActorUri))

            val counted = favourites.countsByNotes(setOf(notePublicId, PublicNoteId("none")))

            // 1 件も無い投稿は含めない
            assertEquals(mapOf(notePublicId to 2), counted)
        }
    }

    @Test
    fun `同じ相手のお気に入りは増えない`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            assertTrue(favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri)))

            // 同じアクティビティの送り直し
            assertFalse(favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri)))

            // 別の id で押し直された同じお気に入り
            assertFalse(favourites.add(favourite(activityUri = "https://remote.example/likes/2", actorUri = actorUri)))

            assertEquals(mapOf(notePublicId to 1), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `1 つの投稿が持てるお気に入りには上限がある`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites

            // 相手はアクターをいくつでも作れるので、1 人 1 件だけでは人数ぶんだけ増える
            val added = (1..600).count { index ->
                favourites.add(
                    favourite(
                        activityUri = "https://remote.example/likes/$index",
                        actorUri = "https://remote.example/users/$index",
                    ),
                )
            }

            assertEquals(500, added)
        }
    }

    @Test
    fun `別の相手が同じアクティビティの id を使っても弾かれない`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            val activityUri = "https://remote.example/likes/1"

            assertTrue(favourites.add(favourite(activityUri = activityUri, actorUri = actorUri)))

            // id を全体で一意にすると、先に書き込むだけで他人のお気に入りを弾ける
            assertTrue(favourites.add(favourite(activityUri = activityUri, actorUri = otherActorUri)))
        }
    }

    @Test
    fun `配信していない投稿へのお気に入りは記録しない`() {
        withRepositories { repositories ->
            val added = repositories.noteFavourites.add(
                NewNoteFavourite(
                    notePublicId = PublicNoteId("none"),
                    actor = actor(actorUri),
                    activityUri = "https://remote.example/likes/1",
                    receivedAt = now,
                ),
            )

            assertFalse(added)
        }
    }

    @Test
    fun `取り消しはアクティビティの id で消せる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            // 他人の取り消しでは消えない
            assertFalse(
                favourites.removeByActivityUri(
                    actorUri = otherActorUri,
                    activityUri = "https://remote.example/likes/1",
                ),
            )

            assertTrue(
                favourites.removeByActivityUri(actorUri = actorUri, activityUri = "https://remote.example/likes/1"),
            )
            assertEquals(mapOf(), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `取り消しは投稿と押した相手でも消せる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            assertFalse(favourites.removeByNote(notePublicId = notePublicId, actorUri = otherActorUri))
            assertTrue(favourites.removeByNote(notePublicId = notePublicId, actorUri = actorUri))

            assertEquals(mapOf(), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `消えた相手のお気に入りをまとめて消せる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))
            favourites.add(favourite(activityUri = "https://remote.example/likes/2", actorUri = otherActorUri))

            assertEquals(1, favourites.removeByActor(actorUri))

            assertEquals(mapOf(notePublicId to 1), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `投稿を消すとお気に入りも消える`() {
        withRepositories { repositories ->
            repositories.noteFavourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            repositories.notes.delete(notePublicId)

            assertEquals(mapOf(), repositories.noteFavourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `フォロワーでない相手でもお気に入りを押したときの鍵を引ける`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            // 相手が消えた後の Delete は、この鍵でしか検証できない
            assertEquals("pem of $actorUri", favourites.findPublicKeyPem(actorUri))
            assertNull(favourites.findPublicKeyPem(otherActorUri))
        }
    }

    @Test
    fun `お気に入りが残っていない相手の鍵は引けない`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            favourites.removeByActor(actorUri)

            // 関わりの切れた相手の鍵を返すと、その鍵で署名を通せる
            assertNull(favourites.findPublicKeyPem(actorUri))
        }
    }

    /**
     * 相手の行が残っているかは公開する口が無いので、DB を直接見る
     */
    private fun remoteActorCount(): Int {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        return dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM remote_actors").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }
    }

    @Test
    fun `取り消して誰も指さなくなった相手の行は消える`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))
            favourites.add(favourite(activityUri = "https://remote.example/likes/2", actorUri = otherActorUri))

            // 押してすぐ取り消すのを繰り返されても、相手の行が溜まらない
            favourites.removeByActivityUri(actorUri = actorUri, activityUri = "https://remote.example/likes/1")
            favourites.removeByNote(notePublicId = notePublicId, actorUri = otherActorUri)

            assertEquals(0, remoteActorCount())
        }
    }

    @Test
    fun `取り消してもフォローしている相手の行は残る`() {
        withRepositories { repositories ->
            repositories.followers.record(
                IncomingFollow(
                    username = "admin",
                    follower = actor(actorUri),
                    followActivityUri = "https://remote.example/follows/1",
                    receivedAt = now,
                    acceptBody = "{}",
                ),
            )
            val favourites = repositories.noteFavourites
            favourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            favourites.removeByActivityUri(actorUri = actorUri, activityUri = "https://remote.example/likes/1")

            // 消すとフォローまで外部キーで消える
            assertEquals("pem of $actorUri", repositories.followers.findPublicKeyPem(actorUri))
        }
    }

    @Test
    fun `相手のアクターを消すとお気に入りも消える`() {
        withRepositories { repositories ->
            repositories.noteFavourites.add(favourite(activityUri = "https://remote.example/likes/1", actorUri = actorUri))

            repositories.followers.removeRemoteActor(actorUri)

            assertEquals(mapOf(), repositories.noteFavourites.countsByNotes(setOf(notePublicId)))
        }
    }
}
