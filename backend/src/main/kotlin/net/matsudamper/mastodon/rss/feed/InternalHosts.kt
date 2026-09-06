package net.matsudamper.mastodon.rss.feed

import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.Url

/**
 * 取りに行ってよい URL かを見る係。
 *
 * 名前を引く実装を外から差せるようにしてあるのは、テストで実際の DNS を
 * 引かせないため。差せないと、名前が引けるかどうかでテストの結果が変わる。
 */
interface ExternalHosts {
    suspend fun isExternal(url: String): Boolean
}

/**
 * 内部ネットワークを指す URL を断る [ExternalHosts]。
 *
 * 記事のリンクはフィードの配信元が自由に書けるので、そのまま取りに行くと
 * こちらのサーバーからしか届かない場所（DB や管理画面、クラウドのメタデータ）を
 * 叩かせられる。応答は投稿には出ないが、繋がったかどうかや応答の速さで中の様子は分かる。
 *
 * 名前を引いた結果で判断するので、引いた後に別のアドレスへ差し替えられる形
 * （DNS rebinding）は防げない。取りに行くのが画像 1 枚のためであることを踏まえて、
 * ここでは名前解決の 1 回ぶんだけを見る。
 */
object InternalHosts : ExternalHosts {
    /**
     * 名前を引けない場合も false にする。引けない先は取りに行っても意味が無い。
     */
    override suspend fun isExternal(url: String): Boolean {
        val host = runCatching { Url(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false

        val addresses = withContext(Dispatchers.IO) {
            try {
                InetAddress.getAllByName(host).toList()
            } catch (_: UnknownHostException) {
                emptyList()
            }
        }
        if (addresses.isEmpty()) return false

        // 1 つでも内部を指していれば断る。名前が複数のアドレスを持つとき、
        // どれで繋ぐかはこちらでは決められない
        return addresses.none { isInternal(it) }
    }

    internal fun isInternal(address: InetAddress): Boolean {
        with(address) {
            if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
                return true
            }
        }

        val bytes = address.address
        return when (bytes.size) {
            // 100.64.0.0/10。ISP が加入者に配る範囲で、外に見えて外ではない
            4 -> bytes[0].toInt() and 0xFF == 100 && (bytes[1].toInt() and 0xC0) == 0x40

            // fc00::/7。IPv6 のプライベートアドレス。isSiteLocalAddress は
            // 非推奨の fec0::/10 しか見ないのでここで見る
            16 -> (bytes[0].toInt() and 0xFE) == 0xFC

            else -> false
        }
    }
}
