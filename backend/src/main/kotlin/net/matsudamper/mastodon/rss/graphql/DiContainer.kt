package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.ActorUrls // pragma: allowlist secret
import net.matsudamper.mastodon.rss.crypto.PasswordHash // pragma: allowlist secret
import net.matsudamper.mastodon.rss.feed.FeedFetchService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.AccountService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.AdminLoginService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.FeedService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.NoteService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.note.NotePublisher // pragma: allowlist secret
import net.matsudamper.mastodon.rss.note.NoteStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.AccountRepository // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.FeedRepository // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.FollowerRepository // pragma: allowlist secret

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    feedRepository: FeedRepository,
    feedFetcher: FeedFetchService,
    fixedActor: ActorUrls,
    val actorDirectory: ActorDirectory,
    notePublisher: NotePublisher,
    val noteStore: NoteStore,
) {
    /**
     * 投稿の URL を組み立てるのに要る。アカウントの URL と同じドメイン
     */
    val domain: String = fixedActor.domain

    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        fixed = fixedActor,
    )

    val noteService: NoteService = NoteService(
        directory = actorDirectory,
        publisher = notePublisher,
        notes = noteStore,
    )

    val feedService: FeedService = FeedService(
        accounts = accountRepository,
        feeds = feedRepository,
        fetcher = feedFetcher,
    )
}
