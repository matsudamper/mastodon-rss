package net.matsudamper.mastodon.rss.logic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import net.matsudamper.mastodon.rss.feed.IconImageType
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取ってきたアイコンの中身を置くディレクトリ。
 *
 * DB には置き場だけを入れる。画像を DB に入れると、バックアップや持ち運びの単位が
 * 画像のぶんだけ重くなる。
 */
class FeedIconStore(
    private val root: Path,
) {
    /**
     * 書き込んで、[root] から見た置き場を返す。
     *
     * 書くたびに別の名前にする。同じ名前に上書きすると、DB の行を入れ替えるまでの間に
     * 読み出した側が新しい中身を古い種類で受け取る。古いファイルは、DB の行が
     * 新しい置き場を指した後に呼び出し側が消す
     */
    fun write(
        feedId: FeedId,
        bytes: ByteArray,
        imageType: IconImageType,
    ): String {
        Files.createDirectories(root)

        val path = fileName(feedId, imageType)
        val target = root.resolve(path)
        // 書いている途中のものを読み出されないよう、別名で書いてから move する
        val temporary = Files.createTempFile(root, path, ".tmp")

        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }

        return path
    }

    /**
     * 読む。消えていれば null。
     *
     * 消えていても DB には行が残るので、その場合は取り直す側に倒す
     */
    fun read(path: String): ByteArray? {
        val target = resolve(path) ?: return null
        if (!Files.isRegularFile(target)) return null
        return runCatching { Files.readAllBytes(target) }.getOrNull()
    }

    fun delete(path: String) {
        val target = resolve(path) ?: return
        runCatching { Files.deleteIfExists(target) }
    }

    /**
     * 置き場の文字列を [root] の下のファイルに解決する。
     *
     * 名前 1 つだけを受け付ける。DB に入っている値をそのまま繋ぐと、`..` を含む値で
     * ディレクトリの外に出られる
     */
    private fun resolve(path: String): Path? {
        val name = Path.of(path)
        if (name.nameCount != 1 || name.isAbsolute || name.toString() != path) return null
        return root.resolve(name)
    }

    private fun fileName(
        feedId: FeedId,
        imageType: IconImageType,
    ): String = "${feedId.value}-${UUID.randomUUID()}.${imageType.fileExtension}"
}
