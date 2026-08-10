package net.matsudamper.mastodon.rss.gradle

import java.io.File

/**
 * マイグレーション SQL をバージョンの昇順に並べる。
 *
 * ファイル名の連番が 0 埋めなので名前順で並ぶ。実行時に読む側
 * (`MigrationLoader`) は連番を数値として読むが、こちらは順番さえ合えばよい。
 */
internal fun Iterable<File>.migrationsInOrder(): List<File> =
    filter { it.isFile && it.name.endsWith(".sql") }
        .sortedBy { it.name }
