rootProject.name = "mastodon-rss"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    resolutionStrategy {
        eachPlugin {
            // Apollo 5.x のプラグインマーカーは Gradle Plugin Portal に実体が無く、
            // Maven Central へ 303 で飛ばされる（4.x はポータルが直接返していた）。
            // このリダイレクトの扱いは環境によって差があり、CI では
            // 「could not resolve plugin artifact」でビルドが落ちた。
            // マーカーを引かずに実体を直接指せば、どこから引いても同じ結果になる
            if (requested.id.id == "com.apollographql.apollo") {
                useModule("com.apollographql.apollo:apollo-gradle-plugin:${requested.version}")
            }
        }
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

// GraphQL のスキーマは :backend と :frontend の両方から使う。どちらかの下に置くと
// 相手のビルドがそのディレクトリを見ることになるので、等距離になる root に置く。
// 共有するものが増えたら :shared の下に並べる
include(":shared:graphql")
include(":shared:graphql:schema")
