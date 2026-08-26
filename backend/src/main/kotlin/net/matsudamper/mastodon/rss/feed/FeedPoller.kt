package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.logic.FeedService
import org.slf4j.LoggerFactory

/**
 * 取得の時期が来たフィードを定期的に取り込ませる。
 *
 * 取得の間隔を持つのはフィードの側（`Feed.pollIntervalSeconds`。登録時の既定は 15 分）で、
 * ここが決めるのはその時期が来たかを確かめに行く間隔だけ。確かめる間隔が取得の間隔と
 * 同じだと、ずれた分がそのまま遅れになるので短くする。
 *
 * @param batchLimit 1 回で見に行くフィードの数。溜まっていても一度に取りに行かない
 */
class FeedPoller(
    private val feedService: FeedService,
    private val checkInterval: Duration = DEFAULT_CHECK_INTERVAL,
    private val batchLimit: Int = DEFAULT_BATCH_LIMIT,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            while (true) {
                poll()
                delay(checkInterval)
            }
        }

    private suspend fun poll() {
        val results = try {
            feedService.pollDue(now = Instant.now(), limit = batchLimit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ここで投げると繰り返しが終わり、次に再起動するまで自動投稿が流れなくなる
            logger.warn("フィードの定期取得に失敗した", e)
            return
        }

        results.forEach { result ->
            if (result.error != null) {
                logger.warn("フィードを取得できなかった: ${result.url}: ${result.error}")
                return@forEach
            }
            if (result.postedItems.isNotEmpty()) {
                logger.info("${result.url} の新着 ${result.postedItems.size} 件を投稿した")
            }
        }
    }

    private companion object {
        val DEFAULT_CHECK_INTERVAL: Duration = 1.minutes
        const val DEFAULT_BATCH_LIMIT = 20
        val logger = LoggerFactory.getLogger(FeedPoller::class.java)
    }
}
