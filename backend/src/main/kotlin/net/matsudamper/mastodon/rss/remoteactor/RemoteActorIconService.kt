package net.matsudamper.mastodon.rss.remoteactor

import kotlin.time.Duration
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.image.RemoteImageFetchService
import net.matsudamper.mastodon.rss.repository.FollowerRepository

/**
 * フォロワーのアイコンを、見に来たときに配信元から取って返す。
 *
 * 中身は保存しない。フォロワーは何人になるか読めないので、全員分の画像を
 * 抱えると置き場が読めなくなる。取り直しの頻度は、返す `Cache-Control` を見て
 * 前段の CDN とブラウザが決める。
 *
 * 取りに行く先は相手のサーバーが名乗った URL で、こちらの管理者が決めた値ではない。
 * このエンドポイントは無認証なので、[RemoteImageFetchService] を通して内側を叩けない
 * ことと、画像でないものを配らないことを確かめる。
 */
class RemoteActorIconService(
    private val followers: FollowerRepository,
    private val fetcher: RemoteImageFetchService,
) {
    /**
     * アクター文書の URL と、画面が URL に付けている版で引く。
     * フォロワーとして記録が無いか、アイコンを名乗っていないか、版が合わないか、
     * 取ってこられなければ null。
     *
     * @param version [ActorUrls.iconVersion] の値。相手がいま名乗っている
     *   取得元から決まる値と一致するものだけを受ける
     */
    suspend fun find(
        actorUri: String,
        version: String,
    ): RemoteActorIcon? {
        // 記録にある相手のものしか返さない。こうしておかないと、任意の URL の中身を
        // こちらのドメインから配る口になる
        val sourceUrl = followers.findIconUrl(actorUri) ?: return null

        // 版が合わなければ取りに行かない。この口は無認証なので、版を変えながら
        // 呼ぶだけで前段のキャッシュを外し、そのたびに配信元へ取りに行かせられる。
        // 合う版は 1 つしか無いので、前段に載る URL も 1 つに絞れる
        if (version != ActorUrls.iconVersion(sourceUrl)) return null

        return when (val fetched = fetcher.fetch(sourceUrl)) {
            is RemoteImageFetchService.FetchResult.Success -> RemoteActorIcon(
                bytes = fetched.bytes,
                contentType = fetched.imageType.contentType,
                freshFor = fetched.freshFor,
            )

            RemoteImageFetchService.FetchResult.Failure -> null
        }
    }
}

/**
 * 中継するアイコン 1 つ。
 *
 * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
 */
class RemoteActorIcon(
    val bytes: ByteArray,
    val contentType: ContentType,
    val freshFor: Duration?,
)
