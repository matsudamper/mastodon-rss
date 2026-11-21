package net.matsudamper.mastodon.rss.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RemoteImageRelayPermitsTest {
    @Test
    fun `枠を使い切っていれば中継せずに断る`() =
        runTest {
            val permits = RemoteImageRelayPermits(maxConcurrentRelays = 1)
            val holding = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = launch {
                permits.tryRelay {
                    holding.complete(Unit)
                    release.await()
                }
            }
            holding.await()

            var relayedWhileBusy = false
            val accepted = permits.tryRelay { relayedWhileBusy = true }

            assertFalse(accepted)
            assertFalse(relayedWhileBusy)

            release.complete(Unit)
            first.join()
        }

    @Test
    fun `中継が終われば枠が戻り次を受ける`() =
        runTest {
            val permits = RemoteImageRelayPermits(maxConcurrentRelays = 1)
            var relayedCount = 0

            assertTrue(permits.tryRelay { relayedCount++ })
            assertTrue(permits.tryRelay { relayedCount++ })
            assertEquals(2, relayedCount)
        }

    @Test
    fun `中継が失敗しても枠は戻る`() =
        runTest {
            val permits = RemoteImageRelayPermits(maxConcurrentRelays = 1)

            runCatching { permits.tryRelay { error("失敗") } }

            assertTrue(permits.tryRelay {})
        }
}
