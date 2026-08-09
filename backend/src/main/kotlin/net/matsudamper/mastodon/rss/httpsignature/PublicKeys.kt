package net.matsudamper.mastodon.rss.httpsignature

import java.security.PublicKey

/**
 * `keyId` から検証用の公開鍵を引く。
 *
 * ActivityPub では相手のアクター文書を GET して `publicKey` を読むことになるが、
 * 署名の検証そのものは「鍵をどこから持ってくるか」と関係が無い。
 * ここを口にしておくと、検証のテストがネットワークなしで書ける。
 */
interface PublicKeys {
    /** 引けなければ null。取得に失敗した場合も null で、呼び出し側は検証失敗として扱う */
    suspend fun find(keyId: String): SignatureKey?
}

/**
 * 検証に使う鍵と、その持ち主。
 *
 * @param keyId 引くのに使った `keyId`
 * @param owner この鍵を持つアクターの id。署名した相手が誰なのかはこれで決まる。
 *   アクティビティの `actor` と突き合わせて、他人になりすました投稿を弾く
 */
class SignatureKey(
    val keyId: String,
    val owner: String,
    val publicKey: PublicKey,
)
