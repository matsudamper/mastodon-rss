package net.matsudamper.mastodon.rss.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.StringListSerializer

/**
 * 配信する投稿。Mastodon のタイムラインに 1 件として並ぶもの。
 *
 * `to` に公開を表す URI を入れると、フォロワーのホームタイムラインに出るうえ、
 * 相手のインスタンスの公開タイムラインからも見える。`cc` にフォロワーの
 * コレクションを入れるのは Mastodon の慣習で、これが無いと配信されても
 * ホームタイムラインに並ばないことがある。
 *
 * `@context` は単体で返すときだけ入れる。[CreateNote] に包んで送るときは
 * 外側が持っているので、中で重ねると同じものを 2 回書くことになる。
 */
@Serializable
data class Note(
    @SerialName("@context")
    val context: List<String>? = null,
    /**
     * この投稿の URL。相手はここをパーマリンクとして引きに来る
     */
    val id: String,
    val type: String = TYPE,
    /**
     * 投稿したアクターの id
     */
    val attributedTo: String,
    /**
     * 本文の HTML。許可されていないタグは相手側で落とされる
     */
    val content: String,
    /**
     * ISO 8601。相手のタイムラインでの並び順になる
     */
    val published: String,
    val to: List<String>,
    val cc: List<String>,
    /**
     * Mastodon がパーマリンクの別名として引くことがある
     */
    val atomUri: String? = null,
    val sensitive: Boolean = false,
    /**
     * プロフィールや検索から開くリンク。無ければ [id] が使われる
     */
    val url: String? = null,
) {
    companion object {
        const val TYPE: String = "Note"
    }
}

/**
 * [Note] を包んで配る `Create`。
 *
 * `to` と `cc` は中の [Note] と同じものを入れる。片方だけに入れると、
 * 実装によって配信先の判断が変わる。
 *
 * [OutgoingActivity] と分けているのは、あちらの `object` が
 * [net.matsudamper.mastodon.rss.activitypub.LinkOrObject] で、受け取ったものを
 * そのまま返す `Accept` のための形だから。こちらは中身をこちらが組み立てる。
 */
@Serializable
data class CreateNote(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OutgoingActivity.DEFAULT_CONTEXT,
    /**
     * このアクティビティ自身の id。相手側の重複判定に使われる
     */
    val id: String,
    val type: String = TYPE,
    val actor: String,
    val published: String,
    val to: List<String>,
    val cc: List<String>,
    @SerialName("object")
    val target: Note,
) {
    companion object {
        const val TYPE: String = "Create"
    }
}

/**
 * 消した投稿を表す `Tombstone`（墓標）。
 *
 * 消えたことだけを表すので本文は持たない。id だけを送る実装もあるが、
 * 型を付けておくと相手側が何を消せばよいか一意に決まる。
 *
 * 名前は ActivityStreams 2.0 の語彙で、`type` に出る値もこれ。別の語にすると相手が削除と分からない。
 */
@Serializable
data class Tombstone(
    /**
     * 消した投稿を配ったときの [Note.id]。投稿の URL がそのまま入る。
     *
     * 相手はこれを鍵にして受け取り済みの投稿を引き当てるので、
     * 1 文字でも違うと何も消えない
     */
    val id: String,
) {
    /**
     * 相手はこの値を見て、消えたオブジェクトだと判断する。`Tombstone` 以外は入らない
     */
    val type: String = "Tombstone"
}

/**
 * 投稿を消したことを伝える `Delete`。
 *
 * こちらで消しても相手のサーバーには残る。これを配らない限り、
 * フォロワーのタイムラインからは消えない。
 *
 * `to` と `cc` は元の投稿と同じものを入れる。宛先を見て配るかどうかを決める実装があり、
 * 元の投稿と揃っていないと消したことが無視されてタイムラインに残る。
 */
@Serializable
data class DeleteNote(
    @SerialName("@context")
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = OutgoingActivity.DEFAULT_CONTEXT,
    /**
     * このアクティビティ自身の id。消す投稿の id ではない。
     *
     * 相手の重複判定に使われるので、消した投稿の id と同じにしてはいけない
     */
    val id: String,
    val actor: String,
    val to: List<String>,
    val cc: List<String>,
    @SerialName("object")
    val target: Tombstone,
) {
    /**
     * 相手はこの値を見て削除だと判断する。`Delete` 以外は入らない
     */
    val type: String = "Delete"
}

/**
 * 誰でも見られることを表す宛先。
 *
 * `to` に入れると公開投稿、`cc` に入れると未収載（フォロワーには届くが
 * 公開タイムラインには出ない）になる。
 */
const val PUBLIC_AUDIENCE: String = "https://www.w3.org/ns/activitystreams#Public"
