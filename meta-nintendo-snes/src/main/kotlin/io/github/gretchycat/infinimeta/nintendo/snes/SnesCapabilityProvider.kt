package io.github.gretchycat.infinimeta.nintendo.snes

import io.github.gretchycat.infinimeta.nintendo.NintendoBytes
import io.github.gretchycat.infinistream.*

class SnesCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(SnesRomHandler())
}

private class SnesRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.snes", "meta-nintendo", "Super Nintendo / Super Famicom cartridge ROM",
        listOf(FormatSupport(setOf("application/x-snes-rom"), setOf("sfc", "smc", "swc", "fig"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "mapping", "mapMode", "cartridgeType", "romSize", "region", "checksumComplement"),
        limitations = listOf("Uses standard LoROM and HiROM headers, including 512-byte copier headers", "ExHiROM headers beyond the bounded sequential-read window are not inspected", "Stored checksum is reported but ROM checksum is not recalculated"),
        attributes = mapOf("probePrefixBytes" to "69632")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        val header = findHeader(prefix, context.resource.metadata.length)
        if (header != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated a plausible SNES internal header, reset vector and checksum complement",
            mapOf("mimeType" to MIME, "format" to "snes-cartridge-rom"))
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val offsets = candidateOffsets(context.length)
        for ((offset, copierHeader) in offsets) {
            val header = context.readAt(offset.toLong(), 0x40)
            val parsed = parse(header, offset, copierHeader) ?: continue
            val fields = linkedMapOf(
                "System" to "Super Nintendo Entertainment System / Super Famicom",
                "Mapping" to parsed.mapping,
                "Map mode" to "0x%02X".format(parsed.mapMode),
                "Cartridge type" to "0x%02X".format(parsed.cartridgeType),
                "Declared ROM size" to parsed.romSize,
                "Destination code" to parsed.destination,
                "Stored checksum complement" to "0x%04X".format(parsed.checksumComplement),
                "Stored checksum" to "0x%04X".format(parsed.checksum),
                "Copier header" to if (copierHeader) "512 bytes" else "None",
                "Internal header offset" to "0x%X".format(offset)
            )
            context.length?.let { fields["Image size"] = "$it bytes" }
            return MetadataRecord("snes-cartridge-rom", MIME, parsed.title.takeIf(String::isNotBlank), fields,
                listOf("The checksum-complement relationship is checked; the full ROM checksum is not recalculated."))
        }
        return null
    }

    private data class Parsed(val title: String, val mapping: String, val mapMode: Int, val cartridgeType: Int,
        val romSize: String, val destination: String, val checksumComplement: Int, val checksum: Int)

    private fun parse(bytes: ByteArray, absoluteOffset: Int, copierHeader: Boolean): Parsed? {
        if (bytes.size < 0x40) return null
        val title = NintendoBytes.text(bytes, 0, 21)
        val mapMode = bytes[0x15].toInt() and 0xff
        val layout = mapMode and 0x0f
        val modeFamily = mapMode and 0xf0
        val mapOffset = absoluteOffset - if (copierHeader) 512 else 0
        val checksumComplement = NintendoBytes.u16le(bytes, 0x1c)
        val checksum = NintendoBytes.u16le(bytes, 0x1e)
        val resetVector = NintendoBytes.u16le(bytes, 0x3c)
        if (title.length < 5 || NintendoBytes.printableScore(title) < 0.9 || modeFamily !in setOf(0x20, 0x30) ||
            layout !in setOf(0, 1, 2, 3, 5, 0x0a) || (checksumComplement xor checksum) != 0xffff || resetVector < 0x8000) return null
        val romSizeCode = bytes[0x17].toInt() and 0xff
        if (romSizeCode !in 0..15) return null
        val destinationCode = bytes[0x19].toInt() and 0xff
        val mapping = if (mapOffset == 0x7fc0) "LoROM" else "HiROM"
        val romSize = if (romSizeCode >= 7) "${1 shl (romSizeCode - 7)} MiB" else "${32 shl romSizeCode} KiB"
        val destination = if (destinationCode == 0) "Japan" else "International (code $destinationCode)"
        return Parsed(title, mapping, mapMode, bytes[0x16].toInt() and 0xff, romSize, destination, checksumComplement, checksum)
    }

    private fun findHeader(prefix: ByteArray, length: Long?): Parsed? {
        for ((offset, copierHeader) in candidateOffsets(length)) {
            if (offset > prefix.size - 0x40) continue
            parse(prefix.copyOfRange(offset, offset + 0x40), offset, copierHeader)?.let { return it }
        }
        return null
    }

    private fun candidateOffsets(length: Long?): List<Pair<Int, Boolean>> {
        val copier = length?.rem(1024) == 512L
        val bases = listOf(0x7fc0 to false, 0xffc0 to false)
        return (bases + if (copier) bases.map { (offset, _) -> offset + 512 to true } else emptyList())
            .sortedBy { it.first }
    }

    companion object { private const val MIME = "application/x-snes-rom" }
}
