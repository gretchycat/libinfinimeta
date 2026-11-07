package io.github.gretchycat.infinimeta.sega.dreamcast

import io.github.gretchycat.infinimeta.sega.SegaText
import io.github.gretchycat.infinistream.*

class DreamcastCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(DreamcastHandler())
}

private class DreamcastHandler : MetadataCapabilityHandler {
    private val signatures = listOf("SEGA SEGAKATANA ", "SEGA SEGADREAMCAST")
    override val descriptor = HandlerDescriptor("meta.sega.dreamcast", "meta-sega", "Sega Dreamcast boot header",
        listOf(FormatSupport(setOf("application/x-sega-dreamcast-disc"), setOf("gdi", "cdi", "iso", "bin", "cue", "chd"),
            magic = signatures.flatMap { signature -> listOf(MagicSignature(signature.toByteArray().joinToString("") { "%02X".format(it) }, 0, "Dreamcast boot header"),
                MagicSignature(signature.toByteArray().joinToString("") { "%02X".format(it) }, 16, "Dreamcast boot header in raw sector")) })),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "makerCode", "productCode", "version", "releaseDate", "bootFile", "publisher", "title", "region"),
        limitations = listOf("Extracts the Dreamcast IP.BIN boot header from a raw header or first data sector; GDI/CDI containers and track tables are not parsed"))

    private fun base(bytes: ByteArray): Int? = (listOf(0, 16).firstOrNull { at -> signatures.any { SegaText.ascii(bytes, at, it) } })
    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val at = base(prefix) ?: return super.probe(context)
        val title = SegaText.field(prefix, at + 0xA0, 32)
        if (title.isBlank()) return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated Dreamcast IP.BIN header and title",
            mapOf("mimeType" to "application/x-sega-dreamcast-disc", "format" to "sega-dreamcast-ipbin"))
    }
    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val bytes = context.readAt(0, 0x200)
        val at = base(bytes) ?: return null
        val title = SegaText.field(bytes, at + 0xA0, 32).takeIf(String::isNotBlank)
        val fields = linkedMapOf("System" to "Sega Dreamcast")
        SegaText.field(bytes, at + 0x10, 10).takeIf(String::isNotBlank)?.let { fields["Maker code"] = it }
        SegaText.field(bytes, at + 0x4A, 10).takeIf(String::isNotBlank)?.let { fields["Product code"] = it }
        SegaText.field(bytes, at + 0x5A, 6).takeIf(String::isNotBlank)?.let { fields["Version"] = it }
        SegaText.field(bytes, at + 0x60, 8).takeIf(String::isNotBlank)?.let { fields["Release date"] = it }
        SegaText.field(bytes, at + 0x70, 16).takeIf(String::isNotBlank)?.let { fields["Boot filename"] = it }
        SegaText.field(bytes, at + 0x80, 16).takeIf(String::isNotBlank)?.let { fields["Publisher"] = it }
        SegaText.field(bytes, at + 0x30, 8).takeIf(String::isNotBlank)?.let { fields["Area codes"] = it }
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("sega-dreamcast-ipbin", "application/x-sega-dreamcast-disc", title, fields)
    }
}
