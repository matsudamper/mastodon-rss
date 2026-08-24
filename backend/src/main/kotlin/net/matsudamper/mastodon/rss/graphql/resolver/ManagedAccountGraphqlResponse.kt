package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminAccount
import net.matsudamper.mastodon.rss.logic.AccountService

internal fun AccountService.ManagedAccount.toGraphqlResponse(): QlAdminAccount = QlAdminAccount(
    account = urls.toGraphqlResponse(accountId = accountId),
    createdAt = createdAt.epochSecond,
)
