package io.github.gretchycat.infinimeta.nintendo.n64

import io.github.gretchycat.infinimeta.nintendo.NintendoBytes
import io.github.gretchycat.infinistream.*

class N64CapabilityProvider : CapabilityProvider {
    override val library = "meta-nintendo"
    override fun handlers(): List<CapabilityHandler> = listOf(N64RomHandler())
}

private class N64RomHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nintendo.n64", "meta-nintendo", "Nintendo 64 cartridge ROM",
        listOf(FormatSupport(setOf("application/x-n64-rom"), setOf("z64", "v64", "n64"), magic = listOf(
            MagicSignature("80371240", 0, "N64 big-endian ROM header"),
            MagicSignature("37804012", 0, "N64 16-bit byte-swapped ROM header"),
            MagicSignature("40123780", 0, "N64 32-bit little-endian ROM header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "title", "byteOrder", "productCode", "region", "version", "storedCrc1", "storedCrc2"),
        limitations = listOf("Identifies the three common N64 byte orders; does not validate cartridge CRC1/CRC2", "Compressed archival wrappers are not parsed")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val raw = context.prefix()
        val header = normalize(raw)
        val length = context.resource.metadata.length
        return if (header != null && validHeader(header) && (length == null || length >= 0x1000)) HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the N64 byte-order marker and cartridge header", mapOf("mimeType" to MIME, "format" to "n64-cartridge-rom"))
        else super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        if (context.length != null && context.length!! < 0x1000) return null
        val raw = context.readAt(0, 64)
        val bytes = normalize(raw)?.takeIf(::validHeader) ?: return null
        val title = NintendoBytes.text(bytes, 0x20, 20)
        val regionCode = bytes[0x3e].toInt() and 0xff
        val fields = linkedMapOf(
            "System" to "Nintendo 64",
            "Byte order" to byteOrder(raw),
            "Product code" to NintendoBytes.text(bytes, 0x3c, 2),
            "Manufacturer code" to NintendoBytes.text(bytes, 0x3b, 1),
            "Region code" to "%02X".format(regionCode),
            "Region" to region(regionCode),
            "Revision" to (bytes[0x3f].toInt() and 0xff).toString(),
            "Stored CRC1" to "%08X".format(NintendoBytes.u32be(bytes, 0x10)),
            "Stored CRC2" to "%08X".format(NintendoBytes.u32be(bytes, 0x14))
        )
        context.length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("n64-cartridge-rom", MIME, title.takeIf(String::isNotBlank), fields,
            listOf("Cartridge CRC1 and CRC2 are reported but not recalculated."))
    }

    private fun validHeader(bytes: ByteArray): Boolean = bytes.size >= 64 && bytes[0] == 0x80.toByte() &&
        bytes[1] == 0x37.toByte() && bytes[2] == 0x12.toByte() && bytes[3] == 0x40.toByte()

    private fun normalize(bytes: ByteArray): ByteArray? {
        if (bytes.size < 64) return null
        val order = when {
            bytes[0] == 0x80.toByte() && bytes[1] == 0x37.toByte() && bytes[2] == 0x12.toByte() && bytes[3] == 0x40.toByte() -> Order.BIG
            bytes[0] == 0x37.toByte() && bytes[1] == 0x80.toByte() && bytes[2] == 0x40.toByte() && bytes[3] == 0x12.toByte() -> Order.PAIR_SWAPPED
            bytes[0] == 0x40.toByte() && bytes[1] == 0x12.toByte() && bytes[2] == 0x37.toByte() && bytes[3] == 0x80.toByte() -> Order.WORD_REVERSED
            else -> return null
        }
        return ByteArray(64) { index ->
            val source = when (order) {
                Order.BIG -> index
                Order.PAIR_SWAPPED -> index xor 1
                Order.WORD_REVERSED -> (index and -4) + (3 - (index and 3))
            }
            bytes[source]
        }
    }

    private fun byteOrder(bytes: ByteArray) = when {
        bytes.size >= 4 && bytes[0] == 0x80.toByte() && bytes[1] == 0x37.toByte() -> "Big-endian (.z64)"
        bytes.size >= 4 && bytes[0] == 0x37.toByte() && bytes[1] == 0x80.toByte() -> "16-bit byte-swapped (.v64)"
        else -> "32-bit little-endian (.n64)"
    }

    private fun region(code: Int) = when (code.toChar()) {
        'A' -> "Asia (NTSC)"; 'B' -> "Brazil"; 'C' -> "China"; 'D' -> "Germany"; 'E' -> "North America"
        'F' -> "France"; 'G' -> "Gateway 64 (NTSC)"; 'H' -> "Netherlands"; 'I' -> "Italy"; 'J' -> "Japan"
        'K' -> "Korea"; 'L' -> "Gateway 64 (PAL)"; 'P' -> "Europe (PAL)"; 'U' -> "Australia"; 'W' -> "Scandinavia"
        'X' -> "Europe"; 'Y' -> "Europe"; '7' -> "Beta"; else -> "Unknown"
    }

    private enum class Order { BIG, PAIR_SWAPPED, WORD_REVERSED }

    companion object { private const val MIME = "application/x-n64-rom" }
}
