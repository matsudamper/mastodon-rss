package net.matsudamper.mastodon.rss.frontend.format

expect object UnixTimeUtil {
    /**
     * エポックからの秒数を、画面に出す文字列にする。
     *
     * 秒数のままではどの時点なのか読めないので、必ずここを通してから画面に載せる。
     * 書式とタイムゾーンは見ている人の環境に合わせる。
     */
    fun format(epochSeconds: Long): String
}
