package net.matsudamper.mastodon.rss.actor

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.crypto.RsaKeys
import net.matsudamper.mastodon.rss.crypto.RsaSignature

// 起動のたびに同じ鍵が返ることを確認する。
// 鍵が入れ替わると相手側の署名検証が通らなくなるので、
// 一度書き出したファイルには触らないことを見ておく。
class ActorKeyLoaderTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-actor-key-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `ファイルが無ければ生成して書き出す`() {
        val path = tempDir.resolve("actor-private-key.pem")

        val key = ActorKeyLoader.load(ActorPrivateKey.File(path))

        assertEquals(ActorKey.Origin.GeneratedFile(path.toAbsolutePath().normalize()), key.origin)
        assertTrue(Files.exists(path))
        assertEquals(RsaKeys.encodeToPem(key.privateKey), Files.readString(path))
    }

    @Test
    fun `親ディレクトリが無くても作る`() {
        val path = tempDir.resolve("keys").resolve("actor-private-key.pem")

        ActorKeyLoader.load(ActorPrivateKey.File(path))

        assertTrue(Files.exists(path))
    }

    @Test
    fun `書き出した鍵ファイルは所有者しか読めない`() {
        val path = tempDir.resolve("actor-private-key.pem")

        ActorKeyLoader.load(ActorPrivateKey.File(path))

        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(path),
            )
        }
    }

    @Test
    fun `2回目は書き出した鍵をそのまま読む`() {
        val path = tempDir.resolve("actor-private-key.pem")

        val generated = ActorKeyLoader.load(ActorPrivateKey.File(path))
        val loaded = ActorKeyLoader.load(ActorPrivateKey.File(path))

        assertEquals(ActorKey.Origin.LoadedFile(path.toAbsolutePath().normalize()), loaded.origin)
        assertEquals(generated.privateKey, loaded.privateKey)
        assertEquals(generated.publicKeyPem, loaded.publicKeyPem)
    }

    @Test
    fun `環境変数の PEM から読む`() {
        val privateKey = RsaKeys.generateKeyPair().private

        val key = ActorKeyLoader.load(ActorPrivateKey.Pem(RsaKeys.encodeToPem(privateKey)))

        assertIs<ActorKey.Origin.Environment>(key.origin)
        assertEquals(privateKey, key.privateKey)
    }

    @Test
    fun `導いた公開鍵で秘密鍵の署名を検証できる`() {
        val key = ActorKeyLoader.load(ActorPrivateKey.File(tempDir.resolve("actor-private-key.pem")))
        val data = "署名する対象".toByteArray()

        val signature = RsaSignature.sign(key.privateKey, data)

        assertTrue(RsaSignature.verify(key.publicKey, data, signature))
    }

    @Test
    fun `読めない PEM は落とす`() {
        val path = tempDir.resolve("actor-private-key.pem")
        Files.writeString(path, "-----BEGIN PRIVATE KEY-----\nこれは鍵ではない\n-----END PRIVATE KEY-----\n")

        assertFailsWith<IllegalArgumentException> {
            ActorKeyLoader.load(ActorPrivateKey.File(path))
        }
        assertFailsWith<IllegalArgumentException> {
            ActorKeyLoader.load(ActorPrivateKey.Pem("鍵ではない"))
        }
    }
}
