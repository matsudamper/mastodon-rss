package net.matsudamper.mastodon.rss.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** [DatabaseCodegenPlugin] の設定 */
interface DatabaseCodegenExtension {
    /** マイグレーション SQL の置き場所。既定は `src/main/resources/db/migration` */
    val migrationDirectory: DirectoryProperty

    /** 生成コードのパッケージ名 */
    val packageName: Property<String>

    /**
     * 生成の対象から外すテーブルの正規表現。
     *
     * 既定では `schema_version` と SQLite 自身の内部表を外す。前者は
     * マイグレーションの適用を記録する管理表で、生成すると適用の記録を
     * DSL 経由で書き換えられるようになってしまう。
     */
    val excludes: Property<String>

    /**
     * リフレクション設定を置く `META-INF/native-image/` の下のパス。
     * 既定は `<group>/<プロジェクト名>-jooq`
     */
    val nativeImageMetadataPath: Property<String>
}
