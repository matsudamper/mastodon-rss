package net.matsudamper.mastodon.rss.image

import kotlinx.coroutines.sync.Semaphore

/**
 * 無認証の画像中継が同時に取りに行く数の枠。
 *
 * 取得から送り終えるまで画像はメモリに丸ごと載る。同じ正しい URL を並列に叩かれるだけで
 * 使い切られないよう、フォロワーのアイコンとリンク先の画像の中継で 1 つの枠を共有する。
 * 待たせると接続とメモリがたまるので、空いていなければ取りに行かずに断る
 */
class RemoteImageRelayPermits(
    maxConcurrentRelays: Int,
) {
    private val permits = Semaphore(maxConcurrentRelays)

    /**
     * 枠が空いていれば [relay] を実行して true、使い切っていれば実行せずに false。
     *
     * 取得した画像は応答を書き終えるまで残るので、[relay] には応答の書き込みまで含める
     */
    suspend fun tryRelay(relay: suspend () -> Unit): Boolean {
        if (!permits.tryAcquire()) return false
        try {
            relay()
        } finally {
            permits.release()
        }
        return true
    }
}
