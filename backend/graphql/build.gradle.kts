import com.kobylynskyi.graphql.codegen.model.ApiInterfaceStrategy
import com.kobylynskyi.graphql.codegen.model.GeneratedLanguage
import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graphql.codegen)
    id("mastodon-rss.graphql-schema-list")
}

dependencies {
    api(libs.graphql.kickstart.tools)
    api(libs.graphql.java)
    implementation(project(":shared"))
}

val generatedSourcesDirectory = layout.buildDirectory.dir("generated/codegen")

kotlin {
    jvmToolchain(25)

    sourceSets.named("main") {
        kotlin.srcDir(generatedSourcesDirectory)
    }
}

tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
    graphqlSchemaPaths = layout.projectDirectory
        .dir("src/main/resources/graphql")
        .asFile
        .listFiles()
        .orEmpty()
        .filter { it.extension == "graphqls" }
        .map { it.toString() }
        .sorted()

    generatedLanguage = GeneratedLanguage.KOTLIN
    outputDir = generatedSourcesDirectory.get().asFile
    packageName = "net.matsudamper.mastodon.rss.graphql.model"

    customTypesMapping = mutableMapOf(
        "UnixTime" to "kotlin.Long",
        "PublicNoteId" to "net.matsudamper.mastodon.rss.shared.PublicNoteId",
        "AccountId" to "net.matsudamper.mastodon.rss.shared.AccountId",
        "FeedId" to "net.matsudamper.mastodon.rss.shared.FeedId",
        "FeedItemId" to "net.matsudamper.mastodon.rss.shared.FeedItemId",
    )

    modelNamePrefix = "Ql"
    generateImmutableModels = true
    generateBuilder = false

    // @Generated に生成時刻が入る。付けると中身が毎回変わってビルドキャッシュが効かない
    addGeneratedAnnotation = false

    fieldsWithResolvers = setOf("@lazy")
    generateParameterizedFieldsResolvers = true

    // 操作ごとのインタフェースを作らせると、Query.admin から AdminQueryResolver という
    // 名前が生まれ、AdminQuery 型のリゾルバと衝突して codegen が落ちる
    apiInterfaceStrategy = ApiInterfaceStrategy.DO_NOT_GENERATE

    apiReturnType = "java.util.concurrent.CompletionStage<graphql.execution.DataFetcherResult<{{TYPE}}>>"
    generateApisWithThrowsException = false
    generateDataFetchingEnvironmentArgumentInApis = true

    parentInterfaces {
        resolver = "graphql.kickstart.tools.GraphQLResolver<{{TYPE}}>"
        queryResolver = "graphql.kickstart.tools.GraphQLQueryResolver"
        mutationResolver = "graphql.kickstart.tools.GraphQLMutationResolver"
        subscriptionResolver = "graphql.kickstart.tools.GraphQLSubscriptionResolver"
    }
}

tasks.named("compileKotlin") {
    dependsOn("graphqlCodegen")
}

// ktlint のタスクはソースセットごと入力として見るので、生成より後に走らせないと
// Gradle が構成の誤りとして落とす
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn("graphqlCodegen")
}
