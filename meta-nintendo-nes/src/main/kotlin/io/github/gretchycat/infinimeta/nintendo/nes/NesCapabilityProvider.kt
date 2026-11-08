package io.github.gretchycat.infinimeta.nintendo.nes

import io.github.gretchycat.infinistream.*

class NesCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(NesRomHandler())
}

private class NesRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.nes", "meta-nintendo", "Nintendo Entertainment System ROM",
        listOf(FormatSupport(setOf("application/x-nes-rom"), setOf("nes"), magic = listOf(
            MagicSignature("4E45531A", 0, "iNES / NES 2.0 header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "headerVersion", "mapper", "submapper", "programRom", "characterRom", "trainer", "battery", "mirroring"),
        limitations = listOf("Supports iNES and NES 2.0 headers; legacy UNIF and headerless dumps are not parsed", "Does not validate game payload checksums")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val prefix = context.prefix()
        if (!prefix.startsWith(INES_MAGIC)) return super.probe(context)
        val header = parse(prefix, context.resource.metadata.length)
        return if (header != null) HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the iNES signature, declared payload size and header fields",
            mapOf("mimeType" to "application/x-nes-rom", "format" to header.formatId))
        else HandlerProbe(candidate(context), descriptor.operations, "iNES signature is present but the header or declared payload size is invalid")
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val header = parse(context.readAt(0, 16), context.length) ?: return null
        val fields = linkedMapOf(
            "System" to "Nintendo Entertainment System",
            "Header version" to header.version,
            "Mapper" to header.mapper.toString(),
            "Program ROM" to sizeText(header.prgBytes),
            "Character ROM" to if (header.chrBytes == 0L) "None (CHR RAM expected)" else sizeText(header.chrBytes),
            "Trainer" to if (header.trainer) "Present (512 bytes)" else "Absent",
            "Battery-backed memory" to if (header.battery) "Yes" else "No",
            "Mirroring" to header.mirroring
        )
        if (header.submapper != null) fields["Submapper"] = header.submapper.toString()
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord(header.formatId, "application/x-nes-rom", fields = fields)
    }

    private fun candidate(context: CapabilityProbeContext) =
        if (descriptor.formats.any { it.score(context.resource) > 0 }) MatchConfidence.CANDIDATE else MatchConfidence.NONE

    private data class Header(val formatId: String, val version: String, val mapper: Int, val submapper: Int?,
        val prgBytes: Long, val chrBytes: Long, val trainer: Boolean, val battery: Boolean, val mirroring: String)

    private fun parse(bytes: ByteArray, length: Long?): Header? {
        if (!bytes.startsWith(INES_MAGIC) || bytes.size < 16) return null
        val flags6 = bytes[6].toInt() and 0xff
        val flags7 = bytes[7].toInt() and 0xff
        val isNes2 = flags7 and 0x0c == 0x08
        val trainer = flags6 and 0x04 != 0
        val prg = romSize(bytes[4].toInt() and 0xff, if (isNes2) bytes[9].toInt() and 0x0f else 0, 16 * 1024L, isNes2) ?: return null
        val chr = romSize(bytes[5].toInt() and 0xff, if (isNes2) (bytes[9].toInt() ushr 4) and 0x0f else 0, 8 * 1024L, isNes2) ?: return null
        if (prg <= 0 || chr < 0) return null
        val payloadStart = 16L + if (trainer) 512L else 0L
        if (prg > Long.MAX_VALUE - payloadStart || chr > Long.MAX_VALUE - payloadStart - prg) return null
        val requiredLength = payloadStart + prg + chr
        if (length != null && length < requiredLength) return null
        var mapper = (flags6 ushr 4) or (flags7 and 0xf0)
        val submapper = if (isNes2) (bytes[8].toInt() ushr 4) and 0x0f else null
        if (isNes2) mapper = mapper or ((bytes[8].toInt() and 0x0f) shl 8)
        val mirroring = when {
            flags6 and 0x08 != 0 -> "Four-screen"
            flags6 and 0x01 != 0 -> "Vertical"
            else -> "Horizontal"
        }
        return Header(if (isNes2) "nes-2.0-rom" else "ines-rom", if (isNes2) "NES 2.0" else "iNES",
            mapper, submapper, prg, chr, trainer, flags6 and 0x02 != 0, mirroring)
    }

    private fun romSize(lsb: Int, msb: Int, unit: Long, nes2: Boolean): Long? {
        if (nes2 && msb == 0x0f) {
            val exponent = lsb ushr 2
            if (exponent >= 63) return null
            val value = (1L shl exponent) * (((lsb and 3) * 2L) + 1L)
            return value.takeIf { it > 0 }
        }
        return ((msb shl 8) or lsb).toLong() * unit
    }

    private fun sizeText(bytes: Long) = if (bytes % (1024 * 1024L) == 0L) "${bytes / (1024 * 1024L)} MiB ($bytes bytes)"
        else if (bytes % 1024L == 0L) "${bytes / 1024L} KiB ($bytes bytes)" else "$bytes bytes"

    private fun ByteArray.startsWith(expected: ByteArray) = expected.indices.all { this.getOrNull(it) == expected[it] }

    companion object { private val INES_MAGIC = byteArrayOf(0x4e, 0x45, 0x53, 0x1a) }
}
