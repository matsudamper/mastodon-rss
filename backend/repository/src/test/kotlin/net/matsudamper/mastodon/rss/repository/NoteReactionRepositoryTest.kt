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

// 本物の SQLite に対して確かめる。
// 相手は同じ反応を送り直してくるので、増えないことと、取り消しで減ることが要件になる。
class NoteReactionRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-reaction-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    private val notePublicId = PublicNoteId("note1")

    private val actorUri = "https://remote.example/users/alice"

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

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /**
     * 反応は投稿を指すので、先に投稿を入れておく
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

    private fun reaction(
        activityUri: String,
        emoji: String,
        actorUri: String = this.actorUri,
        emojiImageUrl: String? = null,
    ): NewNoteReaction = NewNoteReaction(
        notePublicId = notePublicId,
        actor = actor(actorUri),
        activityUri = activityUri,
        emoji = emoji,
        emojiImageUrl = emojiImageUrl,
        receivedAt = now,
    )

    @Test
    fun `お気に入りとスタンプを数えられる`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))
            reactions.add(
                reaction(
                    activityUri = "https://remote.example/likes/2",
                    emoji = ":kawaii:",
                    emojiImageUrl = "https://remote.example/emoji/kawaii.png",
                ),
            )
            reactions.add(
                reaction(
                    activityUri = "https://remote.example/likes/3",
                    emoji = ":kawaii:",
                    actorUri = "https://remote.example/users/bob",
                ),
            )

            val counted = reactions.countsByNotes(setOf(notePublicId)).getValue(notePublicId)

            // 多い順に並ぶ
            assertEquals(":kawaii:", counted[0].emoji)
            assertEquals(2, counted[0].count)
            assertEquals("https://remote.example/emoji/kawaii.png", counted[0].emojiImageUrl)
            assertEquals("", counted[1].emoji)
            assertEquals(1, counted[1].count)
        }
    }

    @Test
    fun `同じ相手の同じ反応は増えない`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            assertTrue(reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = "")))

            // 同じアクティビティの送り直し
            assertFalse(reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = "")))

            // 別の id で押し直された同じお気に入り
            assertFalse(reactions.add(reaction(activityUri = "https://remote.example/likes/2", emoji = "")))

            assertEquals(1, reactions.countsByNotes(setOf(notePublicId)).getValue(notePublicId).single().count)
        }
    }

    @Test
    fun `1 人の相手が積める反応には上限がある`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions

            // 絵文字を変えれば一意制約には当たらないので、上限が無いと際限なく入る
            val added = (1..20).count { index ->
                reactions.add(reaction(activityUri = "https://remote.example/likes/$index", emoji = "絵文字$index"))
            }

            assertEquals(8, added)

            // 別の相手は自分の分を押せる
            assertTrue(
                reactions.add(
                    reaction(
                        activityUri = "https://remote.example/likes/100",
                        emoji = "👍",
                        actorUri = "https://remote.example/users/bob",
                    ),
                ),
            )
        }
    }

    @Test
    fun `1 つの投稿が持てる反応にも上限がある`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions

            // 相手はアクターをいくつでも作れるので、相手ごとの上限だけでは人数ぶんだけ増える
            val added = (1..600).count { index ->
                reactions.add(
                    reaction(
                        activityUri = "https://remote.example/likes/$index",
                        emoji = "👍",
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
            val reactions = repositories.noteReactions
            val activityUri = "https://remote.example/likes/1"

            assertTrue(reactions.add(reaction(activityUri = activityUri, emoji = "👍")))

            // id を全体で一意にすると、先に書き込むだけで他人の反応を弾ける
            assertTrue(
                reactions.add(
                    reaction(
                        activityUri = activityUri,
                        emoji = "👍",
                        actorUri = "https://remote.example/users/bob",
                    ),
                ),
            )
        }
    }

    @Test
    fun `配信していない投稿への反応は記録しない`() {
        withRepositories { repositories ->
            val added = repositories.noteReactions.add(
                NewNoteReaction(
                    notePublicId = PublicNoteId("none"),
                    actor = actor(actorUri),
                    activityUri = "https://remote.example/likes/1",
                    emoji = "",
                    emojiImageUrl = null,
                    receivedAt = now,
                ),
            )

            assertFalse(added)
        }
    }

    @Test
    fun `取り消しはアクティビティの id でも絵文字でも消せる`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = "👍"))
            reactions.add(reaction(activityUri = "https://remote.example/likes/2", emoji = ":kawaii:"))

            // 他人の取り消しでは消えない
            assertFalse(
                reactions.removeByActivityUri(
                    actorUri = "https://remote.example/users/bob",
                    activityUri = "https://remote.example/likes/1",
                ),
            )

            assertTrue(
                reactions.removeByActivityUri(actorUri = actorUri, activityUri = "https://remote.example/likes/1"),
            )
            assertTrue(
                reactions.removeByEmoji(notePublicId = notePublicId, actorUri = actorUri, emoji = ":kawaii:"),
            )

            assertEquals(mapOf(), reactions.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `消えた相手の反応をまとめて消せる`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))
            reactions.add(reaction(activityUri = "https://remote.example/likes/2", emoji = "👍"))
            reactions.add(
                reaction(
                    activityUri = "https://remote.example/likes/3",
                    emoji = "👍",
                    actorUri = "https://remote.example/users/bob",
                ),
            )

            assertEquals(2, reactions.removeByActor(actorUri))

            val remained = reactions.countsByNotes(setOf(notePublicId)).getValue(notePublicId).single()
            assertEquals("👍", remained.emoji)
            assertEquals(1, remained.count)
        }
    }

    @Test
    fun `投稿を消すと反応も消える`() {
        withRepositories { repositories ->
            repositories.noteReactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))

            repositories.notes.delete(notePublicId)

            assertEquals(mapOf(), repositories.noteReactions.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `フォロワーでない相手でも反応を押したときの鍵を引ける`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))

            // 相手が消えた後の Delete は、この鍵でしか検証できない
            assertEquals("pem of $actorUri", reactions.findPublicKeyPem(actorUri))
            assertNull(reactions.findPublicKeyPem("https://remote.example/users/bob"))
        }
    }

    @Test
    fun `反応が残っていない相手の鍵は引けない`() {
        withRepositories { repositories ->
            val reactions = repositories.noteReactions
            reactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))

            reactions.removeByActor(actorUri)

            // 関わりの切れた相手の鍵を返すと、その鍵で署名を通せる
            assertNull(reactions.findPublicKeyPem(actorUri))
        }
    }

    @Test
    fun `相手のアクターを消すと反応も消える`() {
        withRepositories { repositories ->
            repositories.noteReactions.add(reaction(activityUri = "https://remote.example/likes/1", emoji = ""))

            repositories.followers.removeRemoteActor(actorUri)

            assertEquals(mapOf(), repositories.noteReactions.countsByNotes(setOf(notePublicId)))
        }
    }
}
