package net.matsudamper.mastodon.rss.logic

/**
 * プロフィール画像の URL に付ける版。
 *
 * 相手はアイコンを URL で覚えるので、取得元が変わったら違う URL にして取り直してもらう。
 * 中身ではなく取得元で見るので、同じ URL のまま画像だけ差し替えられた場合は変わらない。
 * アクターの `icon`・GraphQL・フォロワーのアイコンの中継で同じ値を使う
 */
object ActorIconVersion {
    private const val HEX_RADIX = 16

    fun of(sourceUrl: String): String = sourceUrl.hashCode().toUInt().toString(HEX_RADIX)
}
