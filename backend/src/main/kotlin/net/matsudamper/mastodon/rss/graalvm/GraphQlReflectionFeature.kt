package net.matsudamper.mastodon.rss.graalvm

import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeReflection

/**
 * GraphQL の結線に使うクラスを native-image のリフレクション対象に登録する。
 *
 * graphql-java-tools (kickstart) はスキーマとクラスの対応をリフレクションで解決する。
 * リゾルバはメソッド名で、モデルはプロパティ名で引かれるので、登録が無いと
 * そのフィールドを解決できないまま起動する。JVM のテストは通るので、
 * CI の native-image ジョブが唯一の検出手段になる。
 *
 * 手で `reflect-config.json` に並べないのは、スキーマにフィールドを足すたびに
 * 更新が要り、忘れると native バイナリでだけ落ちるため。イメージのビルド時に
 * クラスパスを走査すれば忘れようがない。
 *
 * 走査するのは [GraphQlReflectionTargets.PACKAGES] だけ。そこに入っていない
 * クラスは対象にならないので、リゾルバの実装は `graphql.resolver` パッケージに置くこと。
 * 置き場所は `GraphQlReflectionTargetsTest` が確かめている。
 *
 * `--features=` で native-image に渡す。指定は `backend/build.gradle.kts` にある。
 */
class GraphQlReflectionFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
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

    private fun register(className: String) {
        // initialize = false にするのは、登録のためにクラスを初期化させないため。
        // 初期化までするとイメージのビルド中に static の初期化子が走る
        val clazz = Class.forName(className, false, GraphQlReflectionFeature::class.java.classLoader)

        RuntimeReflection.register(clazz)
        RuntimeReflection.register(*clazz.declaredConstructors)
        RuntimeReflection.register(*clazz.declaredMethods)
        RuntimeReflection.register(*clazz.declaredFields)
    }
}
