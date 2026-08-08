package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.repository.Repositories

// ルーティングのテストで使う Repositories の差し替え。
// 呼ばれたことだけを記録し、DB には一切触らない。
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
