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

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// :crypto と :repository と :rss と :graphql は :backend からしか使われない（JCA も JDBC も
// javax.xml も JVM 専用で、Kotlin/Wasm の :frontend からは参照できない）ので、
// backend の下にネストする。
//
// :backend:graphql はスキーマと、そこから生成したモデル・リゾルバのインタフェースを持つ。
// :frontend はスキーマのファイルを Apollo のコード生成の入力として読むだけで、依存はしない
include(":backend")
include(":backend:crypto")
include(":backend:graphql")
include(":backend:repository")
include(":backend:rss")
include(":frontend")
