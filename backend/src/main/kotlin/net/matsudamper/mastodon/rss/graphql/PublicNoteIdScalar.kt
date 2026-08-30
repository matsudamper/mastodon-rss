package net.matsudamper.mastodon.rss.graphql

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
import net.matsudamper.mastodon.rss.shared.PublicNoteId

object PublicNoteIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("PublicNoteId")
        .description("投稿の公開 id")
        .coercing(PublicNoteIdCoercing)
        .build()

    private object PublicNoteIdCoercing : Coercing<PublicNoteId, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return (dataFetcherResult as? PublicNoteId)?.value
                ?: throw CoercingSerializeException(
                    "PublicNoteId にできない型: ${dataFetcherResult::class.qualifiedName}",
                )
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): PublicNoteId {
            return when (input) {
                is PublicNoteId -> input
                is String -> PublicNoteId(input)
                else -> throw CoercingParseValueException("PublicNoteId として読めない: $input")
            }
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): PublicNoteId {
            val raw = (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException("PublicNoteId は文字列で書く")
            return PublicNoteId(raw)
        }
    }
}
