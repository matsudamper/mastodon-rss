package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import net.matsudamper.mastodon.rss.FakeFeedHeaders
import net.matsudamper.mastodon.rss.FakeFeedIcons
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestActorPublisher
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteEnqueuer
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators

class FeedPollerOpenTelemetryTest {
    @Test
    fun `due が無いときは確認だけ 1 トレースで root を出す`() =
        runTest {
            val exporter = RecordingSpanExporter()
            val openTelemetry = sdk(openTelemetryExporter = exporter)
            val repositories = FakeRepositories()
            val account =
                assertNotNull(
                    repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT),
                )
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)

            FeedPoller(feedService = service, openTelemetry = openTelemetry).poll()

            val span = exporter.spans.single()
            assertEquals("FeedPoller.poll", span.name)
            assertEquals(false, span.parentSpanContext.isValid)
        }

    @Test
    fun `due の取り込みは確認と 1 本ずつ別 root を出す`() =
        runTest {
            val exporter = RecordingSpanExporter()
            val openTelemetry = sdk(openTelemetryExporter = exporter)
            val repositories = FakeRepositories()
            val account =
                assertNotNull(
                    repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT),
                )
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)
            val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
            repositories.feeds.recordFetchSuccess(
                id = feed.id,
                fetchedAt = Instant.now().minusSeconds(feed.pollIntervalSeconds + 1),
                validators = FeedFetchValidators.NONE,
            )

            FeedPoller(feedService = service, openTelemetry = openTelemetry).poll()

            assertEquals(
                listOf("FeedPoller.poll", "FeedService.poll"),
                exporter.spans.map { it.name },
            )
            exporter.spans.forEach { span ->
                assertFalse(span.parentSpanContext.isValid)
            }
        }

    private fun serviceOf(repositories: FakeRepositories): FeedService {
        val engine =
            MockEngine {
                respond(
                    content = FEED_XML,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/rss+xml"),
                )
            }
        return FeedService(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            feedItems = repositories.feedItems,
            fetcher = FeedFetchService(HttpClient(engine)),
            actorDirectory = TestLocalActor.directory,
            noteEnqueuer =
                NoteEnqueuer(
                    publisher =
                        NotePublisher(
                            notes = FakeNoteStore(),
                            webPages = TestWebPageUrls,
                        ),
                    followers = repositories.followers,
                    deliveryQueue = repositories.deliveryQueue,
                ),
            icons = FakeFeedIcons(),
            headers = FakeFeedHeaders(),
            actorEnqueuer = TestActorPublisher.enqueuerOf(repositories),
        )
    }

    private fun sdk(openTelemetryExporter: SpanExporter): OpenTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(openTelemetryExporter))
                    .build(),
            )
            .build()

    private class RecordingSpanExporter : SpanExporter {
        val spans = mutableListOf<SpanData>()

        override fun export(spans: Collection<SpanData>): CompletableResultCode {
            this.spans += spans
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
        const val FEED_URL = "https://example.com/feed.xml"
        val FEED_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
            """.trimIndent()
    }
}
