package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.applicationCall
import net.matsudamper.mastodon.rss.graphql.model.AdminQueryResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

class AdminQueryResolverImpl(
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
) : AdminQueryResolver {
    override fun session(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        return completed(adminSession(env.applicationCall(), passwordHash, sessions))
    }
}
