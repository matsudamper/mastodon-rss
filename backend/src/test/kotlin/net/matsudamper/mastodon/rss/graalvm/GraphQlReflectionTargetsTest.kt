package net.matsudamper.mastodon.rss.graalvm

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * native-image のリフレクション登録から漏れているものが無いかを JVM のテストで見る。
 *
 * 本体（[GraphQlReflectionFeature]）が動くのはイメージのビルド中だけなので、
 * JVM のテストからは動かせない。走査の部分だけをここで確かめておけば、
 * パッケージを移した時点で気付ける。
 *
 * 登録が足りているかどうか自体はここでは分からない。それは native バイナリを
 * 動かして確かめる。
 */
class GraphQlReflectionTargetsTest {
    @Test
    fun `走査するパッケージにクラスがある`() {
        GraphQlReflectionTargets.PACKAGES.forEach { packageName ->
            assertTrue(
                GraphQlReflectionTargets.classNamesIn(packageName).isNotEmpty(),
                "$packageName にクラスが無い",
            )
        }
    }

    @Test
    fun `依存の jar に入っている生成物も拾う`() {
        val classNames = GraphQlReflectionTargets.classNamesIn(MODEL_PACKAGE)

        // モデルとリゾルバのインタフェースの両方が要る。
        // kickstart はモデルをプロパティ名で、リゾルバをメソッド名で引く
        assertContains(classNames, "$MODEL_PACKAGE.QlAdminSession")
        assertContains(classNames, "$MODEL_PACKAGE.AdminMutationResolver")
    }

    @Test
    fun `リゾルバの実装は走査するパッケージに置く`() {
        val scanned = GraphQlReflectionTargets.classNamesIn(RESOLVER_PACKAGE)

        val implementations =
            GraphQlReflectionTargets
                .classNamesIn(ROOT_PACKAGE)
                .filter { it.substringAfterLast('.').endsWith(RESOLVER_IMPL_SUFFIX) }

        // 1 つも見つからないなら走査が空振りしていて、下の確認が意味を持たない
        assertTrue(
            implementations.size >= EXPECTED_RESOLVER_IMPLS,
            "リゾルバの実装が $EXPECTED_RESOLVER_IMPLS 個未満しか見つからない: $implementations",
        )

        // 実装を別のパッケージに移すと登録から外れる。JVM のテストには影響しないので、
        // ここで見ておかないと気付く手段が無くなる
        assertEquals(
            emptyList(),
            implementations.filterNot { it in scanned },
            "リゾルバの実装が $RESOLVER_PACKAGE の外にある。走査の対象から外れる",
        )
    }

    private companion object {
        const val ROOT_PACKAGE = "net.matsudamper.mastodon.rss"
        const val MODEL_PACKAGE = "$ROOT_PACKAGE.graphql.model"
        const val RESOLVER_PACKAGE = "$ROOT_PACKAGE.graphql.resolver"
        const val RESOLVER_IMPL_SUFFIX = "ResolverImpl"

        /** Query / Mutation / AdminQuery / AdminMutation の 4 つ。増える分は数えない */
        const val EXPECTED_RESOLVER_IMPLS = 4
    }
}
