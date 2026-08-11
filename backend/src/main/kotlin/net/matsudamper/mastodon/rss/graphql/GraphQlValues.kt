package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** 実行結果を JSON にする */
fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> {
            JsonNull
        }

        is String -> {
            JsonPrimitive(this)
        }

        is Boolean -> {
            JsonPrimitive(this)
        }

        is Number -> {
            JsonPrimitive(this)
        }

        is Map<*, *> -> {
            JsonObject(
                entries.associate { (key, value) ->
                    require(key is String) { "GraphQL の結果のキーが文字列ではない: $key" }
                    key to value.toJsonElement()
                },
            )
        }

        is List<*> -> {
            JsonArray(map { it.toJsonElement() })
        }

        else -> {
            throw IllegalArgumentException("GraphQL の結果に知らない型が入っている: ${this::class.qualifiedName}")
        }
    }
}

/** 変数を graphql-java に渡せる形にする */
fun JsonElement.toRawValue(): Any? {
    return when (this) {
        is JsonNull -> {
            null
        }

        is JsonPrimitive -> {
            when {
                isString -> content
                else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }

        is JsonObject -> {
            mapValues { (_, value) -> value.toRawValue() }
        }

        is JsonArray -> {
            map { it.toRawValue() }
        }
    }
}
