package net.matsudamper.mastodon.rss.graalvm

import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeReflection

/**
 * GraphQL の結線に使うクラスを native-image のリフレクション対象に登録する。
 * `--features=` で渡す（指定は `backend/build.gradle.kts`）。
 *
 * 手で `reflect-config.json` に並べるとスキーマを触るたびに更新が要るので、
 * イメージのビルド時にクラスパスを走査する。何が要るのかと、それぞれ何で落ちたかは
 * `META-INF/native-image/` の README にある。
 */
class GraphQlReflectionFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        registerKotlinReflection()
        registerGraphQlReflection()
    }

    private fun registerGraphQlReflection() {
        GraphQlReflectionTargets.PACKAGES.forEach { packageName ->
            val classNames = GraphQlReflectionTargets.classNamesIn(packageName)

            // パッケージごとに確かめる。まとめて数えると、片方が空でも
            // もう片方の分で通ってしまい、登録漏れに気付けない
            check(classNames.isNotEmpty()) {
                "$packageName にクラスが 1 つも無い。パッケージを移したか、走査に失敗している"
            }

            classNames.forEach { register(it) }
        }
    }

    /** kickstart がリゾルバの引数を数えるのに kotlin-reflect を使う。実装は名前で引かれる */
    private fun registerKotlinReflection() {
        val factory = Class.forName(KOTLIN_REFLECTION_FACTORY, false, javaClass.classLoader)

        RuntimeReflection.register(factory)
        RuntimeReflection.register(*factory.declaredConstructors)
    }

    private fun register(className: String) {
        // initialize = false。登録のためにイメージのビルド中に static の初期化子を走らせない
        val clazz = Class.forName(className, false, GraphQlReflectionFeature::class.java.classLoader)

        RuntimeReflection.register(clazz)
        RuntimeReflection.register(*clazz.declaredConstructors)
        RuntimeReflection.register(*clazz.declaredMethods)
        RuntimeReflection.register(*clazz.declaredFields)
    }

    private companion object {
        const val KOTLIN_REFLECTION_FACTORY = "kotlin.reflect.jvm.internal.ReflectionFactoryImpl"
    }
}
