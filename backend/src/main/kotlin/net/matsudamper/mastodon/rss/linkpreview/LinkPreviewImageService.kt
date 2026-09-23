package net.matsudamper.mastodon.rss.linkpreview

import java.time.Duration
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.feed.IconFetchService

/**
 * リンク先の OGP 画像を、見に来たときに配信元から取って返す。
 *
 * 中身は保存しない。取り直しの頻度は、返す `Cache-Control` を見て
 * 前段の CDN とブラウザが決める。
 *
 * このエンドポイントは無認証なので、[IconFetchService] を通して内側を叩けない
 * ことと、画像でないものを配らないことを確かめる。
 */
class LinkPreviewImageService(
    private val linkPreviews: LinkPreviewService,
    private val fetcher: IconFetchService,
) {
    /**
     * リンク先のページの URL と、画面が URL に付けている版で引く。
     * そのページの OGP をまだ取っていないか、画像が無いか、版が合わないか、
     * 取ってこられなければ null。
     *
     * @param version [LinkPreviewImageUrls.version] の値
     */
    suspend fun find(
        pageUrl: String,
        version: String,
    ): LinkPreviewImage? {
        // 投稿の OGP として取ったものしか返さない。こうしておかないと、任意の URL の中身を
        // こちらのドメインから配る口になる
        val sourceUrl = linkPreviews.cachedImageUrl(pageUrl) ?: return null

        // 版が合わなければ取りに行かない。版を変えながら呼ぶだけで前段のキャッシュを外し、
        // そのたびに配信元へ取りに行かせられる
        if (version != LinkPreviewImageUrls.version(sourceUrl)) return null

        val fetched = fetcher.fetch(sourceUrl) as? IconFetchService.FetchResult.Success ?: return null

        return LinkPreviewImage(
            bytes = fetched.bytes,
            contentType = fetched.imageType.contentType,
            freshFor = fetched.freshFor,
        )
    }
}

/**
 * 中継する画像 1 つ。
 *
 * @param freshFor 配信元が言う、取り直さなくてよい時間。言っていなければ null
 */
class LinkPreviewImage(
    val bytes: ByteArray,
    val contentType: ContentType,
    val freshFor: Duration?,
)
