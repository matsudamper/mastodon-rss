package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    fixedActor: ActorUrls,
    val actorDirectory: ActorDirectory,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        fixed = fixedActor,
    )

    // TODO: 投稿の口(NoteService)は Phase 4 で足す。domain / NotePublisher / NoteStore の
    //       受け渡しもそのときにここへ足す
}
