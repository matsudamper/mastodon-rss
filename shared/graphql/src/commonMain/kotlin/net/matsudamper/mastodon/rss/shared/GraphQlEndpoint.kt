package net.matsudamper.mastodon.rss.shared

/**
 * GraphQL の口。
 *
 * エンドポイントは 1 つで、管理用とそれ以外はフィールドで分ける
 * （管理用は `Query.admin` / `Mutation.admin` の下）。認可もエンドポイントではなく
 * フィールドごとに見る。パスを両方で持つと綴りがずれたときに 404 になるので、
 * サーバーが登録するパスとクライアントが叩くパスをここ 1 つにする。
 *
 * スキーマ自体は `:shared:graphql:schema` にある。`:backend` は実行時にリソースとして読み、
 * `:frontend` は Apollo のコード生成の入力としてビルド時に読む。
 */
object GraphQlEndpoint {
    /** `POST` で叩く。`GET` は受けない */
    const val PATH: String = "/graphql"

    /** スキーマのリソース上のパス。`:backend` がクラスパスから読むときに使う */
    const val SCHEMA_RESOURCE: String = "graphql/schema.graphqls"
}
