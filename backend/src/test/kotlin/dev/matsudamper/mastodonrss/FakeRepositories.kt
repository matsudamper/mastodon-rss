package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.repository.Repositories

/**
 * ルーティングのテストで使う [Repositories] の差し替え。
 *
 * DB そのものの挙動は `:repository` 側でテストするので、ここでは実ファイルを触らない。
 */
class FakeRepositories : Repositories {
    var verifyWritableCallCount: Int = 0
        private set
    var closed: Boolean = false
        private set

    override fun verifyWritable() {
        verifyWritableCallCount++
    }

    override fun close() {
        closed = true
    }
}
