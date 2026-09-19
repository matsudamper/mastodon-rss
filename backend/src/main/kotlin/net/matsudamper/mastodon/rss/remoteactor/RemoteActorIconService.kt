package net.matsudamper.mastodon.rss.remoteactor

import java.time.Duration
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.FollowerRepository

/**
 * フォロワーのアイコンを、見に来たときに配信元から取って返す。
 *
 * 中身は保存しない。フォロワーは何人になるか読めないので、全員分の画像を
 * 抱えると置き場が読めなくなる。取り直しの頻度は、返す `Cache-Control` を見て
 * 前段の CDN とブラウザが決める。
 *
 * 取りに行く先は相手のサーバーが名乗った URL で、こちらの管理者が決めた値ではない。
 * このエンドポイントは無認証なので、[IconFetchService] を通して内側を叩けない
 * ことと、画像でないものを配らないことを確かめる。
 */
class RemoteActorIconService(
    private val followers: FollowerRepository,
    private val fetcher: IconFetchService,
) {
    /**
     * アクター文書の URL で引く。フォロワーとして記録が無いか、アイコンを
     * 名乗っていないか、取ってこられなければ null。
     */
    suspend fun find(actorUri: String): RemoteActorIcon? {
        // 記録にある相手のものしか返さない。こうしておかないと、任意の URL の中身を
        // こちらのドメインから配る口になる
        val sourceUrl = followers.findIconUrl(actorUri) ?: return null

        val fetched = fetcher.fetch(sourceUrl) as? IconFetchService.FetchResult.Success ?: return null

        return RemoteActorIcon(
            bytes = fetched.bytes,
            contentType = fetched.imageType.contentType,
            // 画面が URL に付けているのと同じ値。一致していれば、その URL は
            // 相手がいま名乗っているアイコンを指している
            version = ActorUrls.iconVersion(sourceUrl),
            freshFor = fetched.freshFor,
        )
    }
}

/**
 * 中継するアイコン 1 つ。
 *
 * @param version 取得元の URL から決まる値。要求された値と一致すれば、
 *   同じ URL で中身が入れ替わらないと分かる
 * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
 */
class RemoteActorIcon(
    val bytes: ByteArray,
    val contentType: ContentType,
    val version: String,
    val freshFor: Duration?,
)
