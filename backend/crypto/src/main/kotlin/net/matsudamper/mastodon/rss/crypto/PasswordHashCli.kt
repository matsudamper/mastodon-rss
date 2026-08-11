package net.matsudamper.mastodon.rss.crypto

/**
 * `ADMIN_PASSWORD_HASH` に入れる値を作る。`./gradlew --quiet :backend:crypto:passwordHash`
 *
 * 引数ではなく標準入力から受けるのは、シェルの履歴と `ps` に平文で残るため。
 */
fun main() {
    val password = readlnOrNull()?.trim()

    if (password.isNullOrEmpty()) {
        System.err.println("パスワードを標準入力に 1 行で渡すこと")
        return
    }

    // そのまま環境変数に貼れるよう、ハッシュ以外は出さない
    println(PasswordHash.create(password).encode())
}
