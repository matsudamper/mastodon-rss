rootProject.name = "mastodon-rss"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal {
            // Apollo 5.x はポータルに実体が無く Maven Central へ 303 で飛ばされる。
            // このリダイレクトの扱いには環境差があり、CI では解決できなかったので直接取る
            content {
                excludeGroup("com.apollographql.apollo")
            }
        }
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// :crypto と :repository と :rss は :backend からしか使われない（JCA も JDBC も
// javax.xml も JVM 専用で、Kotlin/Wasm の :frontend からは参照できない）ので、
// backend の下にネストする
include(":backend")
include(":backend:crypto")
include(":backend:repository")
include(":backend:rss")
include(":frontend")
include(":shared:graphql")
