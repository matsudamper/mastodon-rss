package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/** [DatabaseCodegenPlugin] の設定 */
interface DatabaseCodegenExtension {
    /**
     * スキーマの唯一の定義になる SQL ファイル。既定は `src/main/resources/db/schema.sql`。
     * [DumpSchemaTask] の書き出し先で、codegen の入力でもある
     */
    val schemaFile: RegularFileProperty

    /** 生成コードのパッケージ名 */
    val packageName: Property<String>

    /**
     * 生成の対象から外すテーブルの正規表現。
     *
     * 既定では SQLite 自身の内部表と、旧マイグレーション機構の管理表
     * `schema_version` を外す。後者は、切り替え前の開発用 DB に残っていても
     * dump と生成物に紛れ込まないようにするため。
     */
    val excludes: Property<String>

    /**
     * リフレクション設定を置く `META-INF/native-image/` の下のパス。
     * 既定は `<group>/<プロジェクト名>-jooq`
     */
    val nativeImageMetadataPath: Property<String>
}
