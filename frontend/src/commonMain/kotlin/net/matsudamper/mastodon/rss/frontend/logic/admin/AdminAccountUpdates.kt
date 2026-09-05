package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * フィードを登録したことを、画面をまたいで伝える。
 *
 * 登録はダイアログの画面が行うが、その結果はアカウントの画面に出る。ダイアログは
 * 重ねて出している間もアカウントの画面を残すので、閉じてもそちらは作り直されず、
 * 自分では登録に気付けない。呼ぶ側と受け取る側のどちらの画面より長生きするものが
 * 要るので、画面の外に置く。
 */
object AdminFeedUpdates {
    private val registeredUsernameFlow: MutableSharedFlow<String> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** フィードを登録したアカウントのユーザー名 */
    val registeredUsernames: Flow<String> = registeredUsernameFlow.asSharedFlow()

    fun notifyRegistered(username: String) {
        registeredUsernameFlow.tryEmit(username)
    }
}
