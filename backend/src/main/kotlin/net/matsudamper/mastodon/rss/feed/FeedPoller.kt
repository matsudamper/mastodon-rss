package net.matsudamper.mastodon.rss.feed

import java.net.URI
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.repository.Feed
import org.slf4j.LoggerFactory

/**
 * 取得の時期が来たフィードを定期的に取り込ませる。
 *
 * 取得の間隔を持つのはフィードの側（`Feed.pollIntervalSeconds`。登録時の既定は 15 分）で、
 * ここが決めるのはその時期が来たかを確かめに行く間隔だけ。確かめる間隔が取得の間隔と
 * 同じだと、ずれた分がそのまま遅れになるので短くする。
 *
 * @param batchLimit 1 回で見に行くフィードの数。溜まっていても一度に取りに行かない
 * @param openTelemetry 確認 1 回と取り込み 1 本ごとに root の span を出す。配信ワーカーと同じく
 *   HTTP の親は付けない
 */
class FeedPoller(
    private val feedService: FeedService,
    private val checkInterval: Duration = DEFAULT_CHECK_INTERVAL,
    private val batchLimit: Int = DEFAULT_BATCH_LIMIT,
    openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    private val tracer = openTelemetry.getTracer("feed-poller")

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            while (true) {
                poll()
                delay(checkInterval)
            }
        }

    internal suspend fun poll() {
        val now = Instant.now()
        val due = try {
            findDueInSpan(now = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ここで投げると繰り返しが終わり、次に再起動するまで自動投稿が流れなくなる
            logger.warn("フィードの定期取得に失敗した", e)
            return
        }

        val results = due.map { feed ->
            try {
                pollFeedInSpan(feed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("フィードの定期取得に失敗した", e)
                FeedService.PollResult(
                    feedId = feed.id,
                    host = feed.logHost(),
                    postedItems = emptyList(),
                    error = "処理中に例外が出た",
                )
            }
        }

        results.forEach { result ->
            // URL 全体は出さない。購読者だけが知るトークンを含むことがある
            val feed = "フィード ${result.feedId.value}（${result.host}）"
            if (result.error != null) {
                logger.warn("$feed を取得できなかった: ${result.error}")
                return@forEach
            }
            if (result.postedItems.isNotEmpty()) {
                logger.info("$feed の新着 ${result.postedItems.size} 件を投稿した")
            }
        }
    }

    /**
     * 取得の時期が来たフィードを列挙するだけの 1 トレース。中の SQL はここにぶら下がる。
     */
    private suspend fun findDueInSpan(now: Instant): List<Feed> {
        val span =
            tracer.spanBuilder("FeedPoller.poll")
                .setNoParent()
                .startSpan()
        return try {
            withContext(Context.current().with(span).asContextElement()) {
                feedService.findDue(now = now, limit = batchLimit)
            }.also { due ->
                FeedPollSpan.set(FeedPollSpan.DUE_COUNT, due.size.toLong())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FeedPollSpan.pollFailed(e)
            throw e
        } finally {
            span.end()
        }
    }

    /**
     * 1 本の取り込みを 1 トレースにする。確認のトレースとは別の root にする。
     */
    private suspend fun pollFeedInSpan(feed: Feed): FeedService.PollResult {
        val span =
            tracer.spanBuilder("FeedService.poll")
                .setNoParent()
                .setAttribute(FeedPollSpan.FEED_ID, feed.id.value)
                .setAttribute(FeedPollSpan.HOST, feed.logHost())
                .startSpan()
        return try {
            withContext(Context.current().with(span).asContextElement()) {
                feedService.pollOne(feed)
            }.also { result ->
                if (result.error != null) {
                    FeedPollSpan.pollFailed(result.error)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FeedPollSpan.pollFailed(e)
            throw e
        } finally {
            span.end()
        }
    }

    private fun Feed.logHost(): String = runCatching { URI(url).host }.getOrNull().orEmpty()

    private companion object {
        val DEFAULT_CHECK_INTERVAL: Duration = 1.minutes
        const val DEFAULT_BATCH_LIMIT = 20
        val logger = LoggerFactory.getLogger(FeedPoller::class.java)
    }
}
