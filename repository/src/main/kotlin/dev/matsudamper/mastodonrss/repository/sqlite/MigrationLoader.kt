package dev.matsudamper.mastodonrss.repository.sqlite

/**
 * リソースからマイグレーションを読み込む。
 *
 * jar 内のディレクトリ走査は native-image では動かないことがあるため、
 * ビルド時に生成した `db/migration/index` を読んでファイル名を得る。
 */
internal object MigrationLoader {
    private const val DIRECTORY = "db/migration"
    private const val INDEX_RESOURCE = "$DIRECTORY/index"
    private val FILE_NAME_PATTERN = Regex("""^V(\d+)__(.+)\.sql$""")

    /** バージョンの昇順で返す */
    fun load(): List<Migration> {
        val index = readResource(INDEX_RESOURCE)
            ?: error(
                "マイグレーションの一覧 ($INDEX_RESOURCE) がリソースに含まれていない。" +
                    "native バイナリの場合は resource-config.json の登録漏れの可能性がある",
            )

        val migrations = index.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { fileName -> loadMigration(fileName) }
            .sortedBy { it.version }
            .toList()

        val duplicated = migrations.groupBy { it.version }.filterValues { it.size > 1 }
        check(duplicated.isEmpty()) {
            val detail = duplicated.values.joinToString(" / ") { group ->
                group.joinToString(", ") { it.fileName }
            }
            "マイグレーションのバージョンが重複している: $detail"
        }

        return migrations
    }

    private fun loadMigration(fileName: String): Migration {
        val matched = FILE_NAME_PATTERN.matchEntire(fileName)
            ?: error("マイグレーションのファイル名が V001__name.sql の形式になっていない: $fileName")

        val path = "$DIRECTORY/$fileName"
        val sql = readResource(path)
            ?: error("マイグレーション ($path) がリソースに含まれていない")

        return Migration(
            // 先頭の 0 は 8 進数として解釈されないよう toInt() で読む
            version = matched.groupValues[1].toInt(),
            name = matched.groupValues[2],
            fileName = fileName,
            sql = sql,
        )
    }

    private fun readResource(path: String): String? {
        val classLoader = MigrationLoader::class.java.classLoader
        return classLoader.getResourceAsStream(path)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }
}
