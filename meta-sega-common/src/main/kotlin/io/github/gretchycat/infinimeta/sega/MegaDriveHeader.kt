package io.github.gretchycat.infinimeta.sega

import io.github.gretchycat.infinistream.MetadataRecord
import java.nio.charset.StandardCharsets

/** Common Sega Mega Drive header decoder shared by raw, copier-header and CD boot records. */
object MegaDriveHeader {
    fun read(bytes: ByteArray, headerOffset: Int, length: Long?, defaultFormat: String, defaultMime: String): MetadataRecord? {
        if (headerOffset < 0 || bytes.size < headerOffset + 0x100) return null
        val console = field(bytes, headerOffset, 16)
        val domestic = field(bytes, headerOffset + 0x20, 48)
        val international = field(bytes, headerOffset + 0x50, 48)
        val product = field(bytes, headerOffset + 0x80, 14)
        val region = field(bytes, headerOffset + 0xF0, 16)
        val marker = console.uppercase()
        val mime = when {
            marker.contains("PICO") || marker.contains("SAMSUNG PICO") || marker.contains("IMA IKUNO") -> "application/x-sega-pico-rom"
            marker.contains("TERA68K") || marker.contains("TERA286") -> "application/x-sega-teradrive-rom"
            marker.contains("32X") || field(bytes, 0x3C0, 16).contains("MARS CHECK MODE") -> "application/x-genesis-32x-rom"
            else -> defaultMime
        }
        val format = when (mime) {
            "application/x-sega-pico-rom" -> "sega-pico-rom"
            "application/x-sega-teradrive-rom" -> "sega-teradrive-rom"
            "application/x-genesis-32x-rom" -> "sega-32x-rom"
            else -> defaultFormat
        }
        val title = domestic.takeIf(::meaningful) ?: international.takeIf(::meaningful)
        val fields = linkedMapOf<String, String>()
        fields["System"] = console.takeIf(::meaningful) ?: formatLabel(format)
        if (meaningful(domestic)) fields["Domestic title"] = domestic
        if (meaningful(international)) fields["International title"] = international
        if (meaningful(product)) fields["Product code"] = product
        if (bytes.size >= headerOffset + 0x90) fields["Stored header checksum"] = hex16(bytes, headerOffset + 0x8E)
        if (bytes.size >= headerOffset + 0xA8) {
            val start = u32be(bytes, headerOffset + 0xA0)
            val end = u32be(bytes, headerOffset + 0xA4)
            if (start <= end && end != 0L) fields["ROM address range"] = "0x${start.toString(16)}–0x${end.toString(16)}"
        }
        if (meaningful(region)) fields["Region codes"] = region
        length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord(format, mime, title, fields,
            warnings = listOf("The stored checksum is reported; full ROM checksum validation is not performed."))
    }

    fun field(bytes: ByteArray, offset: Int, count: Int): String {
        if (offset < 0 || count < 0 || offset > bytes.size - count) return ""
        return String(bytes, offset, count, StandardCharsets.ISO_8859_1)
            .replace('\u0000', ' ').replace(Regex("[\\p{Cntrl}&&[^\\t]]"), " ")
            .trim().replace(Regex("\\s+"), " ")
    }

    fun matches(bytes: ByteArray, offset: Int, text: String): Boolean =
        offset >= 0 && offset <= bytes.size - text.length &&
            bytes.copyOfRange(offset, offset + text.length).contentEquals(text.toByteArray(StandardCharsets.US_ASCII))

    fun u16be(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    private fun u32be(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or (bytes[offset + 3].toLong() and 0xFF)
    private fun hex16(bytes: ByteArray, offset: Int): String = "0x%04X".format(u16be(bytes, offset))
    private fun meaningful(value: String): Boolean = value.isNotBlank() && value.any { it.isLetterOrDigit() }
    private fun formatLabel(format: String) = format.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
