package net.matsudamper.mastodon.rss.remoteactor

import io.ktor.http.encodeURLParameter
import net.matsudamper.mastodon.rss.actor.ActorUrls

/**
 * フォロワーのアイコンを中継する URL。
 *
 * 配信元の URL を画面にそのまま出さないのは 2 つの理由から。画面は canvas に
 * 描くので、画像はブラウザの fetch で取ることになり、配信元が CORS を
 * 許していないと出ない。もう 1 つは、直に引かせると誰のプロフィールを見たかが
 * 相手のサーバーに残ること。
 */
class RemoteActorIconUrls(
    private val domain: String,
) {
    /**
     * @param sourceUrl 取得元。ここから決まる値を付けるので、相手がアイコンを
     *   差し替えれば URL ごと変わる
     */
    fun icon(
        actorUri: String,
        sourceUrl: String,
    ): String = "https://$domain$PATH?$ACTOR_PARAMETER=${actorUri.encodeURLParameter()}" +
        "&$VERSION_PARAMETER=${ActorUrls.iconVersion(sourceUrl)}"

    companion object {
        /**
         * アクターをパスではなくクエリで指すのは、アクター文書の URL を
         * パスに畳むと `/` を含む値の扱いが間に入るものごとに変わるため。
         *
         * 拡張子を付けてあるのは、キャッシュする対象を拡張子で決める CDN が
         * あるため。中身は画像だが種類は相手次第なので、種類を名乗らない
         * `.bin` にして、実際の種類は Content-Type で伝える
         */
        const val PATH: String = "/remote-actors/icon.bin"

        const val ACTOR_PARAMETER: String = "actor"

        const val VERSION_PARAMETER: String = "v"
    }
}
