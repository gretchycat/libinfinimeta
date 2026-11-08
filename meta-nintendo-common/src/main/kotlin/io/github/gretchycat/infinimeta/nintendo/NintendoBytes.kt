package io.github.gretchycat.infinimeta.nintendo

import java.nio.charset.Charset

object NintendoBytes {
    fun has(bytes: ByteArray, offset: Int, signature: ByteArray): Boolean =
        offset >= 0 && offset <= bytes.size - signature.size &&
            signature.indices.all { bytes[offset + it] == signature[it] }

    fun text(bytes: ByteArray, offset: Int, length: Int, charset: Charset = Charsets.US_ASCII): String {
        if (offset < 0 || length < 0 || offset > bytes.size - length) return ""
        return String(bytes, offset, length, charset).map { if (it !in ' '..'~') ' ' else it }.joinToString("")
            .trim().replace(Regex("\\s+"), " ")
    }

    fun u16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    fun u16be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    fun u32le(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)

    fun u32be(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or (bytes[offset + 3].toLong() and 0xff)

    fun printableScore(text: String): Double = if (text.isEmpty()) 0.0 else text.count {
        it == ' ' || it in '!'..'~'
    }.toDouble() / text.length
}
