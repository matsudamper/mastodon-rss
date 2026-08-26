package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jooq.codegen.gradle.CodegenPlugin
import org.jooq.codegen.gradle.CodegenPluginExtension
import org.jooq.codegen.gradle.CodegenTask

/**
 * コミットされた `schema.sql` を入力に、jOOQ の生成コードとリフレクション設定を作る。
 * `schema.sql` 自体は `dumpSchema` が開発用 DB から書き出す。
 *
 * codegen 本体は公式の `org.jooq.jooq-codegen-gradle` プラグインに任せる。
 * このプラグインは `GenerationTool` を Gradle デーモンの中で直接呼ぶので、
 * jOOQ 自体がデーモンのクラスパスに乗る。別プロセスに切り出す手もあるが、
 * その分の実装コストに見合わないので許容する
 */
class DatabaseCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create<DatabaseCodegenExtension>(EXTENSION_NAME)
        extension.schemaFile.convention(
            target.layout.projectDirectory.file("src/main/resources/db/schema.sql"),
        )
        extension.excludes.convention("schema_version|sqlite_.*")
        extension.nativeImageMetadataPath.convention(
            target.provider { "${target.group}/${target.name}-jooq" },
        )

        target.pluginManager.apply(CodegenPlugin::class.java)

        // codegen 用の JDBC ドライバは使う側がこの configuration に入れる。
        // jOOQ 自体は org.jooq.jooq-codegen-gradle プラグインが持っているので入れる必要が無い
        val codegenConfiguration = target.configurations.getByName(CODEGEN_CONFIGURATION)

        target.pluginManager.withPlugin("java") {
            target.tasks.register<DumpSchemaTask>("dumpSchema") {
                description = "開発用 DB (-PdevDb=絶対パス) から schema.sql を書き出す"
                devDatabasePath.set(target.providers.gradleProperty("devDb"))
                driverClasspath.from(codegenConfiguration)
                excludes.set(extension.excludes)
                schemaFile.set(extension.schemaFile)
                workDatabase.set(target.layout.buildDirectory.file("dump/normalize.db"))
            }

            val schemaDatabaseFile = target.layout.buildDirectory.file("jooq/schema.db")
            val buildJooqSchema =
                target.tasks.register<BuildSchemaDatabaseTask>("buildJooqSchema") {
                    description = "codegen の入力にする一時 SQLite を作る"
                    schemaSql.set(extension.schemaFile)
                    driverClasspath.from(codegenConfiguration)
                    databaseFile.set(schemaDatabaseFile)
                }

            val generatedDirectory = target.layout.buildDirectory.dir("generated/jooq")

            // jooq {} の設定は呼んだ瞬間に評価される。packageName など使う側の
            // build.gradle.kts が設定する値が要るので、その評価が終わる afterEvaluate まで待つ
            target.afterEvaluate {
                val jooq = target.extensions.getByType<CodegenPluginExtension>()
                jooq.configuration {
                    jdbc {
                        driver = "org.sqlite.JDBC"
                        url = "jdbc:sqlite:${schemaDatabaseFile.get().asFile.absolutePath}"
                    }
                    generator {
                        database {
                            name = "org.jooq.meta.sqlite.SQLiteDatabase"
                            includes = ".*"
                            excludes = extension.excludes.get()
                            forcedTypes {
                                forcedType {
                                    // SQLite の INTEGER は最大 8 バイトで、rowid も int64 まで入る。
                                    // jOOQ は宣言名を SQL 標準の INTEGER（4 バイト）として読むので、
                                    // そのままだと 2^31 を超えた値が黙って別の値になる
                                    name = "BIGINT"
                                    includeTypes = "INTEGER"
                                }
                            }
                        }
                        generate {
                            // TEXT に入れた ISO 8601 を java.time で受けたい
                            javaTimeTypes = true
                            // POJO と DAO はリフレクション経由になるので作らない。native-image で効いてくる
                            pojos = false
                            daos = false
                            deprecated = false
                        }
                        target {
                            packageName = extension.packageName.get()
                            directory = generatedDirectory.get().asFile.absolutePath
                            // テーブルや列を消したときに、古い型がそのまま残らないように作り直す
                            clean = true
                        }
                    }
                }
            }

            val generateJooq =
                target.tasks.named<CodegenTask>("jooqCodegen") {
                    dependsOn(buildJooqSchema)
                    // Configuration の中身（JDBC URL の文字列）だけでは schema.sql の変更を
                    // 検知できないので、DB ファイル自体も input として追跡させる
                    inputs
                        .file(schemaDatabaseFile)
                        .withPropertyName("schemaDatabase")
                        .withPathSensitivity(PathSensitivity.NONE)
                }

            // 生成物を main のソースとして扱う。TaskProvider#map 経由で参照することで、
            // このタスクを実際に実現(configure)しなくても "jooqCodegen に依存する" ことが
            // コンパイルタスク側に伝わる。afterEvaluate まで設定を遅らせている都合上、
            // プラグイン側の自動配線（configuration の即時評価を前提にしている）は使えない
            target.extensions.getByType<SourceSetContainer>().named("main") {
                java.srcDir(generateJooq.map { it.outputDirectory })
            }

            val generateJooqReflectConfig =
                target.tasks.register<GenerateJooqReflectConfigTask>("generateJooqReflectConfig") {
                    description = "生成コードの native-image 向けリフレクション設定を作る"
                    dependsOn(generateJooq)
                    generatedSources.set(generatedDirectory)
                    metadataPath.set(extension.nativeImageMetadataPath)
                    outputDirectory.set(target.layout.buildDirectory.dir("generated/jooqNativeImage"))
                }

            target.extensions.getByType<SourceSetContainer>().named("main") {
                resources.srcDir(generateJooqReflectConfig.flatMap { it.outputDirectory })
            }
        }
    }

    private companion object {
        const val EXTENSION_NAME = "databaseCodegen"
        const val CODEGEN_CONFIGURATION = "jooqCodegen"
    }
}
