package net.matsudamper.mastodon.rss.gradle

import java.io.File
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.Driver
import java.util.Properties

/**
 * タスク専用の classpath から SQLite の JDBC ドライバを読み込んで接続する。
 *
 * ドライバをこのビルド自身の classpath に載せないための仕組み。
 * `DriverManager` は使わない。ドライバの static イニシャライザが登録する分は
 * 触りようがないが、こちらから登録経由で解決するとデーモンに classloader が
 * 残る経路が増えるだけなので、直接インスタンス化して `connect` する。
 */
internal fun <T> withSqliteConnection(
    driverClasspath: Iterable<File>,
    databaseFile: File,
    block: (Connection) -> T,
): T {
    val loader =
        URLClassLoader(
            driverClasspath.map { it.toURI().toURL() }.toTypedArray(),
            // java.sql.* は JDK 側が持つので、ここで読んだドライバを Driver として扱える
            ClassLoader.getPlatformClassLoader(),
        )

    return loader.use {
        val driver =
            loader
                .loadClass("org.sqlite.JDBC")
                .getDeclaredConstructor()
                .newInstance() as Driver

        driver.connect("jdbc:sqlite:$databaseFile", Properties()).use(block)
    }
}
