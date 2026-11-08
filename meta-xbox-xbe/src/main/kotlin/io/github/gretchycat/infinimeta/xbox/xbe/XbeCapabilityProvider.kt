package io.github.gretchycat.infinimeta.xbox.xbe

import io.github.gretchycat.infinistream.*

class XbeCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(XbeHandler())
}

private class XbeHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.xbox.xbe", "meta-consoles", "Microsoft original Xbox XBE executable",
        listOf(FormatSupport(setOf("application/x-xbox-xbe"), setOf("xbe"),
            magic = listOf(MagicSignature("58424548", 0, "XBEH executable")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("baseAddress", "imageSize", "timestamp", "titleId", "title"),
        limitations = listOf("Reads the XBE header and title certificate; does not inspect the executable sections")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(MAGIC)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "XBEH signature found but declared header/image bounds are invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated the Xbox XBE header",
            mapOf("mimeType" to MIME, "format" to "xbox-xbe", "titleId" to "%08X".format(h.titleId)))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, minOf(context.length ?: 0, MAX_HEADER_READ.toLong()).toInt()), context.length) ?: return null
        return MetadataRecord("xbox-xbe", MIME, h.title.ifBlank { null }, linkedMapOf(
            "System" to "Original Xbox",
            "Base address" to "0x%08X".format(h.base),
            "Header size" to "${h.headerSize} bytes",
            "Image size" to "${h.imageSize} bytes",
            "Build timestamp" to h.timestamp.toString(),
            "Title ID" to "0x%08X".format(h.titleId),
            "Title" to h.title.ifBlank { "Not present" }
        ))
    }

    private data class Header(val base: Long, val headerSize: Long, val imageSize: Long, val timestamp: Long,
        val titleId: Long, val title: String)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!b.startsWith(MAGIC) || b.size < HEADER_SIZE || length != null && length < HEADER_SIZE) return null
        val base = u32le(b, 0x104); val headerSize = u32le(b, 0x108); val imageSize = u32le(b, 0x10c)
        val timestamp = u32le(b, 0x114); val certAddress = u32le(b, 0x118)
        val actual = length ?: return null
        if (headerSize < HEADER_SIZE || headerSize > actual || imageSize < headerSize || imageSize > actual ||
            certAddress < base || certAddress - base > actual - 20) return null
        val certOffset = (certAddress - base).toInt()
        if (certOffset + 20 > b.size) return null
        val certSize = u32le(b, certOffset)
        if (certSize < 20 || certSize > actual - certOffset) return null
        val titleId = u32le(b, certOffset + 8)
        val titlePointer = u32le(b, certOffset + 4)
        val titleOffset = if (titlePointer >= base) titlePointer - base else -1
        val title = if (titleOffset >= certOffset && titleOffset <= certOffset + certSize - 2 && titleOffset + 2 <= b.size)
            readUtf16Z(b, titleOffset.toInt(), minOf(256, (certOffset + certSize - titleOffset).toInt())) else ""
        return Header(base, headerSize, imageSize, timestamp, titleId, title)
    }

    private fun readUtf16Z(b: ByteArray, start: Int, bound: Int): String {
        val end = minOf(b.size, start + bound)
        var p = start
        while (p + 1 < end && (b[p] != 0.toByte() || b[p + 1] != 0.toByte())) p += 2
        return String(b, start, p - start, Charsets.UTF_16LE).filterNot(Char::isISOControl).trim()
    }
    private fun u32le(b: ByteArray, p: Int): Long = (b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
        ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }
    companion object {
        private const val MIME = "application/x-xbox-xbe"
        private const val HEADER_SIZE = 0x178
        private const val MAX_HEADER_READ = 1024 * 1024
        private val MAGIC = "XBEH".toByteArray(Charsets.US_ASCII)
    }
}
