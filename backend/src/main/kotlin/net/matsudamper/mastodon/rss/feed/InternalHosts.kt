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
                listOf()
            }
        }
        if (addresses.isEmpty()) return false

        // 1 つでも内部を指していれば断る。名前が複数のアドレスを持つとき、
        // どれで繋ぐかはこちらでは決められない
        return addresses.none { isInternal(it) }
    }

    /**
     * 外に出せない IPv4 の範囲。private と loopback は [InetAddress] が見るので、
     * ここに書くのはそれ以外
     */
    private fun isInternalIpv4(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF

        return when {
            // 0.0.0.0/8。宛先には使えない
            first == 0 -> true

            // 100.64.0.0/10。ISP が加入者に配る範囲で、外に見えて外ではない
            first == 100 && (second and 0xC0) == 0x40 -> true

            // 192.0.0.0/24。IETF がプロトコルの割り当てに使う
            first == 192 && second == 0 && (bytes[2].toInt() and 0xFF) == 0 -> true

            // 198.18.0.0/15。機器の性能測定用で、外には出ない
            first == 198 && (second and 0xFE) == 18 -> true

            // 240.0.0.0/4。予約されていて経路が無い
            first >= 240 -> true

            else -> false
        }
    }

    internal fun isInternal(address: InetAddress): Boolean {
        with(address) {
            if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
                return true
            }
        }

        val bytes = address.address
        return when (bytes.size) {
            4 -> isInternalIpv4(bytes)

            // fc00::/7。IPv6 のプライベートアドレス。isSiteLocalAddress は
            // 非推奨の fec0::/10 しか見ないのでここで見る
            16 -> (bytes[0].toInt() and 0xFE) == 0xFC

            else -> false
        }
    }
}
