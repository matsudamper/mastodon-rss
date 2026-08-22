plugins {
    alias(libs.plugins.kotlin.jvm)
    id("mastodon-rss.database-codegen")
}

dependencies {
    // implementation にすることで、JDBC と jOOQ の型が :backend の compile classpath に漏れない。
    // :backend からは repository パッケージの interface だけが見える状態を保つ
    implementation(libs.sqlite.jdbc)
    implementation(libs.jooq)
    implementation(libs.opentelemetry.jdbc)

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
