package io.github.gretchycat.infinimeta.sega.smd

import io.github.gretchycat.infinimeta.sega.MegaDriveHeader
import io.github.gretchycat.infinistream.*

class SmdCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(SmdRomHandler())
}

private class SmdRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.smd", "meta-sega", "Super Mega Drive interleaved ROM",
        listOf(FormatSupport(setOf("application/x-genesis-rom"), setOf("smd"), magic = listOf(
            MagicSignature("4541474E", 0x280, "SMD Genesis header"), MagicSignature("45414D47", 0x280, "SMD Mega Drive header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("console", "domesticTitle", "internationalTitle", "productCode", "checksum", "romRange", "region"),
        limitations = listOf("Decodes the first 16 KiB interleaved block only; stored checksum is not validated"))

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val marker = MegaDriveHeader.matches(prefix, 0x280, "EAGN") || MegaDriveHeader.matches(prefix, 0x280, "EAMG")
        val length = context.resource.metadata.length
        if (marker && prefix.size >= 512 && (length == null || length >= 512 + 16 * 1024) &&
            (length == null || (length - 512) % (16 * 1024) == 0L)) {
            val evenPlane = context.prefix().copyOfRange(512, 512 + 8192)
            val oddPlane = context.prefix().copyOfRange(512 + 8192, 512 + 16 * 1024)
            val logical = ByteArray(16 * 1024)
            for (i in 0 until 8192) {
                logical[i * 2] = oddPlane[i]
                logical[i * 2 + 1] = evenPlane[i]
            }
            if (MegaDriveHeader.matches(logical, 0x100, "SEGA") || MegaDriveHeader.matches(logical, 0x100, " SEGA") ||
                MegaDriveHeader.matches(logical, 0x100, "SAMSUNG PICO") || MegaDriveHeader.matches(logical, 0x100, "IMA IKUN"))
                return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
                    "Validated SMD marker and deinterleaved Mega Drive header",
                    mapOf("mimeType" to "application/x-genesis-rom", "format" to "sega-smd-rom"))
        }
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val length = context.length
        if (length != null && (length < 512 + 16 * 1024 || (length - 512) % (16 * 1024) != 0L)) return null
        // One forward read supports both seekable and sequential-only storage providers.
        val data = context.readAt(0, 512 + 16 * 1024)
        if (data.size < 512 + 16 * 1024) return null
        val firstPlane = data.copyOfRange(512, 512 + 8192)
        val secondPlane = data.copyOfRange(512 + 8192, 512 + 16 * 1024)
        if (firstPlane.size != 8192 || secondPlane.size != 8192) return null
        val logical = ByteArray(16 * 1024)
        for (i in 0 until 8192) {
            logical[i * 2] = secondPlane[i]
            logical[i * 2 + 1] = firstPlane[i]
        }
        if (!MegaDriveHeader.matches(data, 0x280, "EAGN") && !MegaDriveHeader.matches(data, 0x280, "EAMG")) return null
        if (!MegaDriveHeader.matches(logical, 0x100, "SEGA") && !MegaDriveHeader.matches(logical, 0x100, " SEGA") &&
            !MegaDriveHeader.matches(logical, 0x100, "SAMSUNG PICO") && !MegaDriveHeader.matches(logical, 0x100, "IMA IKUN")) return null
        return MegaDriveHeader.read(logical, 0x100, length?.minus(512), "sega-smd-rom", "application/x-genesis-rom")
    }
}
