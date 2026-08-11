package net.matsudamper.mastodon.rss.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

// ここが崩れると、実行は通っているのにレスポンスの形だけが変わる。
class GraphQlValuesTest {
    @Test
    fun `実行結果を JSON にする`() {
        val result =
            mapOf(
                "data" to
                    mapOf(
                        "admin" to
                            mapOf(
                                "session" to mapOf("loggedIn" to true, "passwordConfigured" to false),
                                "failure" to null,
                                "count" to 3,
                                "names" to listOf("a", "b"),
                            ),
                    ),
            )

        assertEquals(
            """{"data":{"admin":{"session":{"loggedIn":true,"passwordConfigured":false},""" +
                """"failure":null,"count":3,"names":["a","b"]}}}""",
            result.toJsonElement().toString(),
        )
    }

    @Test
    fun `知らない型が来たら落ちる`() {
        assertFailsWith<IllegalArgumentException> {
            mapOf("value" to Any()).toJsonElement()
        }
    }

    @Test
    fun `キーが文字列でなければ落ちる`() {
        assertFailsWith<IllegalArgumentException> {
            mapOf(1 to "value").toJsonElement()
        }
    }

    @Test
    fun `変数を素の値に開く`() {
        val variables =
            buildJsonObject {
                put("password", JsonPrimitive("とても長いパスワード"))
                put("count", JsonPrimitive(3))
                put("ratio", JsonPrimitive(1.5))
                put("enabled", JsonPrimitive(true))
                put("missing", JsonNull)
                put("names", buildJsonArray { add(JsonPrimitive("a")) })
                put("nested", buildJsonObject { put("key", JsonPrimitive("value")) })
            }

        assertEquals(
            mapOf(
                "password" to "とても長いパスワード",
                "count" to 3L,
                "ratio" to 1.5,
                "enabled" to true,
                "missing" to null,
                "names" to listOf("a"),
                "nested" to mapOf("key" to "value"),
            ),
            variables.toRawValue(),
        )
    }

    @Test
    fun `数字に見える文字列は文字列のまま`() {
        val variables = JsonObject(mapOf("value" to JsonPrimitive("3")))

        assertEquals(mapOf("value" to "3"), variables.toRawValue())
    }
}
