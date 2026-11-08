package io.github.gretchycat.infinimeta.nintendo.gba

import io.github.gretchycat.infinimeta.nintendo.NintendoBytes
import io.github.gretchycat.infinistream.*

class GbaCapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(GbaRomHandler())
}

private class GbaRomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.gba", "meta-nintendo", "Game Boy Advance cartridge ROM",
        listOf(FormatSupport(setOf("application/x-gba-rom"), setOf("gba", "agb", "mb"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "gameCode", "makerCode", "unitCode", "version", "headerChecksum"),
        limitations = listOf("Nintendo logo, fixed header byte and complement checksum are validated", "Does not parse multiboot header extensions or ROM payload checksums")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = parse(context.prefix())
        if (header != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the GBA fixed header byte and complement checksum",
            mapOf("mimeType" to MIME, "format" to "gba-cartridge-rom"))
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val header = parse(context.readAt(0, HEADER_SIZE)) ?: return null
        val fields = linkedMapOf(
            "System" to "Game Boy Advance",
            "Game code" to header.gameCode,
            "Maker code" to header.makerCode,
            "Main unit code" to "0x%02X".format(header.unitCode),
            "Device type" to "0x%02X".format(header.deviceType),
            "Software version" to header.version.toString(),
            "Header complement checksum" to "0x%02X".format(header.checksum),
            "Header checksum valid" to "Yes"
        )
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("gba-cartridge-rom", MIME, header.title.takeIf(String::isNotBlank), fields)
    }

    private data class Header(val title: String, val gameCode: String, val makerCode: String,
        val unitCode: Int, val deviceType: Int, val version: Int, val checksum: Int)

    private fun parse(bytes: ByteArray): Header? {
        if (bytes.size < HEADER_SIZE || !NintendoBytes.has(bytes, 4, LOGO)) return null
        if ((bytes[0xb2].toInt() and 0xff) != 0x96) return null
        val checksum = (0xa0..0xbc).sumOf { bytes[it].toInt() and 0xff }
        val expected = (-checksum - 0x19) and 0xff
        val stored = bytes[0xbd].toInt() and 0xff
        if (expected != stored) return null
        val title = NintendoBytes.text(bytes, 0xa0, 12)
        val gameCode = NintendoBytes.text(bytes, 0xac, 4)
        val makerCode = NintendoBytes.text(bytes, 0xb0, 2)
        if (title.isBlank() || NintendoBytes.printableScore(title) < 0.75 || !gameCode.all { it.isLetterOrDigit() }) return null
        return Header(title, gameCode, makerCode, bytes[0xb3].toInt() and 0xff,
            bytes[0xb4].toInt() and 0xff, bytes[0xbc].toInt() and 0xff, stored)
    }

    companion object {
        private const val MIME = "application/x-gba-rom"
        private const val HEADER_SIZE = 0xc0
        private const val LOGO_HEX = "24FFAE51699AA2213D84820A84E409AD11248B98C0817F21A352BE199309CE2010464A4AF82731EC58C7E83382E3CEBF85F4DF94CE4B09C194568AC01372A7FC9F844D73A3CA9A615897A327FC039876231DC7610304AE56BF38840040A70EFDFF52FE036F9530F197FBC08560D68025A963BE03014E38E2F9A234FFBB3E0344780090CB88113A9465C07C6387F03CAFD625E48B380AAC7221D4F807"
        private val LOGO = LOGO_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
