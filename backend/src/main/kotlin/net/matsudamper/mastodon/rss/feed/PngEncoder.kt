package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * RGBA の画素配列から PNG バイト列を組み立てる。
 *
 * フィルタは使わず（各行を無変換のまま）、zlib の可逆圧縮にだけ任せる。
 * ここで作る PNG は自分たちの内部変換専用で、配信元から受け取ったものではないので、
 * 他の画像種のような「名乗りと中身が違わないか」の検査は要らない
 */
internal object PngEncoder {
    val SIGNATURE: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * IEND チャンク全体（長さ 0 + 型 + CRC）。完成した PNG は必ずこれで終わる
     */
    val IEND_CHUNK: ByteArray = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    private val IHDR_TYPE = byteArrayOf('I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte())
    private val IDAT_TYPE = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte())
    private val IEND_TYPE = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())

    /**
     * [rgba] は上から下、1 ピクセルあたり R, G, B, A の並び
     */
    fun encode(
        width: Int,
        height: Int,
        rgba: ByteArray,
    ): ByteArray {
        val stride = width * 4
        val raw = ByteArray(height * (1 + stride))
        for (row in 0 until height) {
            val dst = row * (1 + stride)
            raw[dst] = 0
            System.arraycopy(rgba, row * stride, raw, dst + 1, stride)
        }

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        val idat = ByteArrayOutputStream()
        DeflaterOutputStream(idat, deflater).use { it.write(raw) }
        deflater.end()

        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        writeChunk(out, IHDR_TYPE, ihdrOf(width, height))
        writeChunk(out, IDAT_TYPE, idat.toByteArray())
        writeChunk(out, IEND_TYPE, ByteArray(0))
        return out.toByteArray()
    }

    private fun ihdrOf(
        width: Int,
        height: Int,
    ): ByteArray {
        val data = ByteArray(13)
        writeU32BE(data, 0, width)
        writeU32BE(data, 4, height)
        data[8] = 8 // ビット深度
        data[9] = 6 // カラータイプ: RGBA
        data[10] = 0 // 圧縮方式
        data[11] = 0 // フィルタ方式
        data[12] = 0 // インターレース無し
        return data
    }

    private fun writeChunk(
        out: ByteArrayOutputStream,
        type: ByteArray,
        data: ByteArray,
    ) {
        writeU32BE(out, data.size)
        out.write(type)
        out.write(data)

        val crc = CRC32()
        crc.update(type)
        crc.update(data)
        writeU32BE(out, crc.value.toInt())
    }

    private fun writeU32BE(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun writeU32BE(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }
}
