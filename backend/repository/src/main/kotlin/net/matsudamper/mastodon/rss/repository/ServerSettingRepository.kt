package net.matsudamper.mastodon.rss.repository

/**
 * サーバー全体の設定。管理画面から変えられる値を置く。
 *
 * 保存していない値は null で返す。既定の値は使う側が決める。
 * ここで既定を持つと、使う側の既定と食い違ったときにどちらが効いているのか分からなくなる。
 */
interface ServerSettingRepository {
    /**
     * 同じホストからのフォローで配る先の上限。保存していなければ null
     */
    fun maxDeliveryTargetsPerHost(): Int?

    /**
     * @param value 1 以上。0 以下はスキーマの制約で落ちる
     */
    fun saveMaxDeliveryTargetsPerHost(value: Int)
}
