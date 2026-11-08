package io.github.gretchycat.infinimeta.nintendo.ds

import io.github.gretchycat.infinimeta.nintendo.NintendoBytes
import io.github.gretchycat.infinistream.*

class NintendoDsCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(NintendoDsRomHandler())
}

private class NintendoDsRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.ds", "meta-nintendo", "Nintendo DS / DSi game card ROM",
        listOf(FormatSupport(setOf("application/x-nintendo-ds-rom"), setOf("nds", "dsi"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "gameCode", "makerCode", "unitCode", "romSize", "headerCrc", "logoCrc"),
        limitations = listOf("Verifies the Nintendo-logo and header CRC16 fields", "Does not parse the Nitro filesystem or localized banner titles")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = parse(context.prefix())
        if (header != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Nintendo logo CRC, header CRC and ARM9 ROM range",
            mapOf("mimeType" to MIME, "format" to "nintendo-ds-card-rom"))
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val header = parse(context.readAt(0, HEADER_SIZE)) ?: return null
        val fields = linkedMapOf(
            "System" to if (header.unitCode == 0) "Nintendo DS" else "Nintendo DS / DSi",
            "Game code" to header.gameCode,
            "Maker code" to header.makerCode,
            "Unit code" to "0x%02X".format(header.unitCode),
            "Declared used ROM size" to "${header.romSize} bytes",
            "ARM9 ROM offset" to "0x%X".format(header.arm9Offset),
            "Header CRC16" to "0x%04X (valid)".format(header.headerCrc),
            "Nintendo-logo CRC16" to "0x%04X (valid)".format(header.logoCrc)
        )
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("nintendo-ds-card-rom", MIME, header.title.takeIf(String::isNotBlank), fields)
    }

    private data class Header(val title: String, val gameCode: String, val makerCode: String, val unitCode: Int,
        val romSize: Long, val arm9Offset: Long, val headerCrc: Int, val logoCrc: Int)

    private fun parse(bytes: ByteArray): Header? {
        if (bytes.size < HEADER_SIZE) return null
        val title = NintendoBytes.text(bytes, 0, 12)
        val gameCode = NintendoBytes.text(bytes, 0x0c, 4)
        val makerCode = NintendoBytes.text(bytes, 0x10, 2)
        val unitCode = bytes[0x12].toInt() and 0xff
        val romSize = NintendoBytes.u32le(bytes, 0x80)
        val declaredHeaderSize = NintendoBytes.u32le(bytes, 0x84)
        val arm9Offset = NintendoBytes.u32le(bytes, 0x20)
        val logoCrc = NintendoBytes.u16le(bytes, 0x15c)
        val headerCrc = NintendoBytes.u16le(bytes, 0x15e)
        if (title.isBlank() || NintendoBytes.printableScore(title) < 0.75 || !gameCode.all { it.isLetterOrDigit() } ||
            unitCode !in setOf(0, 2, 3) || romSize < HEADER_SIZE || declaredHeaderSize !in 0x200L..0x4000L ||
            arm9Offset < 0x200 || arm9Offset >= (romSize.takeIf { it > 0 } ?: Long.MAX_VALUE)) return null
        val calculatedLogoCrc = crc16(bytes, 0xc0, 0x9c)
        val calculatedHeaderCrc = crc16(bytes, 0, 0x15e)
        if (logoCrc != calculatedLogoCrc || logoCrc != 0xcf56 || headerCrc != calculatedHeaderCrc) return null
        return Header(title, gameCode, makerCode, unitCode, romSize, arm9Offset, headerCrc, logoCrc)
    }

    private fun crc16(bytes: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xffff
        for (index in offset until offset + length) {
            crc = crc xor (bytes[index].toInt() and 0xff)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
        }
        return crc and 0xffff
    }

    companion object {
        private const val MIME = "application/x-nintendo-ds-rom"
        private const val HEADER_SIZE = 0x200
    }
}
