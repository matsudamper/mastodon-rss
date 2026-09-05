package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * アカウントの内容を変えたことを、画面をまたいで伝える。
 *
 * 変えるのはダイアログの画面だが、その結果はアカウントの画面に出る。ダイアログは
 * 重ねて出している間もアカウントの画面を残すので、閉じてもそちらは作り直されず、
 * 自分では変更に気付けない。呼ぶ側と受け取る側のどちらの画面より長生きするものが
 * 要るので、画面の外に置く。
 */
object AdminAccountUpdates {
    private val changedUsernameFlow: MutableSharedFlow<String> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 内容が変わったアカウントのユーザー名 */
    val changedUsernames: Flow<String> = changedUsernameFlow.asSharedFlow()

    fun notifyChanged(username: String) {
        changedUsernameFlow.tryEmit(username)
    }
}
