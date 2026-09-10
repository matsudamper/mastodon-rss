package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * ICO コンテナに埋め込まれた画像を PNG として取り出す。
 *
 * favicon.ico の多くは複数解像度を 1 つの ICO コンテナに詰めている。ICO のまま
 * 配ると、Mastodon はこれをプロフィール画像として読めない（ブラウザで見えるのは
 * ブラウザ自身が ICO を解釈しているだけで、Mastodon 側の扱いとは別）。
 *
 * ICO の中の 1 枚が PNG ならバイト列としてそのまま完成した PNG なので、
 * コンテナから範囲を切り出すだけで配れる形になる。もう一方の形式である BMP（DIB）は
 * ICONDIR が持つ幅・高さと、AND マスクによる透過だけの単純な無圧縮画像なので、
 * 展開して PNG に描き直す。パレット形式（1/4/8 ビット）や圧縮された DIB は対応しない
 */
object IcoImagesUtil {
    /**
     * 埋め込まれた画像のうち面積が一番大きく、かつ変換できるものを PNG として返す。
     *
     * 見つからない、または ICO として壊れているときは null。
     * 配信元が申告する位置・長さはそのまま信じず、ファイルの範囲に収まっているかを見る
     */
    fun extractLargestImageAsPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < HEADER_SIZE) return null
        if (readU16(bytes, RESERVED_OFFSET) != 0) return null
        if (readU16(bytes, TYPE_OFFSET) != TYPE_ICON) return null

        val count = readU16(bytes, COUNT_OFFSET)
        var bestArea = -1
        var bestPng: ByteArray? = null

        for (index in 0 until count) {
            val entry = HEADER_SIZE + index * ENTRY_SIZE
            if (entry + ENTRY_SIZE > bytes.size) break

            val size = readU32(bytes, entry + SIZE_OFFSET)
            val offset = readU32(bytes, entry + IMAGE_OFFSET_OFFSET)
            val end = offset + size
            if (size <= 0 || end > bytes.size.toLong()) continue

            val area = areaOf(bytes, entry)
            if (area <= bestArea) continue

            val png = imageAsPng(bytes, offset.toInt(), size.toInt()) ?: continue
            bestArea = area
            bestPng = png
        }

        return bestPng
    }

    /**
     * ICONDIRENTRY が申告する幅と高さの面積。0 は 256 を表す
     */
    private fun areaOf(
        bytes: ByteArray,
        entry: Int,
    ): Int {
        val width = bytes[entry].toInt() and 0xFF
        val height = bytes[entry + 1].toInt() and 0xFF
        return (if (width == 0) SIDE_WHEN_ZERO else width) * (if (height == 0) SIDE_WHEN_ZERO else height)
    }

    /**
     * このエントリの中身を PNG バイト列にする。
     *
     * PNG 署名で始まっていればそのまま切り出す。そうでなければ BMP（DIB）として復号し、
     * 復号できなければ null（パレット形式・圧縮された DIB など）
     */
    private fun imageAsPng(
        bytes: ByteArray,
        start: Int,
        size: Int,
    ): ByteArray? {
        if (size >= PNG_SIGNATURE.size && bytes.regionStartsWith(start, PNG_SIGNATURE)) {
            return bytes.copyOfRange(start, start + size)
        }
        val decoded = decodeDib(bytes, start, size) ?: return null
        return encodePng(decoded.width, decoded.height, decoded.rgba)
    }

    private fun ByteArray.regionStartsWith(
        offset: Int,
        signature: ByteArray,
    ): Boolean = signature.indices.all { index -> this[offset + index] == signature[index] }

    // ==== BMP（DIB）の復号 ====

    private class DecodedImage(
        val width: Int,
        val height: Int,
        /** 上から下、1 ピクセルあたり R, G, B, A の並び */
        val rgba: ByteArray,
    )

    /**
     * ICO に埋め込まれた DIB を復号する。
     *
     * ICO の DIB は BITMAPFILEHEADER を持たず BITMAPINFOHEADER（40 バイト）から始まり、
     * 高さは実際の高さの 2 倍（色データ分 + AND マスク分）を申告する。行は下から並び、
     * 4 バイト境界にパディングされる。無圧縮の 24 / 32 ビットだけを扱う
     */
    private fun decodeDib(
        bytes: ByteArray,
        dibOffset: Int,
        entrySize: Int,
    ): DecodedImage? {
        if (entrySize < DIB_HEADER_SIZE || dibOffset + DIB_HEADER_SIZE > bytes.size) return null
        if (readU32(bytes, dibOffset) != DIB_HEADER_SIZE.toLong()) return null

        val width = readU32(bytes, dibOffset + 4)
        val doubledHeight = readU32(bytes, dibOffset + 8)
        val planes = readU16(bytes, dibOffset + 12)
        val bitCount = readU16(bytes, dibOffset + 14)
        val compression = readU32(bytes, dibOffset + 16)

        if (width <= 0L || width > MAX_SIDE) return null
        if (doubledHeight <= 0L || doubledHeight % 2L != 0L || doubledHeight / 2L > MAX_SIDE) return null
        if (planes != 1) return null
        if (bitCount != 24 && bitCount != 32) return null
        if (compression != 0L) return null

        val w = width.toInt()
        val h = (doubledHeight / 2L).toInt()
        val entryEnd = dibOffset.toLong() + entrySize

        val colorStride = rowStride(w, bitCount)
        val colorStart = dibOffset + DIB_HEADER_SIZE
        val colorEnd = colorStart.toLong() + colorStride.toLong() * h
        if (colorEnd > bytes.size.toLong() || colorEnd > entryEnd) return null

        val rgba = ByteArray(w * h * 4)
        val bytesPerPixel = bitCount / 8
        for (row in 0 until h) {
            val srcRowStart = colorStart + row * colorStride
            val dstRow = h - 1 - row
            for (col in 0 until w) {
                val srcPixel = srcRowStart + col * bytesPerPixel
                val b = bytes[srcPixel].toInt() and 0xFF
                val g = bytes[srcPixel + 1].toInt() and 0xFF
                val r = bytes[srcPixel + 2].toInt() and 0xFF
                val a = if (bitCount == 32) bytes[srcPixel + 3].toInt() and 0xFF else OPAQUE

                val dstIndex = (dstRow * w + col) * 4
                rgba[dstIndex] = r.toByte()
                rgba[dstIndex + 1] = g.toByte()
                rgba[dstIndex + 2] = b.toByte()
                rgba[dstIndex + 3] = a.toByte()
            }
        }

        val maskStride = rowStride(w, 1)
        val maskStart = colorEnd.toInt()
        val maskEnd = maskStart.toLong() + maskStride.toLong() * h
        val hasMask = maskEnd <= bytes.size.toLong() && maskEnd <= entryEnd

        // 32 ビットは実アルファを持つことがある。全ピクセルで 0 なら実アルファ無しとみなし、
        // AND マスクの方を使う（さもないと画像全体が透明に見える）
        if (bitCount != 32 || !hasRealAlpha(rgba)) {
            applyAndMask(bytes, maskStart, maskStride, w, h, hasMask, rgba)
        }

        return DecodedImage(w, h, rgba)
    }

    private fun hasRealAlpha(rgba: ByteArray): Boolean {
        var index = 3
        while (index < rgba.size) {
            if (rgba[index].toInt() != 0) return true
            index += 4
        }
        return false
    }

    private fun applyAndMask(
        bytes: ByteArray,
        maskStart: Int,
        maskStride: Int,
        width: Int,
        height: Int,
        hasMask: Boolean,
        rgba: ByteArray,
    ) {
        if (!hasMask) {
            for (index in rgba.indices step 4) rgba[index + 3] = OPAQUE.toByte()
            return
        }
        for (row in 0 until height) {
            val dstRow = height - 1 - row
            val rowStart = maskStart + row * maskStride
            for (col in 0 until width) {
                val byteIndex = rowStart + col / 8
                val bit = 7 - (col % 8)
                val transparent = (bytes[byteIndex].toInt() ushr bit) and 1 == 1
                rgba[(dstRow * width + col) * 4 + 3] = if (transparent) 0 else OPAQUE.toByte()
            }
        }
    }

    /** 1 行のバイト数。4 バイト境界に切り上げる */
    private fun rowStride(
        width: Int,
        bitsPerPixel: Int,
    ): Int = ((width * bitsPerPixel + 31) / 32) * 4

    // ==== PNG への書き出し ====

    /**
     * RGBA の画素配列から PNG バイト列を組み立てる。
     *
     * フィルタは使わず（各行を無変換のまま）、zlib の可逆圧縮にだけ任せる。
     * ここで作る PNG は自分たちの内部変換専用で、配信元から受け取ったものではないので、
     * 他の画像種のような「名乗りと中身が違わないか」の検査は要らない
     */
    private fun encodePng(
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
        out.write(PNG_SIGNATURE)
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

    // ==== 共通のバイト読み出し ====

    private fun readU16(
        bytes: ByteArray,
        offset: Int,
    ): Int = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /**
     * 32 ビットの値。オフセットや長さ、幅・高さは配信元の申告値で 2GB を超えうるので Long で扱う
     */
    private fun readU32(
        bytes: ByteArray,
        offset: Int,
    ): Long = readU16(bytes, offset).toLong() or (readU16(bytes, offset + 2).toLong() shl 16)

    /** ICONDIR の大きさ（reserved + type + count） */
    private const val HEADER_SIZE = 6

    /** ICONDIRENTRY 1 件の大きさ */
    private const val ENTRY_SIZE = 16
    private const val RESERVED_OFFSET = 0
    private const val TYPE_OFFSET = 2
    private const val COUNT_OFFSET = 4
    private const val SIZE_OFFSET = 8
    private const val IMAGE_OFFSET_OFFSET = 12

    /** ICONDIR.type がアイコン（カーソルではない）を表す値 */
    private const val TYPE_ICON = 1
    private const val SIDE_WHEN_ZERO = 256

    /** BITMAPINFOHEADER の大きさ。これ以外のヘッダー形式（パレット付きの古い形式など）は扱わない */
    private const val DIB_HEADER_SIZE = 40
    private const val MAX_SIDE = 256L
    private const val OPAQUE = 0xFF

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val IHDR_TYPE = byteArrayOf('I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte())
    private val IDAT_TYPE = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte())
    private val IEND_TYPE = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
}
