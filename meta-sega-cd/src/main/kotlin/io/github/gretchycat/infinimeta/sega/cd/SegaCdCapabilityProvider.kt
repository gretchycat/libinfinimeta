package io.github.gretchycat.infinimeta.sega.cd

import io.github.gretchycat.infinistream.*

class SegaCdCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(SegaCdHandler())
}

private class SegaCdHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.cd", "meta-sega", "Sega CD / Mega CD data disc",
        listOf(FormatSupport(setOf("application/x-sega-cd-image"), setOf("iso", "bin", "chd"), magic = listOf(
            MagicSignature("534547414449534353595354454D", 0, "SEGADISCSYSTEM header"),
            MagicSignature("534547414449534353595354454D", 16, "SEGADISCSYSTEM after raw CD sector header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA), metadata = setOf("system", "sectorFormat", "imageSize"),
        limitations = listOf("Identifies raw/cooked data tracks by the Sega CD boot signature; cue sheets and multi-track layouts are not parsed"))

    private fun header(prefix: ByteArray): Pair<Int, String>? = when {
        starts(prefix, 0, "SEGADISCSYSTEM") -> 0 to "2048-byte cooked sectors"
        isRawSync(prefix) && starts(prefix, 16, "SEGADISCSYSTEM") -> 16 to "2352-byte raw sectors"
        else -> null
    }
    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val found = header(prefix) ?: return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated Sega CD boot signature (${found.second})",
            mapOf("mimeType" to "application/x-sega-cd-image", "format" to "sega-cd-disc-image"))
    }
    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val prefix = context.readAt(0, 64)
        val found = header(prefix) ?: return null
        return MetadataRecord("sega-cd-disc-image", "application/x-sega-cd-image", "Sega CD / Mega CD disc",
            buildMap {
                put("System", "Sega CD / Mega CD")
                put("Sector format", found.second)
                context.length?.let { put("Image size", "$it bytes") }
            })
    }
    private fun starts(b: ByteArray, offset: Int, text: String) = offset >= 0 && offset <= b.size - text.length &&
        text.toByteArray(Charsets.US_ASCII).indices.all { b[offset + it] == text.toByteArray(Charsets.US_ASCII)[it] }
    private fun isRawSync(b: ByteArray) = b.size >= 12 && b[0] == 0.toByte() &&
        (1..10).all { (b[it].toInt() and 0xff) == 0xff } && b[11] == 0.toByte()
}
