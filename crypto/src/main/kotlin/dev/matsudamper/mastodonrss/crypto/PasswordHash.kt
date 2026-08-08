package dev.matsudamper.mastodonrss.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 管理画面のログインに使うパスワードハッシュ。
 *
 * 環境変数に入れて渡す前提なので、salt と反復回数を含めて 1 行の文字列に畳む。
 * 別々の変数に分けると片方だけ書き換わって検証が通らなくなるうえ、
 * 反復回数を後から上げたときに既存の設定が読めなくなる。
 *
 * 形式は `pbkdf2-sha256:<反復回数>:<salt>:<ハッシュ>` で、salt とハッシュは
 * パディング無しの URL-safe Base64。`+` や `/` が出ないので、シェルや
 * docker compose の `.env` にそのまま貼れる。
 *
 * 区切りが `$` でないのは、この種のハッシュでよくある PHC 形式（`$` 区切り）を
 * `.env` に貼ると docker compose が変数展開しようとして壊れるため。
 * `:` なら展開もクォートも要らない。
 *
 * アルゴリズムは PBKDF2-HMAC-SHA256。bcrypt や Argon2 の方が望ましいが、
 * どちらも JCA には無く依存を足すことになる。native-image に持ち込む依存は
 * 少ないほど安全なので、標準で使える中では最も素直なこれにする。
 */
class PasswordHash private constructor(
    private val iterations: Int,
    private val salt: ByteArray,
    private val hash: ByteArray,
) {
    /**
     * パスワードが一致するか調べる。
     *
     * 比較は [MessageDigest.isEqual] で行う。長さが同じなら内容によらず
     * 同じ時間で終わるので、応答時間から先頭何バイトが合っているかを
     * 推測されない。
     */
    fun matches(password: String): Boolean {
        val candidate = derive(password, salt, iterations, hash.size * BITS_PER_BYTE)
        return MessageDigest.isEqual(hash, candidate)
    }

    /** 環境変数に入れる 1 行の文字列に戻す。 */
    fun encode(): String =
        listOf(
            ALGORITHM_LABEL,
            iterations.toString(),
            BASE64_ENCODER.encodeToString(salt),
            BASE64_ENCODER.encodeToString(hash),
        ).joinToString(SEPARATOR)

    override fun toString(): String = "PasswordHash(iterations=$iterations)"

    companion object {
        /**
         * 反復回数。OWASP が PBKDF2-HMAC-SHA256 に対して挙げている推奨値に合わせている。
         * ログインのたびにこの回数を回すので、上げすぎると管理画面が重くなる。
         */
        const val DEFAULT_ITERATIONS: Int = 210_000

        /** salt の長さ。16 バイトあれば同じ salt が再び出ることは考えなくてよい */
        const val SALT_SIZE_BYTES: Int = 16

        /** 出力するハッシュの長さ。SHA-256 の出力と同じにしておく */
        const val HASH_SIZE_BITS: Int = 256

        private const val ALGORITHM_LABEL = "pbkdf2-sha256"
        private const val SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SEPARATOR = ":"
        private const val BITS_PER_BYTE = 8

        // パディングの = が付くと環境変数として貼ったときに引用が要ることがあるので落とす
        private val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val BASE64_DECODER: Base64.Decoder = Base64.getUrlDecoder()

        private val secureRandom = SecureRandom()

        /**
         * パスワードから新しいハッシュを作る。salt は毎回引き直すので、
         * 同じパスワードでも呼ぶたびに違う文字列になる。
         */
        fun create(password: String, iterations: Int = DEFAULT_ITERATIONS): PasswordHash {
            require(password.isNotEmpty()) { "パスワードが空" }
            require(iterations > 0) { "反復回数は 1 以上にすること: $iterations" }

            val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
            return PasswordHash(
                iterations = iterations,
                salt = salt,
                hash = derive(password, salt, iterations, HASH_SIZE_BITS),
            )
        }

        /**
         * [encode] が返した形式を読む。
         *
         * 壊れていたら例外にする。ログインが必ず失敗するだけの値を黙って抱えると、
         * パスワードを間違えたのか設定を間違えたのかが区別できなくなるため、
         * 起動時にここで落とす。
         */
        fun parse(encoded: String): PasswordHash {
            val parts = encoded.trim().split(SEPARATOR)
            require(parts.size == PART_COUNT) {
                "パスワードハッシュの形式が違う。$ALGORITHM_LABEL${SEPARATOR}反復回数${SEPARATOR}salt${SEPARATOR}ハッシュ の 4 つに $SEPARATOR 区切りで並べること"
            }

            val (label, rawIterations, rawSalt, rawHash) = parts
            require(label == ALGORITHM_LABEL) {
                "対応していないアルゴリズム: $label。使えるのは $ALGORITHM_LABEL のみ"
            }

            val iterations =
                rawIterations.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("反復回数が数値ではない: $rawIterations")

            val salt = decodeBase64(rawSalt, "salt")
            val hash = decodeBase64(rawHash, "ハッシュ")
            require(salt.isNotEmpty()) { "salt が空" }
            require(hash.isNotEmpty()) { "ハッシュが空" }

            return PasswordHash(iterations = iterations, salt = salt, hash = hash)
        }

        private const val PART_COUNT = 4

        private fun decodeBase64(value: String, name: String): ByteArray =
            try {
                BASE64_DECODER.decode(value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$name が URL-safe Base64 として読めない: $value", e)
            }

        private fun derive(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
            try {
                return SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM).generateSecret(spec).encoded
            } finally {
                // PBEKeySpec は渡した char[] を自前で複製して持つ。使い終わったら消しておく
                spec.clearPassword()
            }
        }
    }
}
