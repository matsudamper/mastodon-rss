plugins {
    alias(libs.plugins.kotlin.jvm)
    // マイグレーション SQL から実行時の一覧・jOOQ の型・リフレクション設定を作る。
    // 中身は build-logic/src/main/kotlin/.../DatabaseCodegenPlugin.kt
    id("mastodon-rss.database-codegen")
}

dependencies {
    // implementation にすることで、JDBC と jOOQ の型が :backend の compile classpath に漏れない。
    // :backend からは repository パッケージの interface だけが見える状態を保つ
    implementation(libs.sqlite.jdbc)
    implementation(libs.jooq)

    // codegen は別プロセスで動くので、実行時の classpath とは分ける。
    // 実物の SQLite に接続してスキーマを読むのでドライバも要る
    jooqCodegen(libs.jooq.codegen)
    jooqCodegen(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
}

databaseCodegen {
    packageName.set("net.matsudamper.mastodon.rss.repository.jooq")
    nativeImageMetadataPath.set("net.matsudamper/mastodon-rss-repository-jooq")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
