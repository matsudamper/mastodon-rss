package net.matsudamper.mastodon.rss

import java.nio.file.Path
import net.matsudamper.mastodon.rss.actor.ActorPrivateKey
import net.matsudamper.mastodon.rss.actor.ActorUsername
import net.matsudamper.mastodon.rss.crypto.PasswordHash

/**
 * 環境変数を読む唯一の場所。起動時にここで全部読み、以降は引数で配る。
 *
 * 各所で `System.getenv` を呼ぶと、どの変数が効くのかがコード全体を追わないと
 * 分からなくなる。テストからも差し替えられず、既定値の確認しかできない。
 * `:backend:repository` のようなライブラリ側のモジュールが環境を読むのも同じ理由でやめる。
 * 何をどこから読むかを決めるのはアプリの入口の仕事にする。
 *
 * native-image では起動時に環境変数を読む方が設定ファイルより素直なので、
 * 設定の入口そのものは環境変数に寄せている。
 *
 * 空文字と空白だけの指定はどれも未設定と同じに扱う。docker compose の
 * `${VAR}` が空で展開されたときに、既定値へ落ちる方が扱いやすい。
 *
 * @param env 環境変数。テストから差し替えるためだけに引数にしている。
 *   JVM から自プロセスの環境変数は設定できないので、差し替えられないと読み取りのテストが書けない
 */
class ServerEnv(
    env: Map<String, String> = System.getenv(),
) {
    /**
     * バインドするアドレス
     */
    val host: String =
        run {
            val raw = env["HOST"]?.trim()
            if (raw.isNullOrEmpty()) "0.0.0.0" else raw
        }

    /**
     * 待ち受けポート。数値でなければ既定値に落とす
     */
    val port: Int = env["PORT"]?.trim()?.toIntOrNull() ?: 8080

    /**
     * 外部に公開するドメイン。WebFinger の `acct:` と Actor の `id` に使う。
     * Mastodon はリモートアクターを永続キャッシュするので、間違えると相手側からは直せない。
     */
    val domain: String =
        run {
            // アクター ID に焼き込まれる値なので、末尾の / は落としておく。
            // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
            val normalized =
                env["DOMAIN"]
                    ?.trim()
                    ?.removePrefix("https://")
                    ?.removePrefix("http://")
                    ?.trimEnd('/')

            // 既定値を用意して起動できてしまうと、localhost のようなドメインが
            // 焼き込まれたアクター ID を配ることになる。Mastodon はリモートアクターを
            // 永続キャッシュするので、相手側からは直せない。落とす方が安い
            require(!normalized.isNullOrEmpty()) {
                "DOMAIN が未設定。WebFinger の acct とアクターの id に使うので必ず指定すること"
            }
            normalized
        }

    /**
     * 固定アクターのユーザー名。`acct:<name>@<domain>` と `/users/<name>` の両方に入る。
     * Phase 6 で複数アクターにするまでは 1 つだけ。
     */
    val actorUsername: String =
        run {
            val raw = env["ACTOR_USERNAME"]?.trim()
            val username = if (raw.isNullOrEmpty()) "admin" else raw

            // URL のパスと acct の両方に入るので、区切り文字が混ざると別のものを指してしまう
            require(ActorUsername.isValid(username)) {
                "ACTOR_USERNAME が使えない形式: $username。" +
                    "英数字と _ . - のみ、先頭と末尾は英数字か _ にすること"
            }
            username
        }

    /**
     * SQLite の DB ファイル。親ディレクトリは接続時に作られる
     */
    val dbPath: Path =
        run {
            val raw = env["DB_PATH"]?.trim()
            Path.of(if (raw.isNullOrEmpty()) "./data/mastodon-rss.db" else raw)
        }

    /**
     * アクターの秘密鍵をどこから読むか
     */
    val actorPrivateKey: ActorPrivateKey =
        run {
            // PEM は中身をそのまま鍵として読むので、前後の空白も落とさずに渡す
            val pem = env["ACTOR_PRIVATE_KEY_PEM"]?.takeIf { it.isNotBlank() }
            val path = env["ACTOR_PRIVATE_KEY_PATH"]?.trim()?.takeIf { it.isNotEmpty() }

            // 両方が設定されていたら落とす。片方を黙って無視すると、意図していない鍵で
            // 起動したことに気付けない。鍵が変わると相手側は署名検証に失敗し続けるうえ、
            // Mastodon はアクターをキャッシュするので後から直しても戻りが遅い
            require(pem == null || path == null) {
                "ACTOR_PRIVATE_KEY_PEM と ACTOR_PRIVATE_KEY_PATH は同時に指定できない。どちらか一方にすること"
            }

            if (pem != null) {
                ActorPrivateKey.Pem(pem)
            } else {
                ActorPrivateKey.File(Path.of(path ?: "./data/actor-private-key.pem"))
            }
        }

    /**
     * 配信する静的ファイルのディレクトリ。未設定なら null で、何も配信しない
     */
    val staticSrcDir: Path? =
        run {
            val raw = env["STATIC_SRC_DIR"]?.trim()
            if (raw.isNullOrEmpty()) null else Path.of(raw)
        }

    val adminPasswordHash: PasswordHash? =
        run {
            val raw = env["ADMIN_PASSWORD_HASH"]?.trim()
            if (raw.isNullOrEmpty()) null else PasswordHash.parse(raw)
        }

    /**
     * セッション Cookie に `Secure` を付けるか。付けたまま http で開くとログインできない
     */
    val adminCookieSecure: Boolean =
        run {
            val raw = env["ADMIN_COOKIE_SECURE"]?.trim()
            if (raw.isNullOrEmpty()) {
                true
            } else {
                raw.lowercase().toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("ADMIN_COOKIE_SECURE は true か false にすること: $raw")
            }
        }
}
