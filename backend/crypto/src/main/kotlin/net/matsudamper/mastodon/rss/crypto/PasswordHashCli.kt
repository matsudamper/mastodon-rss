package net.matsudamper.mastodon.rss.crypto

/**
 * `ADMIN_PASSWORD_HASH` に入れる値を作るだけの入口。
 *
 * 管理画面にログインするにはハッシュが要るが、最初の 1 つを作る手段が他に無い。
 * サーバーに口を開けて作らせる形（未設定の間だけ認証なしで開ける）も考えられるが、
 * それは設定前のサーバーが外に立っている前提になる。手元で作って環境変数に入れる方が、
 * 開ける口が増えない。
 *
 * パスワードは標準入力から 1 行で受け取る。コマンドライン引数にすると、
 * シェルの履歴とプロセス一覧（`ps`）に平文で残る。
 *
 * ```sh
 * ./gradlew --quiet :backend:crypto:passwordHash
 * ```
 */
fun main() {
    val password = readlnOrNull()?.trim()

    if (password.isNullOrEmpty()) {
        System.err.println("パスワードを標準入力に 1 行で渡すこと")
        return
    }

    // 出すのはハッシュだけにする。他のものを混ぜると、そのまま環境変数に貼れない
    println(PasswordHash.create(password).encode())
}
