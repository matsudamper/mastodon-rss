package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 画面を離れても捨てずに持っておく値の置き場。1 画面につき 1 つ。
 *
 * 画面を離れると remember した値は消える。ViewModel まで作り直すと読み込み中の表示から始まり、
 * 中身の高さが無い間にスクロール位置が先頭へ詰められて、戻っても元の位置に戻れない。
 */
@Stable
internal class RetainedScreenState(parentScope: CoroutineScope) {
    /**
     * 画面を離れている間も止めない。置き場ごと捨てたときに止める
     */
    val coroutineScope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private val valuesByType: MutableMap<KClass<*>, Any> = mutableMapOf()

    fun getOrPut(type: KClass<*>, create: (CoroutineScope) -> Any): Any =
        valuesByType.getOrPut(type) { create(coroutineScope) }

    fun dispose() {
        coroutineScope.cancel()
        valuesByType.clear()
    }
}

internal val LocalRetainedScreenState = staticCompositionLocalOf<RetainedScreenState?> { null }

/**
 * 画面を離れて戻ってきても同じものを返す。同じ画面の中では型ごとに 1 つ。
 *
 * [create] に渡すスコープは画面を離れている間も動き続ける。
 * 置き場が無いとき（プレビュー）は remember と同じく組み立ての間だけ持つ。
 */
@Composable
internal inline fun <reified T : Any> rememberRetained(noinline create: (CoroutineScope) -> T): T {
    val retainedScreenState = LocalRetainedScreenState.current
    return if (retainedScreenState == null) {
        val coroutineScope = rememberCoroutineScope()
        remember(coroutineScope) { create(coroutineScope) }
    } else {
        remember(retainedScreenState) { retainedScreenState.getOrPut(T::class, create) as T }
    }
}
