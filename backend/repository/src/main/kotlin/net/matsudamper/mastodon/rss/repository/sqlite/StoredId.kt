package net.matsudamper.mastodon.rss.repository.sqlite

/**
 * 採番した id を列の型に合わせる。
 *
 * 列は INTEGER で、jOOQ の生成物では Int になる。Long のまま toInt() で詰めると
 * 範囲外の値が別の id に化けて、他の行を引いてしまう。範囲外は null にして、
 * 「そんな id は無い」として扱う。
 */
internal fun Long.toStoredId(): Int? = takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
