package net.matsudamper.mastodon.rss.graphql // pragma: allowlist secret

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import net.matsudamper.mastodon.rss.shared.FeedId // pragma: allowlist secret

object FeedIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("FeedId")
        .description("DB 上のフィード id")
        .coercing(FeedIdCoercing)
        .build()

    private object FeedIdCoercing : Coercing<FeedId, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return (dataFetcherResult as? FeedId)?.value
                ?: throw CoercingSerializeException("FeedId にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): FeedId {
            return when (input) {
                is FeedId -> input
                is String -> FeedId(input)
                else -> throw CoercingParseValueException("FeedId として読めない: $input")
            }
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): FeedId {
            val raw = (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException("FeedId は文字列で書く")
            return FeedId(raw)
        }
    }
}
