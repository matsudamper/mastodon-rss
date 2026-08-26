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

    // jOOQ の公式 Gradle プラグインを Kotlin の型付き DSL で組み立てるために要る。
    // GenerationTool を Gradle デーモンの中で直接呼ぶプラグインなので、これを適用した
    // 時点で jOOQ 自体がデーモンのクラスパスに乗る。
    // jooq-codegen-gradle の compile classpath には org.jooq.meta.jaxb 側の型が
    // 含まれない(実行時の classpath にだけ jooq-codegen が付く)ので、型を直接参照する
    // こちらのコンパイルのために jooq-codegen も明示的に足す
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
