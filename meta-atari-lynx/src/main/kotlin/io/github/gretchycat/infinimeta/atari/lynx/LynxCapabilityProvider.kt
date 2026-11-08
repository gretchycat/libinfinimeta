package io.github.gretchycat.infinimeta.atari.lynx

import io.github.gretchycat.infinistream.*

class LynxCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(LynxHandler())
}

private class LynxHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.atari.lynx", "meta-consoles", "Atari Lynx LNX cartridge",
        listOf(FormatSupport(setOf("application/x-atari-lynx-rom"), setOf("lnx"),
            magic = listOf(MagicSignature("4C594E58", 0, "LYNX cartridge header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("title", "manufacturer", "bankSizes", "rotation", "eeprom"),
        limitations = listOf("Validates the 64-byte LNX wrapper; headerless .lyx/raw dumps are not distinguishable from arbitrary binary data")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(MAGIC)) return super.probe(context)
        val parsed = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "LYNX signature is present but the bank lengths or header fields are inconsistent")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Atari Lynx LNX header and declared ROM bank length",
            mapOf("mimeType" to MIME, "title" to parsed.title, "format" to "atari-lynx-lnx"))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val parsed = parse(context.readAt(0, 64), context.length) ?: return null
        return MetadataRecord("atari-lynx-lnx", MIME, parsed.title.takeIf(String::isNotBlank), linkedMapOf(
            "System" to "Atari Lynx",
            "Manufacturer" to parsed.manufacturer.ifBlank { "Unspecified" },
            "Bank 0 size" to "${parsed.bank0 * 256} bytes (${parsed.bank0} pages)",
            "Bank 1 size" to if (parsed.bank1 == 0) "Absent" else "${parsed.bank1 * 256} bytes (${parsed.bank1} pages)",
            "Rotation" to listOf("None", "Left", "Right")[parsed.rotation],
            "Audio input addressing" to if (parsed.audioInput) "Enabled" else "Disabled",
            "EEPROM flags" to "0x%02X".format(parsed.eeprom),
            "ROM payload" to "${parsed.payloadBytes} bytes"
        ))
    }

    private data class Header(val title: String, val manufacturer: String, val bank0: Int, val bank1: Int,
        val rotation: Int, val audioInput: Boolean, val eeprom: Int, val payloadBytes: Long)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!b.startsWith(MAGIC) || b.size < 64) return null
        val bank0 = u16le(b, 4)
        val bank1 = u16le(b, 6)
        val version = u16le(b, 8)
        val rotation = b[58].toInt() and 255
        val payload = bank0.toLong().plus(bank1.toLong()) * 256L
        if (bank0 == 0 || version != 1 || rotation !in 0..2 || (b[59].toInt() and 255) !in 0..1 ||
            (60..63).any { b[it].toInt() and 255 != 0 && it >= 61 } ||
            length != null && length != payload + 64L) return null
        return Header(text(b, 10, 32), text(b, 42, 16), bank0, bank1, rotation,
            b[59].toInt() != 0, b[60].toInt() and 255, payload)
    }

    private fun text(b: ByteArray, start: Int, size: Int) = String(b, start, size, Charsets.US_ASCII)
        .substringBefore('\u0000').filterNot(Char::isISOControl).trim()
    private fun u16le(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }

    companion object {
        private const val MIME = "application/x-atari-lynx-rom"
        private val MAGIC = byteArrayOf(0x4c, 0x59, 0x4e, 0x58)
    }
}
