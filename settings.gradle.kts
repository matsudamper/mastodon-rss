rootProject.name = "mastodon-rss"

pluginManagement {
    includeBuild("build-logic")

    // 無いまま進むと「no value available」だけが出て、何を設定すればよいか分からない。
    // トークンには read:packages が要る
    fun credential(
        property: String,
        environment: String,
    ): String {
        val value =
            providers
                .gradleProperty(property)
                .orElse(providers.environmentVariable(environment))
                .orNull

        return requireNotNull(value) {
            "GitHub Packages の資格情報が無い。~/.gradle/gradle.properties に $property を書くか、" +
                "環境変数 $environment を渡すこと"
        }
    }

    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/matsudamper/graphql-java-codegen")
                    credentials {
                        username = credential("gpr.user", "GITHUB_ACTOR")
                        password = credential("gpr.key", "GITHUB_TOKEN")
                    }
                }
            }
            filter {
                includeGroupByRegex("io\\.github\\.kobylynskyi.*")
            }
        }

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

include(":backend")
include(":backend:crypto")
include(":backend:feature-mastodon")
include(":backend:graphql")
include(":backend:repository")
include(":backend:rss")
include(":frontend")
include(":shared")
