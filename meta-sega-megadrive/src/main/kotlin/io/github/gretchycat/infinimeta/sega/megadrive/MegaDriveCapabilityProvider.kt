package io.github.gretchycat.infinimeta.sega.megadrive

import io.github.gretchycat.infinimeta.sega.MegaDriveHeader
import io.github.gretchycat.infinistream.*

class MegaDriveCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(MegaDriveRomHandler())
}

private class MegaDriveRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        id = "meta.sega.megadrive", library = "meta-sega", title = "Mega Drive / Genesis and related cartridge ROMs",
        formats = listOf(FormatSupport(
            mimeTypes = setOf("application/x-genesis-rom", "application/x-genesis-32x-rom", "application/x-sega-pico-rom", "application/x-sega-teradrive-rom"),
            extensions = setOf("gen", "md", "bin", "32x"),
            magic = listOf(
                MagicSignature("53454741", 0x100, "Sega Mega Drive header"),
                MagicSignature("2053454741", 0x100, "Sega Mega Drive header with leading space"),
                MagicSignature("53454741", 0x300, "Sega ROM after a 512-byte copier header"),
                MagicSignature("4D41525320434845434B204D4F4445", 0x3C0, "Sega 32X marker")
            )
        )),
        operations = setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("console", "domesticTitle", "internationalTitle", "productCode", "checksum", "romRange", "region"),
        limitations = listOf("Reads the fixed cartridge header only; stored checksums are not fully validated", "Generic .bin files are candidates until the ROM header is validated")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val base = listOf(0x100, 0x300).firstOrNull { at ->
            MegaDriveHeader.matches(prefix, at, "SEGA") || MegaDriveHeader.matches(prefix, at, " SEGA") ||
                MegaDriveHeader.matches(prefix, at, "SAMSUNG PICO") || MegaDriveHeader.matches(prefix, at, "IMA IKUN")
        }
        if (base != null) {
            val parsed = MegaDriveHeader.read(prefix, base, context.resource.metadata.length, "sega-megadrive-rom", "application/x-genesis-rom")
            if (parsed != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
                "Validated the Sega cartridge header", mapOf("mimeType" to parsed.mimeType, "format" to parsed.formatId))
        }
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val bytes = context.readAt(0, 0x400)
        val base = listOf(0x100, 0x300).firstOrNull { at ->
            MegaDriveHeader.matches(bytes, at, "SEGA") || MegaDriveHeader.matches(bytes, at, " SEGA") ||
                MegaDriveHeader.matches(bytes, at, "SAMSUNG PICO") || MegaDriveHeader.matches(bytes, at, "IMA IKUN")
        } ?: return null
        return MegaDriveHeader.read(bytes, base, context.length, "sega-megadrive-rom", "application/x-genesis-rom")
    }
}
