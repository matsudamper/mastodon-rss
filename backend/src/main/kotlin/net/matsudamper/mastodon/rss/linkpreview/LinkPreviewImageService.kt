package net.matsudamper.mastodon.rss.linkpreview

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteWriteChannel
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.mastodon.rss.image.RemoteImageFetchService

/**
 * リンク先の OGP 画像を、見に来たときに取得元から取って返す。
 *
 * 中身は保存しない。取り直しの頻度は、返す `Cache-Control` を見て前段の CDN と
 * ブラウザが決める。
 *
 * 取りに行く先はリンク先のページが名乗った URL で、こちらの管理者が決めた値ではない。
 * このエンドポイントは無認証なので、[RemoteImageFetchService] を通して内側を叩けない
 * ことと、画像でないものを配らないことを確かめる。
 */
class LinkPreviewImageService(
    private val notes: NoteStore,
    private val previews: LinkPreviewService,
    private val fetcher: RemoteImageFetchService,
) {
    /**
     * 投稿と、本文の何番目のリンクかと、画面が URL に付けている版で引く。
     * 投稿が無いか、リンクが無いか、画像を名乗っていないか、版が合わないか、
     * 取ってこられなければ [respond] を呼ばずに null。
     *
     * @param version [LinkPreviewImageUrls.version] の値
     */
    suspend fun <T> relay(
        notePublicId: String,
        linkIndex: Int,
        version: String,
        respond: suspend (LinkPreviewImage) -> T,
    ): T? {
        // 記録にある投稿のリンクのものしか返さない。こうしておかないと、任意の URL の中身を
        // こちらのドメインから配る口になる
        val note = notes.find(PublicNoteId(notePublicId)) ?: return null
        val link = previews.links(note.contentHtml).getOrNull(linkIndex) ?: return null
        val sourceUrl = previews.preview(link).imageUrl ?: return null

        // 版が合わなければ取りに行かない。この口は無認証なので、版を変えながら
        // 呼ぶだけで前段のキャッシュを外し、そのたびに取得元へ取りに行かせられる
        if (version != LinkPreviewImageUrls.version(sourceUrl)) return null

        return fetcher.relay(sourceUrl, maxFreshFor = MAX_FRESH_FOR) { image -> respond(LinkPreviewImage(image)) }
    }

    private companion object {
        /**
         * アクセスの少ない個人利用が前提で、リンク先がしばらく変わらなくても困らないので、
         * アイコン・ヘッダーより長く持たせる
         */
        val MAX_FRESH_FOR = 30.days
    }
}

/**
 * 中継する画像 1 つ。本文は相手から読みながら流す。
 */
class LinkPreviewImage(
    private val image: RemoteImageFetchService.RelayedImage,
) {
    val contentType: ContentType get() = image.imageType.contentType
    val freshFor: Duration? get() = image.freshFor
    val contentLength: Long? get() = image.contentLength

    suspend fun copyTo(output: ByteWriteChannel) {
        image.copyTo(output)
    }
}
