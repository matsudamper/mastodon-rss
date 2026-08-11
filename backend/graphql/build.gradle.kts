import com.kobylynskyi.graphql.codegen.model.ApiInterfaceStrategy
import com.kobylynskyi.graphql.codegen.model.GeneratedLanguage
import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graphql.codegen)
    id("mastodon-rss.graphql-schema-list")
}

dependencies {
    // 生成されるリゾルバのインタフェースが graphql.kickstart.tools.GraphQLResolver を継承し、
    // 戻り値に graphql-java の DataFetcherResult が出るので、使う側にも見えている必要がある
    api(libs.graphql.kickstart.tools)

    // kickstart が推移で連れてくるものに任せず、version catalog で固定する。
    // Renovate に追従させるためでもある
    api(libs.graphql.java)
}

val generatedSourcesDirectory = layout.buildDirectory.dir("generated/codegen")

kotlin {
    jvmToolchain(25)

    sourceSets.named("main") {
        kotlin.srcDir(generatedSourcesDirectory)
    }
}

// スキーマからモデルとリゾルバのインタフェースを作る。手で書くものは
// リゾルバの実装だけになり、スキーマと合っているかはコンパイルで分かる
tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
    // このタスクは実行時に Task.project を触るので、宣言しないと configuration cache で落ちる
    notCompatibleWithConfigurationCache("graphqlCodegen uses Task.project at execution time")

    graphqlSchemaPaths =
        layout.projectDirectory
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

    // 型の名前をスキーマと同じにすると、リゾルバの中で「スキーマの型」と
    // 「自分のドメインの型」がどちらも AdminSession のような名前で並ぶ。
    // kake-bo と同じく Ql を付けて、生成物だと一目で分かるようにする
    modelNamePrefix = "Ql"
    generateImmutableModels = true
    generateBuilder = false

    // @Generated には生成した時刻が入る。付けると入力が同じでも中身が毎回変わり、
    // ビルドキャッシュが効かなくなる。生成物は git に入れないので目印も要らない
    addGeneratedAnnotation = false

    // @lazy を付けたフィールドだけリゾルバを作る。付けないフィールドは
    // 親のモデルが持っている値がそのまま返る
    fieldsWithResolvers = setOf("@lazy")
    generateParameterizedFieldsResolvers = true

    // 操作ごとの細かいインタフェースは作らない。Query / Mutation それぞれ 1 つと、
    // @lazy を付けた型ごとのリゾルバだけになる。
    //
    // 作らせると Query.admin から AdminQueryResolver という名前が生まれ、
    // AdminQuery 型のリゾルバと衝突して codegen が
    // FileAlreadyExistsException で落ちる
    apiInterfaceStrategy = ApiInterfaceStrategy.DO_NOT_GENERATE

    // 例外で返さず、DataFetcherResult に詰めて返す
    apiReturnType = "java.util.concurrent.CompletionStage<graphql.execution.DataFetcherResult<{{TYPE}}>>"
    generateApisWithThrowsException = false

    // ApplicationCall は GraphQLContext から取るので、リゾルバの引数に環境が要る
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

// ktlint のタスクは Kotlin のソースセットを入力として見るので、生成先のディレクトリも
// 入力に入る。lint の対象からは root の build.gradle.kts で外してあるが、
// 入力として見る以上は生成より後に走らせないと Gradle が構成の誤りとして落とす
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn("graphqlCodegen")
}
