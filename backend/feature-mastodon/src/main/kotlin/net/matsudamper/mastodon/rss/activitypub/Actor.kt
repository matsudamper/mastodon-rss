package net.matsudamper.mastodon.rss.activitypub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Actor エンドポイントが返す JSON。
 *
 * Mastodon は WebFinger で見つけた URL をこの形で取得し、プロフィールカードと
 * 公開鍵を作る。`publicKey` が無い、あるいは `id` が取得 URL と食い違うと
 * アクターとして認識されない。
 *
 * `@context` のような記号入りのキーは Kotlin の識別子にできないので
 * [SerialName] で対応する。
 */
@Serializable
data class Actor(
    @SerialName("@context")
    @Serializable(with = ActorContextSerializer::class)
    val context: ActorContext = ActorContext,
    @SerialName("id")
    val id: String,
    /**
     * `Service` は「自動化されたアカウント」を表す。人間ではなく RSS の
     * 転送であることを相手に伝えられる。`Person` でも動く。
     */
    @SerialName("type")
    val type: String = TYPE_SERVICE,
    /** WebFinger の acct 名と一致させること。ずれると検索から辿り着けない */
    @SerialName("preferredUsername")
    val preferredUsername: String,
    @SerialName("name")
    val name: String,
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("inbox")
    val inbox: String,
    @SerialName("outbox")
    val outbox: String,
    /**
     * プロフィールに載せる投稿の一覧。Mastodon は未フォローでもここを引きに来る。
     * outbox はフォロー後のバックフィル向けで、プロフィール表示には使われない。
     */
    @SerialName("featured")
    val featured: String,
    @SerialName("followers")
    val followers: String,
    @SerialName("following")
    val following: String,
    /** プロフィールから開くリンク。Mastodon は無ければ id を使う */
    @SerialName("url")
    val url: String? = null,
    /**
     * プロフィールに並べるリンク集。Mastodon は `PropertyValue` の
     * `name` を見出し、`value` を中身として表示する。
     */
    @SerialName("attachment")
    val attachment: List<ActorAttachment> = emptyList(),
    @SerialName("publicKey")
    val publicKey: ActorPublicKey,
    /**
     * Mastodon 4.6 以降。ピン留め欄を出すか
     */
    @SerialName("showFeatured")
    val showFeatured: Boolean = false,
) {
    companion object {
        const val TYPE_SERVICE: String = "Service"
    }
}

/**
 * プロフィールのリンク集の 1 項目。
 *
 * `value` は Mastodon 側で HTML として解釈される。リンクにするには
 * `<a href="...">` を入れる必要があり、素の URL を入れてもリンクにはならない。
 *
 * @param name 見出し
 * @param value 中身の HTML
 */
@Serializable
data class ActorAttachment(
    @SerialName("type")
    val type: String = TYPE_PROPERTY_VALUE,
    @SerialName("name")
    val name: String,
    @SerialName("value")
    val value: String,
) {
    companion object {
        const val TYPE_PROPERTY_VALUE: String = "PropertyValue"
    }
}

/**
 * Actor に埋め込む公開鍵。
 *
 * @param id 署名の `keyId` として飛んでくる値。`<actor>#main-key` の形にする
 * @param owner この鍵を持つアクターの `id`
 * @param publicKeyPem X.509 SubjectPublicKeyInfo の PEM（`BEGIN PUBLIC KEY`）
 */
@Serializable
data class ActorPublicKey(
    @SerialName("id")
    val id: String,
    @SerialName("owner")
    val owner: String,
    @SerialName("publicKeyPem")
    val publicKeyPem: String,
)
