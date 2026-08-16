package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.logic.AccountService

internal fun AccountService.ManagedAccount.toGraphqlResponse(): QlAdminAccount = QlAdminAccount(
    username = urls.username,
    // acct の頭の `acct:` は Mastodon の検索窓に貼るときに要らない
    acct = "@${urls.username}@${urls.domain}",
    actorUrl = urls.actorId,
    deletable = deletable,
    createdAt = createdAt?.epochSecond,
)
