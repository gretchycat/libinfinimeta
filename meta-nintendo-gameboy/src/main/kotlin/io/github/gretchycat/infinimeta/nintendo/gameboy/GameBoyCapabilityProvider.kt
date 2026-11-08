package io.github.gretchycat.infinimeta.nintendo.gameboy

import io.github.gretchycat.infinimeta.nintendo.NintendoBytes
import io.github.gretchycat.infinistream.*

class GameBoyCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(GameBoyRomHandler())
}

private class GameBoyRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.gameboy", "meta-nintendo", "Nintendo Game Boy / Game Boy Color cartridge ROM",
        listOf(FormatSupport(setOf("application/x-gameboy-rom"), setOf("gb", "gbc", "sgb"), magic = listOf(
            MagicSignature(LOGO_HEX, 0x104, "Nintendo Game Boy logo")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "cartridgeType", "romSize", "ramSize", "region", "headerChecksum", "licensee"),
        limitations = listOf("The Nintendo logo and header checksum are validated; ROM payload integrity is not checked", "Super Game Boy border metadata is not parsed")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val length = context.resource.metadata.length
        val header = parse(context.prefix())?.takeIf { length == null || length >= 0x8000 }
        if (header != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Nintendo logo and Game Boy header checksum",
            mapOf("mimeType" to "application/x-gameboy-rom", "format" to header.formatId))
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        if (context.length != null && context.length!! < 0x8000) return null
        return parse(context.readAt(0, 0x150))?.let { header ->
        val fields = linkedMapOf(
            "System" to header.system,
            "Cartridge type" to header.cartridgeType,
            "ROM size code" to "${header.romSize} (${header.romCode})",
            "RAM size" to header.ramSize,
            "Region" to header.region,
            "Header checksum" to "0x%02X".format(header.checksum),
            "Header checksum valid" to "Yes",
            "Licensee code" to header.licensee
        )
        context.length?.let { fields["Image size"] = "$it bytes" }
            MetadataRecord(header.formatId, "application/x-gameboy-rom", header.title.takeIf(String::isNotBlank), fields)
        }
    }

    private data class Header(val formatId: String, val system: String, val title: String, val cartridgeType: String,
        val romSize: String, val romCode: String, val ramSize: String, val region: String, val checksum: Int, val licensee: String)

    private fun parse(bytes: ByteArray): Header? {
        if (bytes.size < 0x150 || !NintendoBytes.has(bytes, 0x104, LOGO)) return null
        var checksum = 0
        for (i in 0x134..0x14c) checksum = (checksum - (bytes[i].toInt() and 0xff) - 1) and 0xff
        val stored = bytes[0x14d].toInt() and 0xff
        if (checksum != stored) return null
        val cgbFlag = bytes[0x143].toInt() and 0xff
        val titleLength = if (cgbFlag and 0x80 != 0) 11 else 16
        val title = NintendoBytes.text(bytes, 0x134, titleLength)
        val type = cartridgeType(bytes[0x147].toInt() and 0xff)
        val romCode = bytes[0x148].toInt() and 0xff
        val romSize = when (romCode) {
            in 0..8 -> "${32L shl romCode} KiB"
            0x52 -> "1,120 KiB"
            0x53 -> "1,152 KiB"
            0x54 -> "1,280 KiB"
            else -> "Unknown code 0x%02X".format(romCode)
        }
        val ramSize = when (bytes[0x149].toInt() and 0xff) {
            0 -> "None"
            1 -> "2 KiB"
            2 -> "8 KiB"
            3 -> "32 KiB"
            4 -> "128 KiB"
            5 -> "64 KiB"
            else -> "Unknown code 0x%02X".format(bytes[0x149].toInt() and 0xff)
        }
        val region = when (bytes[0x14a].toInt() and 0xff) { 0 -> "Japan"; 1 -> "International"; else -> "Unknown code 0x%02X".format(bytes[0x14a].toInt() and 0xff) }
        val licensee = if ((bytes[0x14b].toInt() and 0xff) == 0x33)
            NintendoBytes.text(bytes, 0x144, 2) else "%02X".format(bytes[0x14b].toInt() and 0xff)
        val system = when (cgbFlag) {
            0x80 -> "Game Boy / Game Boy Color compatible"
            0xc0 -> "Game Boy Color exclusive"
            else -> "Game Boy"
        }
        return Header("gameboy-cartridge-rom", system, title, type, romSize, "0x%02X".format(romCode), ramSize,
            region, stored, licensee)
    }

    private fun cartridgeType(code: Int): String = when (code) {
        0x00 -> "ROM only"; 0x01 -> "MBC1"; 0x02 -> "MBC1 + RAM"; 0x03 -> "MBC1 + RAM + battery"
        0x05 -> "MBC2"; 0x06 -> "MBC2 + battery"; 0x08 -> "ROM + RAM"; 0x09 -> "ROM + RAM + battery"
        0x0b -> "MMM01"; 0x0c -> "MMM01 + RAM"; 0x0d -> "MMM01 + RAM + battery"
        0x0f -> "MBC3 + timer + battery"; 0x10 -> "MBC3 + timer + RAM + battery"
        0x11 -> "MBC3"; 0x12 -> "MBC3 + RAM"; 0x13 -> "MBC3 + RAM + battery"
        0x19 -> "MBC5"; 0x1a -> "MBC5 + RAM"; 0x1b -> "MBC5 + RAM + battery"
        0x1c -> "MBC5 + rumble"; 0x1d -> "MBC5 + rumble + RAM"; 0x1e -> "MBC5 + rumble + RAM + battery"
        0x20 -> "MBC6"; 0x22 -> "MBC7 + sensor + rumble + RAM + battery"
        0xfc -> "Pocket Camera"; 0xfd -> "Bandai TAMA5"; 0xfe -> "HuC3"; 0xff -> "HuC1 + RAM + battery"
        else -> "Unknown code 0x%02X".format(code)
    }

    companion object {
        private const val LOGO_HEX = "CEED6666CC0D000B03730083000C000D0008111F8889000EDCCC6EE6DDDDD999BBBB67636E0EECCCDDDC999FBBB9333E"
        private val LOGO = LOGO_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
