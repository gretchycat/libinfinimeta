package io.github.gretchycat.infinimeta.sega.gamegear

import io.github.gretchycat.infinimeta.sega.SegaEightBitHeader
import io.github.gretchycat.infinistream.*

class GameGearCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(GameGearHandler())
}

private class GameGearHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.gamegear", "meta-sega", "Sega Game Gear ROM",
        listOf(FormatSupport(setOf("application/x-gamegear-rom"), setOf("gg", "gamegear"), magic = listOf(
            MagicSignature("544D522053454741", 0x1ff0, "TMR SEGA header"),
            MagicSignature("544D522053454741", 0x3ff0, "TMR SEGA header"),
            MagicSignature("544D522053454741", 0x7ff0, "TMR SEGA header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "productCode", "revision", "region", "storedChecksum", "romSizeCode"),
        limitations = listOf("The header's checksum and size code are reported but not verified against ROM contents"))

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = SegaEightBitHeader.find(context.prefix())?.takeIf { it.system == "game-gear" }
        return if (header != null) HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated TMR SEGA header and Game Gear region code", mapOf("mimeType" to header.mimeType, "format" to "sega-game-gear-rom"))
        else super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? =
        SegaEightBitHeader.find(context)?.takeIf { it.system == "game-gear" }?.metadata(context.length)
}
