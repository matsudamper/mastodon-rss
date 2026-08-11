package net.matsudamper.mastodon.rss.crypto

/** 引数ではなく標準入力から受けるのは、シェルの履歴と `ps` に平文で残るため */
fun main() {
    val password = readlnOrNull()?.trim()

    if (password.isNullOrEmpty()) {
        System.err.println("パスワードを標準入力に 1 行で渡すこと")
        return
    }

    println(PasswordHash.create(password).encode())
}
