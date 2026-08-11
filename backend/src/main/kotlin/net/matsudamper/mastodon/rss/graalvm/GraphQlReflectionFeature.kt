package net.matsudamper.mastodon.rss.graalvm

import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeReflection

/**
 * GraphQL の結線に使うクラスを native-image のリフレクション対象に登録する。
 *
 * graphql-java-tools (kickstart) はスキーマとクラスの対応をリフレクションで解決する。
 * リゾルバはメソッド名で、モデルはプロパティ名で引かれるので、その両方を登録する。
 *
 * これで足りることは native バイナリを動かして確かめた。JVM のテストはこの経路の
 * 問題を出さないので、依存やスキーマを足したときは native ビルドを通すこと。
 * 何が要るのかは `META-INF/native-image/` の README にまとめてある。
 *
 * 手で `reflect-config.json` に並べないのは、スキーマにフィールドを足すたびに
 * 更新が要るため。イメージのビルド時にクラスパスを走査すれば忘れようがない。
 *
 * 走査するのは [GraphQlReflectionTargets.PACKAGES] だけ。そこに入っていない
 * クラスは対象にならないので、リゾルバの実装は `graphql.resolver` パッケージに置くこと。
 * 置き場所は `GraphQlReflectionTargetsTest` が確かめている。
 *
 * `--features=` で native-image に渡す。指定は `backend/build.gradle.kts` にある。
 */
class GraphQlReflectionFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        registerKotlinReflection()

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

    /**
     * kotlin-reflect の実装を引けるようにする。
     *
     * kickstart はリゾルバの引数の数を数えるのに `ReflectJvmMapping.getKotlinFunction`
     * （kotlin-reflect）を使う。`kotlin.jvm.internal.Reflection` は実装を
     * `Class.forName` + `newInstance` で探す作りなので、登録が無いと実装が
     * 見つからず、素の JVM で動いていたものが native バイナリでだけ落ちる。
     *
     *   KotlinReflectionNotSupportedError: Kotlin reflection implementation is not
     *   found at runtime. Make sure you have kotlin-reflect.jar in the classpath
     *     at graphql.kickstart.tools.resolver.FieldResolverScanner.getMethodParameterCount
     *
     * jar はクラスパスに居る（kickstart の推移依存）。イメージに入れるための
     * 登録だけが足りていなかった。
     */
    private fun registerKotlinReflection() {
        val factory = Class.forName(KOTLIN_REFLECTION_FACTORY, false, javaClass.classLoader)

        RuntimeReflection.register(factory)
        RuntimeReflection.register(*factory.declaredConstructors)
    }

    private fun register(className: String) {
        // initialize = false にするのは、登録のためにクラスを初期化させないため。
        // 初期化までするとイメージのビルド中に static の初期化子が走る
        val clazz = Class.forName(className, false, GraphQlReflectionFeature::class.java.classLoader)

        RuntimeReflection.register(clazz)
        RuntimeReflection.register(*clazz.declaredConstructors)
        RuntimeReflection.register(*clazz.declaredMethods)
        RuntimeReflection.register(*clazz.declaredFields)
    }

    private companion object {
        /** `kotlin.jvm.internal.Reflection` がこの名前で実装を探す */
        const val KOTLIN_REFLECTION_FACTORY = "kotlin.reflect.jvm.internal.ReflectionFactoryImpl"
    }
}
