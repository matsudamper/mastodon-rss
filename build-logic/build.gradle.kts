import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

// Java のツールチェーンは指定しない。ここで作るのはビルド中に Gradle 自身が
// 読み込むプラグインなので、デーモンが動いている JVM で動く必要がある。
// 25 を指定すると、それより古い JVM でデーモンが動いている環境で読めなくなる

configure<KtlintExtension> {
    // 本体と同じバージョンで揃える。別々に動くビルドなので個別に設定が要る
    version.set(libs.versions.ktlint.get())
}

dependencies {
    // native-image の設定（どの GraalVM で作るか）をプラグイン側で書くために要る。
    // モジュールごとに同じ結線を 3 回書かないようにするため
    implementation(libs.graalvm.native.plugin)

    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.codegen)
}

gradlePlugin {
    plugins {
        create("databaseCodegen") {
            id = "mastodon-rss.database-codegen"
            implementationClass = "net.matsudamper.mastodon.rss.gradle.DatabaseCodegenPlugin"
        }

        create("graphQlSchemaList") {
            id = "mastodon-rss.graphql-schema-list"
            implementationClass = "net.matsudamper.mastodon.rss.gradle.GraphQlSchemaListPlugin"
        }

        create("webpackBundleHash") {
            id = "mastodon-rss.webpack-bundle-hash"
            implementationClass = "net.matsudamper.mastodon.rss.gradle.WebpackBundleHashPlugin"
        }

        create("nativeImage") {
            id = "mastodon-rss.native-image"
            implementationClass = "net.matsudamper.mastodon.rss.gradle.NativeImagePlugin"
        }
    }
}
