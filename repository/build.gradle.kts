plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // implementation にすることで、JDBC の型が :backend の compile classpath に漏れない。
    // :backend からは repository パッケージの interface だけが見える状態を保つ
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
