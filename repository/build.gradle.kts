plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // implementation にすることで、JDBC の型が :backend の compile classpath に漏れない。
    // :backend からは repository パッケージの interface だけが見える状態を保つ
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
}

/**
 * マイグレーション SQL のファイル名一覧を書いた `db/migration/index` を生成する。
 *
 * jar 内のディレクトリ走査は native-image では動かないことがあるため、
 * 実行時はこの一覧を読んでファイルを開く。手で管理すると SQL を足したときに
 * 更新を忘れて「JVM では動くが native では動かない」状態になるので生成する。
 */
val generateMigrationIndex by tasks.registering {
    val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration").asFile
    val outputDir = layout.buildDirectory.dir("generated/migrationIndex")

    inputs.dir(migrationDir).withPropertyName("migrations")
    outputs.dir(outputDir).withPropertyName("index")

    doLast {
        val fileNames =
            migrationDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".sql") }
                .map { it.name }
                .sorted()

        val indexFile = outputDir.get().asFile.resolve("db/migration/index")
        indexFile.parentFile.mkdirs()
        indexFile.writeText(fileNames.joinToString(separator = "\n", postfix = "\n"))
    }
}

sourceSets.named("main") {
    resources.srcDir(generateMigrationIndex)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
