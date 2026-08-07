package dev.matsudamper.mastodonrss.repository.sqlite

import java.security.MessageDigest

/**
 * マイグレーション 1 ファイル分。
 *
 * @param version ファイル名の `V001` の部分。適用順を決める
 * @param name ファイル名の `__` より後ろ。内容が分かるようにするためだけのもの
 * @param fileName リソース上のファイル名。エラーメッセージで使う
 * @param sql ファイルの中身
 */
internal data class Migration(
    val version: Int,
    val name: String,
    val fileName: String,
    val sql: String,
) {
    /**
     * 適用済みのファイルが後から書き換えられていないか調べるための SHA-256。
     *
     * 適用済みのマイグレーションを直すと、既存の DB と新規の DB でスキーマがずれる。
     * 気付かないまま進むと原因の分からない不具合になるので、起動時に検出する。
     */
    val checksum: String = sha256Hex(sql)

    private companion object {
        fun sha256Hex(value: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        }
    }
}
