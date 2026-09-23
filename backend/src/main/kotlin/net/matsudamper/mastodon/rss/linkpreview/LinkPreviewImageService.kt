package net.matsudamper.mastodon.rss.linkpreview

import java.time.Duration
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.note.NoteStore

/**
 * リンク先の OGP 画像を、見に来たときに取得元から取って返す。
 *
 * 中身は保存しない。取り直しの頻度は、返す `Cache-Control` を見て前段の CDN と
 * ブラウザが決める。
 *
 * 取りに行く先はリンク先のページが名乗った URL で、こちらの管理者が決めた値ではない。
 * このエンドポイントは無認証なので、[IconFetchService] を通して内側を叩けない
 * ことと、画像でないものを配らないことを確かめる。
 */
class LinkPreviewImageService(
    private val notes: NoteStore,
    private val previews: LinkPreviewService,
    private val fetcher: IconFetchService,
) {
    /**
     * 投稿と、本文の何番目のリンクかと、画面が URL に付けている版で引く。
     * 投稿が無いか、リンクが無いか、画像を名乗っていないか、版が合わないか、
     * 取ってこられなければ null。
     *
     * @param version [LinkPreviewImageUrls.version] の値
     */
    suspend fun find(
        notePublicId: String,
        linkIndex: Int,
        version: String,
    ): LinkPreviewImage? {
        // 記録にある投稿のリンクのものしか返さない。こうしておかないと、任意の URL の中身を
        // こちらのドメインから配る口になる
        val note = notes.find(PublicNoteId(notePublicId)) ?: return null
        val link = previews.links(note.contentHtml).getOrNull(linkIndex) ?: return null
        val sourceUrl = previews.preview(link).imageUrl ?: return null

        // 版が合わなければ取りに行かない。この口は無認証なので、版を変えながら
        // 呼ぶだけで前段のキャッシュを外し、そのたびに取得元へ取りに行かせられる
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
 * @param freshFor 取得元が言う、取り直さなくてよい時間。言っていなければ null
 */
class LinkPreviewImage(
    val bytes: ByteArray,
    val contentType: ContentType,
    val freshFor: Duration?,
)
