package net.matsudamper.mastodon.rss.graalvm

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GraphQlReflectionFeature] 自体はイメージのビルド中しか動かないので、走査だけを見る。
 * 登録が足りているかは native バイナリを動かして確かめる。
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
        val classNames = GraphQlReflectionTargets.classNamesIn(GraphQlReflectionTargets.MODEL_PACKAGE)

        assertContains(classNames, "${GraphQlReflectionTargets.MODEL_PACKAGE}.QlAdminSession")
        assertContains(classNames, "${GraphQlReflectionTargets.MODEL_PACKAGE}.AdminMutationResolver")
    }

    @Test
    fun `生成モデルが参照する shared の型も拾う`() {
        val classNames = GraphQlReflectionTargets.sharedTypeNamesReferencedByGraphqlModels()

        assertContains(classNames, "${GraphQlReflectionTargets.SHARED_PACKAGE}.AccountId")
        assertContains(classNames, "${GraphQlReflectionTargets.SHARED_PACKAGE}.FeedId")
        assertContains(classNames, "${GraphQlReflectionTargets.SHARED_PACKAGE}.PublicNoteId")
        assertTrue(
            classNames.none { it == "${GraphQlReflectionTargets.SHARED_PACKAGE}.GraphQlPath" },
            "GraphQL のモデルから参照していない型まで拾っている",
        )
    }

    @Test
    fun `リゾルバの実装は走査するパッケージに置く`() {
        val scanned = GraphQlReflectionTargets.classNamesIn(GraphQlReflectionTargets.RESOLVER_PACKAGE)

        val implementations =
            GraphQlReflectionTargets
                .classNamesIn(ROOT_PACKAGE)
                .filter { it.substringAfterLast('.').endsWith(RESOLVER_IMPL_SUFFIX) }

        // 空振りしていると下の確認が意味を持たない
        assertTrue(implementations.isNotEmpty(), "リゾルバの実装が 1 つも見つからない")

        assertEquals(
            emptyList(),
            implementations.filterNot { it in scanned },
            "リゾルバの実装が ${GraphQlReflectionTargets.RESOLVER_PACKAGE} の外にある。走査の対象から外れる",
        )
    }

    private companion object {
        const val ROOT_PACKAGE = "net.matsudamper.mastodon.rss"
        const val RESOLVER_IMPL_SUFFIX = "ResolverImpl"
    }
}
