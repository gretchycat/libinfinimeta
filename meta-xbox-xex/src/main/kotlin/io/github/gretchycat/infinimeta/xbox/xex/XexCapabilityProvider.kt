package io.github.gretchycat.infinimeta.xbox.xex

import io.github.gretchycat.infinistream.*

class XexCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(XexHandler())
}

private class XexHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.xbox.xex", "meta-consoles", "Microsoft Xbox 360 XEX executable",
        listOf(FormatSupport(setOf("application/x-xbox-xex"), setOf("xex"), magic = listOf(
            MagicSignature("58455832", 0, "XEX2 executable"), MagicSignature("58455831", 0, "XEX1 executable")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("version", "moduleFlags", "imageOffset", "securityOffset", "optionalHeaderCount"),
        limitations = listOf("Reads public XEX header fields only; encrypted/compressed executable sections and title metadata are not decoded")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(XEX1) && !b.startsWith(XEX2)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "XEX signature found but the header directory bounds are invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Xbox executable header and optional-header directory",
            mapOf("mimeType" to MIME, "format" to h.format))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, 0x10000), context.length) ?: return null
        return MetadataRecord(h.format, MIME, fields = linkedMapOf(
            "System" to "Xbox 360",
            "XEX revision" to h.format.uppercase(),
            "Module flags" to "0x%08X".format(h.flags),
            "Image header size" to "${h.headerSize} bytes",
            "Security header offset" to "0x%08X".format(h.securityOffset),
            "Optional header entries" to h.optionalCount.toString(),
            "File size" to "${context.length ?: 0} bytes"
        ))
    }

    private data class Header(val format: String, val flags: Long, val headerSize: Long, val securityOffset: Long, val optionalCount: Long)
    private fun parse(b: ByteArray, length: Long?): Header? {
        if ((!b.startsWith(XEX1) && !b.startsWith(XEX2)) || b.size < 24) return null
        val headerSize = u32be(b, 8); val security = u32be(b, 16); val count = u32be(b, 20)
        val actualLength = length ?: return null
        if (headerSize < 24 || headerSize > actualLength || security < 24 || security > actualLength - 4 ||
            count > MAX_OPTIONAL_HEADERS || 24L + count * 8L > minOf(headerSize, b.size.toLong())) return null
        return Header(if (b.startsWith(XEX2)) "xbox-360-xex2" else "xbox-360-xex1",
            u32be(b, 4), headerSize, security, count)
    }
    private fun u32be(b: ByteArray, p: Int): Long = ((b[p].toLong() and 255) shl 24) or ((b[p + 1].toLong() and 255) shl 16) or
        ((b[p + 2].toLong() and 255) shl 8) or (b[p + 3].toLong() and 255)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }
    companion object {
        private const val MIME = "application/x-xbox-xex"
        private const val MAX_OPTIONAL_HEADERS = 4096L
        private val XEX1 = "XEX1".toByteArray(Charsets.US_ASCII)
        private val XEX2 = "XEX2".toByteArray(Charsets.US_ASCII)
    }
}
