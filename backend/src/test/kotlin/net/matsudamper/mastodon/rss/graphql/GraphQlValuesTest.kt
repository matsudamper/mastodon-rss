package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// graphql-java の素の値と JSON の変換を確認する。
// ここが崩れると、実行そのものは通っているのにレスポンスの形だけが変わる。
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

    // 黙って toString() すると、レスポンスの形が型によって変わるうえ変換の失敗に気付けない
    @Test
    fun `知らない型が来たら落ちる`() {
        assertFailsWith<IllegalArgumentException> {
            mapOf("value" to Any()).toJsonElement()
        }
    }

    // GraphQL のフィールド名は文字列でしか来ない。他のものが来たら組み立てが壊れている
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

    // 引用符が付いていたものは、中身が数字に見えても文字列のまま渡す
    @Test
    fun `数字に見える文字列は文字列のまま`() {
        val variables = JsonObject(mapOf("value" to JsonPrimitive("3")))

        assertEquals(mapOf("value" to "3"), variables.toRawValue())
    }
}
