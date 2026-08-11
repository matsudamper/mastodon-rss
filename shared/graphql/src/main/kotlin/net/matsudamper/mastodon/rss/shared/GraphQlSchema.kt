package net.matsudamper.mastodon.rss.shared

/**
 * GraphQL のスキーマの在り処。
 *
 * 実体は同じモジュールの `src/main/resources/graphql/schema.graphqls`。
 * `:backend` は起動時にクラスパスから読み、`:frontend` は Apollo の
 * コード生成の入力としてビルド時にファイルを読む。
 *
 * ここに置いてあるのは読み出しに要る位置だけ。どの URL で受けるかは
 * サーバーの都合で、スキーマとは関係が無いので持たない。
 */
object GraphQlSchema {
    /** クラスパス上の位置。native バイナリでは resource-config.json にも同じものを書く */
    const val RESOURCE_PATH: String = "graphql/schema.graphqls"
}
