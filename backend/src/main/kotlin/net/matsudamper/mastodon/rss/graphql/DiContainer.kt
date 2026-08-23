package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
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
}
