package io.github.gretchycat.infinimeta.snk.ngp

import io.github.gretchycat.infinistream.*

class NgpCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(NgpHandler())
}

private class NgpHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.snk.ngp", "meta-consoles", "SNK Neo Geo Pocket cartridge ROM",
        listOf(FormatSupport(setOf(MIME), setOf("ngp", "ngc"), magic = listOf(
            MagicSignature("434F5059524947485420425920534E4B", 0, "Neo Geo Pocket copyright header"),
            MagicSignature("204C4943454E53454420425920534E4B", 0, "Neo Geo Pocket licensed header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("title", "gameNumber", "version", "colorMode", "romSize"),
        limitations = listOf("Uses the cartridge copyright header and fixed ROM header fields; does not identify commercial releases or validate save-block protection maps")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val bytes = context.prefix()
        if (!hasCopyright(bytes)) return super.probe(context)
        val h = parse(bytes, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "SNK copyright string is present but the ROM size or fixed header fields are invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated Neo Geo Pocket copyright string, cartridge header fields, and ROM size",
            mapOf("mimeType" to MIME, "format" to "snk-neo-geo-pocket", "title" to h.title,
                "color" to h.color.toString(), "gameNumber" to h.gameNumber.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val length = context.length ?: return null
        val bytes = context.readAt(0, HEADER_SIZE)
        val h = parse(bytes, length) ?: return null
        return MetadataRecord("snk-neo-geo-pocket", MIME, h.title.takeIf(String::isNotBlank), linkedMapOf(
            "System" to if (h.color) "Neo Geo Pocket Color" else "Neo Geo Pocket",
            "Game number" to "%04X".format(h.gameNumber),
            "Cartridge version" to h.version.toString(),
            "Cartridge type" to "0x%02X".format(h.type),
            "Color capability" to if (h.color) "Color" else "Monochrome",
            "ROM size" to "$length bytes"
        ))
    }

    private data class Header(val title: String, val gameNumber: Int, val version: Int, val type: Int,
        val color: Boolean)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!hasCopyright(b) || b.size < HEADER_SIZE || length == null || length !in MIN_SIZE..MAX_SIZE || length % BANK_SIZE != 0L)
            return null
        val gameNumber = u16le(b, 0x20)
        val version = b[0x22].toInt() and 255
        val type = b[0x23].toInt() and 255
        val title = String(b, 0x24, 12, Charsets.ISO_8859_1).substringBefore('\u0000')
            .filterNot(Char::isISOControl).trim()
        if (gameNumber == 0xffff || type !in VALID_TYPES || title.isBlank() || title.count(Char::isLetterOrDigit) < 3) return null
        return Header(title, gameNumber, version, type, type == COLOR_TYPE)
    }

    private fun hasCopyright(b: ByteArray): Boolean = starts(b, COPYRIGHT) || starts(b, LICENSED)
    private fun starts(b: ByteArray, prefix: ByteArray): Boolean = prefix.indices.all { b.getOrNull(it) == prefix[it] }
    private fun u16le(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)

    companion object {
        private const val MIME = "application/x-snk-neo-geo-pocket-rom"
        private const val HEADER_SIZE = 0x30
        private const val COLOR_TYPE = 0x10
        private const val MIN_SIZE = 64L * 1024
        private const val MAX_SIZE = 4L * 1024 * 1024
        private const val BANK_SIZE = 64L * 1024
        private val VALID_TYPES = setOf(0x00, 0x10)
        private val COPYRIGHT = "COPYRIGHT BY SNK".toByteArray(Charsets.US_ASCII)
        private val LICENSED = " LICENSED BY SNK".toByteArray(Charsets.US_ASCII)
    }
}
