package net.matsudamper.mastodon.rss.graphql

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import net.matsudamper.mastodon.rss.shared.FeedId

object FeedIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("FeedId")
        .description("DB 上のフィード id")
        .coercing(FeedIdCoercing)
        .build()

    private object FeedIdCoercing : Coercing<FeedId, Long> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
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
                is Number -> FeedId(input.toLong())
                is String -> input.toLongOrNull()?.let(::FeedId)
                else -> null
            } ?: throw CoercingParseValueException("FeedId として読めない: $input")
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): FeedId {
            val raw = when (input) {
                // toLong() は桁が溢れると別の値に化ける
                is IntValue -> runCatching { input.value.longValueExact() }.getOrNull()

                is StringValue -> input.value?.toLongOrNull()

                else -> null
            }
            return raw?.let(::FeedId)
                ?: throw CoercingParseLiteralException("FeedId は整数で書く")
        }
    }
}
