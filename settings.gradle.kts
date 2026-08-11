rootProject.name = "mastodon-rss"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal {
            // Apollo 5.x はポータルに実体が無く Maven Central へ 303 で飛ばされる。
            // このリダイレクトの扱いには環境差があり、CI では解決できなかったのでportalはexcludeする
            content {
                excludeGroup("com.apollographql.apollo")
            }
        }
        mavenCentral()
        google()
    }
}

// JDK をビルド側で用意する。ツールチェインが手元に無ければ Gradle が取ってくるので、
// JDK 25 と GraalVM を各自で入れる必要がなくなる。
//
// バージョンを version catalog に置けないのは、settings.gradle.kts の plugins ブロックが
// 評価される時点で catalog のアクセサがまだ無いため。Renovate はこの記法も追える
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// :crypto と :repository と :rss と :graphql は :backend からしか使われない（JCA も JDBC も
// javax.xml も JVM 専用で、Kotlin/Wasm の :frontend からは参照できない）ので、
// backend の下にネストする
include(":backend")
include(":backend:crypto")
include(":backend:graphql")
include(":backend:repository")
include(":backend:rss")
include(":frontend")
