import java.io.File
import java.net.URLClassLoader
import java.sql.Driver
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // implementation にすることで、JDBC と jOOQ の型が :backend の compile classpath に漏れない。
    // :backend からは repository パッケージの interface だけが見える状態を保つ
    implementation(libs.sqlite.jdbc)
    implementation(libs.jooq)

    testImplementation(libs.kotlin.test)
}

/**
 * jOOQ の codegen を回すためだけの classpath。
 *
 * `nu.studer.jooq` プラグインは使わない。あれが省いてくれるのは下の
 * [generateJooq] 相当のタスク定義だけで、代わりに Gradle と jOOQ の
 * バージョンの組み合わせに追従する必要が出る。ここでやることは
 * 「XML を書いて GenerationTool を叩く」だけなので、素の [JavaExec] の方が
 * 何が起きているか読める。
 *
 * codegen は実行時には要らないので、この configuration は
 * どの sourceSet の classpath にも入れない。
 */
val jooqCodegen: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    jooqCodegen(libs.jooq.codegen)
    // codegen は実物の SQLite に接続してスキーマを読む
    jooqCodegen(libs.sqlite.jdbc)
}

val migrationDirectory = layout.projectDirectory.dir("src/main/resources/db/migration")

/** codegen の入力にするだけの一時 DB。実行時の DB とは無関係 */
val jooqSchemaDatabase = layout.buildDirectory.file("jooq/schema.db")
val jooqConfigFile = layout.buildDirectory.file("jooq/codegen.xml")
val jooqOutputDirectory = layout.buildDirectory.dir("generated/jooq")
val jooqPackage = "net.matsudamper.mastodon.rss.repository.jooq"

/** バージョン昇順のマイグレーション SQL。ファイル名の連番が 0 埋めなので名前順で並ぶ */
fun migrationFiles(directory: File): List<File> =
    directory
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".sql") }
        .sortedBy { it.name }

/**
 * 下のタスクの入力。ディレクトリごとではなく SQL だけを見る。
 *
 * 同じ場所に説明の README.md を置いてあり、ディレクトリを入力にすると
 * それを直しただけでスキーマの作り直しと codegen が走る。
 */
val migrationSql = fileTree(migrationDirectory) { include("*.sql") }

/**
 * マイグレーション SQL のファイル名一覧を書いた `db/migration/index` を生成する。
 *
 * jar 内のディレクトリ走査は native-image では動かないことがあるため、
 * 実行時はこの一覧を読んでファイルを開く。手で管理すると SQL を足したときに
 * 更新を忘れて「JVM では動くが native では動かない」状態になるので生成する。
 */
val generateMigrationIndex by tasks.registering {
    val migrationDir = migrationDirectory.asFile
    val outputDir = layout.buildDirectory.dir("generated/migrationIndex")

    inputs.files(migrationSql).withPropertyName("migrations")
    outputs.dir(outputDir).withPropertyName("index")

    doLast {
        val fileNames = migrationFiles(migrationDir).map { it.name }

        val indexFile = outputDir.get().asFile.resolve("db/migration/index")
        indexFile.parentFile.mkdirs()
        indexFile.writeText(fileNames.joinToString(separator = "\n", postfix = "\n"))
    }
}

/**
 * codegen の入力になる SQLite ファイルを、マイグレーション SQL を順に適用して作る。
 *
 * jOOQ には SQL スクリプトを直接読む `DDLDatabase` もあるが、そちらは jOOQ 自身の
 * パーサで DDL を解釈する。SQLite の型の扱い（型親和性）まで同じになる保証が無いので、
 * 実物の SQLite に流し込んで、そこから読ませる。生成される型が実行時の DB と
 * ずれないことの方が、パイプラインが 1 段減ることより大事。
 */
val buildJooqSchema by tasks.registering {
    val migrationDir = migrationDirectory.asFile
    val output = jooqSchemaDatabase
    val driverClasspath = jooqCodegen

    inputs.files(migrationSql).withPropertyName("migrations")
    inputs.files(driverClasspath).withPropertyName("driver")
    outputs.file(output).withPropertyName("schema")

    doLast {
        val databaseFile = output.get().asFile
        databaseFile.parentFile.mkdirs()
        // 作り直さないと、消したはずのテーブルが前回の DB に残って生成物に出続ける
        databaseFile.delete()

        val loader =
            URLClassLoader(
                driverClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
                // java.sql.* は JDK 側が持つので、ここで読んだドライバを Driver として扱える
                ClassLoader.getPlatformClassLoader(),
            )

        loader.use {
            val driver =
                loader
                    .loadClass("org.sqlite.JDBC")
                    .getDeclaredConstructor()
                    .newInstance() as Driver

            driver.connect("jdbc:sqlite:$databaseFile", Properties()).use { connection ->
                connection.createStatement().use { statement ->
                    migrationFiles(migrationDir).forEach { file ->
                        // sqlite-jdbc は 1 回の executeUpdate で複数の文を実行する。
                        // 実行時の MigrationRunner は自前で文に切ってから流すので経路が違うが、
                        // ここで欲しいのは codegen に読ませるスキーマだけなので合わせなくてよい
                        statement.executeUpdate(file.readText())
                    }
                }
            }
        }
    }
}

/**
 * jOOQ の生成コードを作る。
 *
 * 生成するのは Kotlin ではなく Java。ktlint を全モジュールに掛けているので、
 * Kotlin で生成すると `build/` の中の生成物まで整形の対象になる。
 */
val generateJooq by tasks.registering(JavaExec::class) {
    dependsOn(buildJooqSchema)

    val schema = jooqSchemaDatabase
    val config = jooqConfigFile
    val output = jooqOutputDirectory

    inputs.file(schema).withPropertyName("schema")
    outputs.dir(output).withPropertyName("generated")

    classpath = jooqCodegen
    mainClass.set("org.jooq.codegen.GenerationTool")

    // 何もしないと起動のたびにロゴと「豆知識」が標準出力に出る
    systemProperty("org.jooq.no-logo", "true")
    systemProperty("org.jooq.no-tips", "true")

    doFirst {
        val outputDir = output.get().asFile
        // テーブルや列を消したときに、古い型がそのまま残らないように作り直す
        outputDir.deleteRecursively()

        val configFile = config.get().asFile
        configFile.parentFile.mkdirs()
        configFile.writeText(
            codegenConfigXml(
                schemaDatabase = schema.get().asFile,
                outputDirectory = outputDir,
            ),
        )

        args = listOf(configFile.absolutePath)
    }
}

fun codegenConfigXml(
    schemaDatabase: File,
    outputDirectory: File,
): String =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
      <jdbc>
        <driver>org.sqlite.JDBC</driver>
        <url>jdbc:sqlite:${schemaDatabase.absolutePath}</url>
      </jdbc>
      <generator>
        <database>
          <name>org.jooq.meta.sqlite.SQLiteDatabase</name>
          <includes>.*</includes>
          <!--
            schema_version は MigrationRunner が自分で作って読み書きする管理表で、
            マイグレーション SQL には出てこない（＝この DB にも無い）。
            将来 SQL 側で作るようになっても jOOQ から触らせないよう名前で外しておく。
            sqlite_ 始まりは SQLite 自身の内部表
          -->
          <excludes>schema_version|sqlite_.*</excludes>
        </database>
        <generate>
          <!-- TEXT に入れた ISO 8601 を java.time で受けたい -->
          <javaTimeTypes>true</javaTimeTypes>
          <!-- POJO と DAO はリフレクション経由になるので作らない。native-image で効いてくる -->
          <pojos>false</pojos>
          <daos>false</daos>
          <deprecated>false</deprecated>
        </generate>
        <target>
          <packageName>$jooqPackage</packageName>
          <directory>${outputDirectory.absolutePath}</directory>
        </target>
      </generator>
    </configuration>
    """.trimIndent()

/**
 * 生成コードの native-image 向けリフレクション設定を作る。
 *
 * jOOQ はクエリを組み立てるときに、テーブルに対応する `Record` を
 * `getDeclaredConstructor().newInstance()` で作る。native-image は到達可能性を
 * 静的に解析するので、この経路は登録しないと実行時に落ちる。JVM では動いて
 * native バイナリでだけ失敗する形になり、テストでは掴めない。
 *
 * 手で書かない理由は 0-7 と同じで、テーブルを足したときに更新を忘れるため。
 * 生成物から作れば、忘れようがない。
 */
val generateJooqReflectConfig by tasks.registering {
    val generatedSources = jooqOutputDirectory
    val outputDir = layout.buildDirectory.dir("generated/jooqNativeImage")

    dependsOn(generateJooq)
    inputs.dir(generatedSources).withPropertyName("generated")
    outputs.dir(outputDir).withPropertyName("reflectConfig")

    doLast {
        val sourceRoot = generatedSources.get().asFile

        val recordClasses =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith("Record.java") }
                .map { file ->
                    file
                        .relativeTo(sourceRoot)
                        .path
                        .removeSuffix(".java")
                        .replace(File.separatorChar, '.')
                }.sorted()
                .toList()

        check(recordClasses.isNotEmpty()) {
            "jOOQ の生成物に Record が 1 つも無い。codegen が空振りしている可能性がある"
        }

        val entries =
            recordClasses.joinToString(separator = ",\n") { className ->
                """  { "name": "$className", "allDeclaredConstructors": true }"""
            }

        // 別ディレクトリにするのは、手で書いた方の resource-config.json と混ざらないようにするため。
        // native-image は META-INF/native-image の下を全部読む
        val configFile =
            outputDir
                .get()
                .asFile
                .resolve("META-INF/native-image/net.matsudamper/mastodon-rss-repository-jooq/reflect-config.json")
        configFile.parentFile.mkdirs()
        configFile.writeText("[\n$entries\n]\n")
    }
}

sourceSets.named("main") {
    resources.srcDir(generateMigrationIndex)
    resources.srcDir(generateJooqReflectConfig)
    // TaskProvider を渡すと、compileJava / compileKotlin が codegen に依存する。
    // 初回ビルドで生成物が無くて落ちることは無い
    java.srcDir(generateJooq)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
