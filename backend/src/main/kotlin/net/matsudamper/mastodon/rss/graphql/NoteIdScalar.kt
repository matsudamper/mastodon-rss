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
import net.matsudamper.mastodon.rss.shared.NoteId

object NoteIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("NoteId")
        .description("投稿の公開 id")
        .coercing(NoteIdCoercing)
        .build()

    private object NoteIdCoercing : Coercing<NoteId, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return (dataFetcherResult as? NoteId)?.value
                ?: throw CoercingSerializeException("NoteId にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): NoteId {
            return when (input) {
                is NoteId -> input
                is String -> NoteId(input)
                else -> throw CoercingParseValueException("NoteId として読めない: $input")
            }
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): NoteId {
            val raw = (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException("NoteId は文字列で書く")
            return NoteId(raw)
        }
    }
}
