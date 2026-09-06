package net.matsudamper.mastodon.rss.feed

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

// 記事のリンク先を取りに行く前の入口。ここが通ると、こちらからしか届かない場所を
// 外から叩かせられる。アドレスの範囲だけを見るので、名前は引かない。
class InternalHostsTest {
    @Test
    fun `内部を指すアドレスは断る`() {
        val internal = listOf(
            "127.0.0.1",
            "0.0.0.0",
            "10.1.2.3",
            "172.16.0.1",
            "192.168.1.1",
            // クラウドのインスタンスメタデータ
            "169.254.169.254",
            "100.64.0.1",
            "::1",
            "fd00::1",
            "fe80::1",
        )

        internal.forEach { address ->
            assertEquals(true, InternalHosts.isInternal(InetAddress.getByName(address)), address)
        }
    }

    @Test
    fun `外を指すアドレスは通す`() {
        val external = listOf(
            "1.1.1.1",
            "93.184.216.34",
            "99.64.0.1",
            "101.64.0.1",
            "2001:4860:4860::8888",
        )

        external.forEach { address ->
            assertEquals(false, InternalHosts.isInternal(InetAddress.getByName(address)), address)
        }
    }
}
