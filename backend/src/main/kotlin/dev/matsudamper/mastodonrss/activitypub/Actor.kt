package dev.matsudamper.mastodonrss.activitypub

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
    @Serializable(with = StringListSerializer::class)
    val context: List<String> = DEFAULT_CONTEXT,
    val id: String,
    /**
     * `Service` は「自動化されたアカウント」を表す。人間ではなく RSS の
     * 転送であることを相手に伝えられる。`Person` でも動く。
     */
    val type: String = TYPE_SERVICE,
    /** WebFinger の acct 名と一致させること。ずれると検索から辿り着けない */
    val preferredUsername: String,
    val name: String,
    val summary: String? = null,
    val inbox: String,
    val outbox: String,
    val followers: String,
    val following: String,
    /** プロフィールから開くリンク。Mastodon は無ければ id を使う */
    val url: String? = null,
    val publicKey: ActorPublicKey,
) {
    companion object {
        const val TYPE_SERVICE: String = "Service"

        /**
         * `security/v1` は `publicKey` の語彙を持ち込むために要る。
         * これが無いと JSON-LD として厳密に処理する実装が公開鍵を読み落とす。
         */
        val DEFAULT_CONTEXT: List<String> =
            listOf(
                "https://www.w3.org/ns/activitystreams",
                "https://w3id.org/security/v1",
            )
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
    val id: String,
    val owner: String,
    val publicKeyPem: String,
)
