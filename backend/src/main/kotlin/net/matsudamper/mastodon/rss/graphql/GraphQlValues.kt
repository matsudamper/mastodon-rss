package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object GraphQlValues {
    /**
     * 実行結果を JSON にする
     */
    fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> {
                JsonNull
            }

            is String -> {
                JsonPrimitive(value)
            }

            is Boolean -> {
                JsonPrimitive(value)
            }

            is Number -> {
                JsonPrimitive(value)
            }

            is Map<*, *> -> {
                JsonObject(
                    value.entries.associate { (key, element) ->
                        require(key is String) { "GraphQL の結果のキーが文字列ではない: $key" }
                        key to toJsonElement(element)
                    },
                )
            }

            is List<*> -> {
                JsonArray(value.map { toJsonElement(it) })
            }

            else -> {
                throw IllegalArgumentException("GraphQL の結果に知らない型が入っている: ${value::class.qualifiedName}")
            }
        }
    }

    /**
     * 変数を graphql-java に渡せる形にする
     */
    fun toRawValue(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    else -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
                }
            }

            is JsonObject -> {
                element.mapValues { (_, value) -> toRawValue(value) }
            }

            is JsonArray -> {
                element.map { toRawValue(it) }
            }
        }
    }
}
