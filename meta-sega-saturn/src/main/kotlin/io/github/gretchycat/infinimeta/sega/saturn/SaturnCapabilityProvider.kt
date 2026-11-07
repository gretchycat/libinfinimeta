package io.github.gretchycat.infinimeta.sega.saturn

import io.github.gretchycat.infinimeta.sega.SegaText
import io.github.gretchycat.infinistream.*

class SaturnCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(SaturnHandler())
}

private class SaturnHandler : MetadataCapabilityHandler {
    private val magic = "SEGA SEGASATURN "
    override val descriptor = HandlerDescriptor("meta.sega.saturn", "meta-sega", "Sega Saturn disc image / boot header",
        listOf(FormatSupport(setOf("application/x-sega-saturn-disc"), setOf("iso", "bin", "cue", "chd"), magic = listOf(
            MagicSignature("53454741205345474153415455524E20", 0, "Sega Saturn header"),
            MagicSignature("53454741205345474153415455524E20", 16, "Sega Saturn header in a raw CD sector")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "productCode", "version", "releaseDate", "publisher", "title", "region"),
        limitations = listOf("Reads the embedded Sega boot header only; cue sheets and disc track layouts are not parsed"))

    private fun base(bytes: ByteArray): Int? = when {
        SegaText.ascii(bytes, 0, magic) -> 0
        SegaText.ascii(bytes, 16, magic) -> 16
        else -> null
    }
    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val at = base(prefix) ?: return super.probe(context)
        val title = SegaText.field(prefix, at + 0x40, 16)
        val product = SegaText.field(prefix, at + 0x50, 10)
        if (title.isBlank() && product.isBlank()) return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated Sega Saturn system identifier and boot fields",
            mapOf("mimeType" to "application/x-sega-saturn-disc", "format" to "sega-saturn-disc"))
    }
    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val bytes = context.readAt(0, 0x200)
        val at = base(bytes) ?: return null
        val title = SegaText.field(bytes, at + 0x40, 16).takeIf(String::isNotBlank)
        val fields = linkedMapOf("System" to "Sega Saturn")
        SegaText.field(bytes, at + 0x50, 10).takeIf(String::isNotBlank)?.let { fields["Product code"] = it }
        SegaText.field(bytes, at + 0x60, 6).takeIf(String::isNotBlank)?.let { fields["Version"] = it }
        SegaText.field(bytes, at + 0x70, 8).takeIf(String::isNotBlank)?.let { fields["Release date"] = it }
        SegaText.field(bytes, at + 0x80, 16).takeIf(String::isNotBlank)?.let { fields["Publisher code"] = it }
        SegaText.field(bytes, at + 0x20, 16).takeIf(String::isNotBlank)?.let { fields["Area codes"] = it }
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("sega-saturn-disc", "application/x-sega-saturn-disc", title, fields)
    }
}
