package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.mastodon.rss.graphql.model.QlAdminQuery
import net.matsudamper.mastodon.rss.graphql.model.QueryResolver

/**
 * `admin` の下に降りるためだけのリゾルバ。値は持たない。
 * ログインが要るかどうかは、この下の各フィールドで見る。
 */
class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return completed(QlAdminQuery())
    }
}
