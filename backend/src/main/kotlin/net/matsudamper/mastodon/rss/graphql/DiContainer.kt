package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.repository.AccountRepository

/**
 * @param actorDirectory 名前からアクターを引く。応答する名前を決めているのはここなので、
 *   引き当てが要るものは自分で判定せずにこれを通す
 */
class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    fixedActor: ActorUrls,
    val actorDirectory: ActorDirectory,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(accounts = accountRepository, fixed = fixedActor)
}
