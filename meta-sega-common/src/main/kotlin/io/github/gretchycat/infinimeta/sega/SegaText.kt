package io.github.gretchycat.infinimeta.sega

import java.nio.charset.Charset

/** Text fields in Sega headers are fixed-width, NUL/space padded legacy byte strings. */
object SegaText {
    private val latin1 = Charset.forName("ISO-8859-1")
    fun field(bytes: ByteArray, offset: Int, size: Int): String {
        if (offset < 0 || size < 0 || offset > bytes.size - size) return ""
        return String(bytes, offset, size, latin1).replace('\u0000', ' ').trim()
            .replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ")
    }
    fun ascii(bytes: ByteArray, offset: Int, value: String): Boolean {
        val expected = value.toByteArray(Charsets.US_ASCII)
        return offset >= 0 && offset <= bytes.size - expected.size &&
            expected.indices.all { (bytes[offset + it].toInt() and 0xff) == (expected[it].toInt() and 0xff) }
    }
    fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}
