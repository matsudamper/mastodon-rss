package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * 下の画面を残したまま重なる画面だという目印。[NavEntry] の metadata に載せる。
 *
 * 下に敷く画面があるかどうかはバックスタックを見る側にしか分からないので、
 * 重ねてよいかの判断材料だけをここに置く。どう出すか（ダイアログ、シート、
 * 全面に敷くなど）は画面が自分で決める。
 *
 * 下の画面は描かれ続けるだけで、止まってはいない。押せてしまうと困るなら、
 * 入力を遮るものを画面が自分で出すこと。ダイアログなら [androidx.compose.material3.AlertDialog]
 * が持つ暗幕がその役目をする。
 */
object TransparentScreen : NavMetadataKey<Unit> {
    fun asMetadata(): Map<String, Any> = metadata { put(TransparentScreen, Unit) }
}

/**
 * [TransparentScreen] の画面を、下の画面を描いたまま重ねる。
 *
 * Navigation 3 の既定は一番上の画面だけを描くので、重なる画面を 1 つの画面として
 * 積むと下が消える。ここで [OverlayScene] にすると、下の画面は別の Scene として
 * 描かれ続ける。
 */
class TransparentScreenSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val top = entries.lastOrNull() ?: return null
        if (TransparentScreen !in top.metadata) return null

        // 下に敷く画面が無いなら普通の画面として出す。何も敷かずに重ねると、
        // 閉じた先に何も無い画面になる
        val below = entries.dropLast(1)
        if (below.isEmpty()) return null

        return TransparentScene(
            key = top.contentKey,
            entry = top,
            below = below,
        )
    }
}

private class TransparentScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    private val below: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val overlaidEntries: List<NavEntry<T>> = below

    override val previousEntries: List<NavEntry<T>> = below

    override val content: @Composable () -> Unit = { entry.Content() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransparentScene<*>) return false

        return key == other.key && entry == other.entry && below == other.below
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entry.hashCode()
        result = 31 * result + below.hashCode()
        return result
    }

    override fun toString(): String = "TransparentScene(key=$key, entry=$entry, below=$below)"
}
