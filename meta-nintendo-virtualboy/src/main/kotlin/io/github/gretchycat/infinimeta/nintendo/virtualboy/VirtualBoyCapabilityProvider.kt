package io.github.gretchycat.infinimeta.nintendo.virtualboy

import io.github.gretchycat.infinistream.*
import java.nio.charset.Charset

class VirtualBoyCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(VirtualBoyRomHandler())
}

private class VirtualBoyRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.virtualboy", "meta-nintendo", "Nintendo Virtual Boy cartridge ROM",
        listOf(FormatSupport(setOf(MIME), setOf("vb", "vboy"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "gameCode", "makerCode", "version", "romSize"),
        limitations = listOf(
            "Commercial and homebrew ROM dumps are raw, headerless data with no universal magic signature",
            "Content confirmation uses a valid power-of-two ROM length and the embedded 16-byte cartridge header near EOF",
            "Header extraction from a sequential-only source requires enough read budget to reach the final 544-byte header"
        ),
        attributes = mapOf("probeRequiresPositionalRead" to "true")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val candidate = super.probe(context)
        if (candidate.confidence == MatchConfidence.NONE) return candidate
        val length = context.resource.metadata.length ?: context.resource.source?.metadata?.length ?: return candidate
        val header = readHeader(context.resource.source, length, context.cancellation) ?: return candidate
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the raw ROM size and embedded Virtual Boy cartridge header",
            mapOf("mimeType" to MIME, "format" to "virtual-boy-cartridge-rom", "title" to header.title))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val length = context.length ?: return null
        if (!validLength(length)) return null
        val bytes = context.readAt(length - HEADER_DISTANCE, HEADER_SIZE)
        val header = parse(bytes, length) ?: return null
        val fields = linkedMapOf(
            "System" to "Nintendo Virtual Boy",
            "Game code" to header.gameCode,
            "Maker code" to header.makerCode,
            "Software version" to header.version.toString(),
            "ROM size" to "$length bytes",
            "Header offset" to "0x%X".format(length - HEADER_DISTANCE)
        )
        return MetadataRecord("virtual-boy-cartridge-rom", MIME, header.title, fields,
            listOf("The embedded title and identifiers are cartridge metadata; they do not identify a commercial release by name."))
    }

    private fun readHeader(source: SourceResource?, length: Long, cancellation: CancellationToken): Header? {
        if (!validLength(length)) return null
        val input = source?.open() ?: return null
        try {
            val positional = input as? PositionalReadable ?: return null
            val bytes = ByteArray(HEADER_SIZE)
            var count = 0
            while (count < bytes.size) {
                cancellation.check()
                val read = positional.readAt(length - HEADER_DISTANCE + count, bytes, count, bytes.size - count)
                if (read <= 0 || read > bytes.size - count) return null
                count += read
            }
            return parse(bytes, length)
        } finally {
            input.close()
        }
    }

    private data class Header(val title: String, val makerCode: String, val gameCode: String, val version: Int)

    private fun parse(bytes: ByteArray, length: Long): Header? {
        if (!validLength(length) || bytes.size < HEADER_SIZE || (0x04..0x08).any { bytes[it] != 0.toByte() }) return null
        val title = String(bytes, 0, 4, SHIFT_JIS).filterNot(Char::isISOControl).replace("\uFFFD", "").trim()
        val makerCode = String(bytes, 9, 2, Charsets.US_ASCII)
        val gameCode = String(bytes, 11, 4, Charsets.US_ASCII)
        if (title.isBlank() || makerCode.length != 2 || !makerCode.all(Char::isLetterOrDigit) ||
            gameCode.length != 4 || !gameCode.all(Char::isLetterOrDigit)) return null
        return Header(title, makerCode, gameCode, bytes[15].toInt() and 0xff)
    }

    private fun validLength(length: Long): Boolean = length in MIN_ROM_SIZE..MAX_ROM_SIZE && length and (length - 1) == 0L

    companion object {
        private const val MIME = "application/x-virtual-boy-rom"
        private const val HEADER_DISTANCE = 544L
        private const val HEADER_SIZE = 16
        private const val MIN_ROM_SIZE = 1024L
        private const val MAX_ROM_SIZE = 16L * 1024 * 1024
        private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    }
}
