package io.github.gretchycat.infinimeta.nintendo.wii

import io.github.gretchycat.infinimeta.nintendo.NintendoDiscHeaderParser
import io.github.gretchycat.infinistream.*

class WiiCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(WiiDiscHandler())
}

private class WiiDiscHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.wii", "meta-nintendo", "Nintendo Wii optical disc image",
        listOf(FormatSupport(setOf("application/x-wii-disc-image"), setOf("iso", "gcm"), magic = listOf(
            MagicSignature("5D1C9EA3", 0x18, "Wii disc header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "gameId", "makerCode", "region", "discNumber", "revision", "streamingAudio"),
        limitations = listOf("Reads raw Wii optical-disc headers only", "WBFS, Wii U, encrypted packages and multi-file disc containers are not parsed")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = NintendoDiscHeaderParser.parse(context.prefix(), wii = true)
        return if (header != null) HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Wii disc magic word and game identifier",
            mapOf("mimeType" to header.mimeType, "format" to header.formatId)) else candidate(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val header = NintendoDiscHeaderParser.parse(context.readAt(0, 0x440), wii = true) ?: return null
        val fields = linkedMapOf(
            "System" to header.system,
            "Game ID" to header.gameId,
            "Maker code" to header.makerCode,
            "Region" to header.region,
            "Disc number" to (header.discNumber + 1).toString(),
            "Revision" to header.revision.toString(),
            "Streaming audio" to if (header.streamingAudio) "Yes" else "No"
        )
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord(header.formatId, header.mimeType, header.title.takeIf(String::isNotBlank), fields)
    }

    private fun candidate(context: CapabilityProbeContext): HandlerProbe {
        val score = descriptor.formats.maxOfOrNull { it.score(context.resource) } ?: 0
        return HandlerProbe(if (score > 0) MatchConfidence.CANDIDATE else MatchConfidence.NONE, descriptor.operations)
    }
}
