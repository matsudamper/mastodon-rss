package net.matsudamper.mastodon.rss.frontend.ui

interface PublicScaffoldListener {
    fun onClickHome()

    fun onClickAdmin()
}

interface AdminScaffoldListener {
    fun onClickHome()

    fun onClickAdmin()
}
