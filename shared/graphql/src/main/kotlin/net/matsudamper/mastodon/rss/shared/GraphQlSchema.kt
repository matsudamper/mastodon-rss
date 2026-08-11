package net.matsudamper.mastodon.rss.shared

/**
 * GraphQL のスキーマの在り処。`:backend` はクラスパスから、`:frontend` は
 * Apollo のコード生成の入力としてファイルから読む。
 */
object GraphQlSchema {
    /** クラスパス上の位置。native バイナリでは resource-config.json にも同じものを書く */
    const val RESOURCE_PATH: String = "graphql/schema.graphqls"
}
