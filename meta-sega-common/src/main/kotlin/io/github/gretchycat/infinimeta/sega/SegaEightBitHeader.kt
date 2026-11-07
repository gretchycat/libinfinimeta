package io.github.gretchycat.infinimeta.sega

import io.github.gretchycat.infinistream.MetadataReadContext
import io.github.gretchycat.infinistream.MetadataRecord

data class SegaEightBitHeader(
    val offset: Long,
    val header: ByteArray,
    val system: String,
    val mimeType: String,
    val regionCode: Int
) {
    fun metadata(length: Long?): MetadataRecord {
        val productRaw = (header[12].toInt() and 0xFF) or ((header[13].toInt() and 0xFF) shl 8)
        val versionByte = header[14].toInt() and 0xFF
        val product = if (versionByte ushr 4 == 0) productRaw.toString().padStart(4, '0')
            else "${versionByte ushr 4}${productRaw.toString().padStart(4, '0')}"
        val version = versionByte and 0x0F
        val sizeCode = header[15].toInt() and 0x0F
        val romSize = when (sizeCode) {
            0x0A -> 8; 0x0B -> 16; 0x0C -> 32; 0x0D -> 48; 0x0E -> 64
            0x0F -> 128; 0x00 -> 256; 0x01 -> 512; 0x02 -> 1024
            else -> null
        }
        val region = when (regionCode) {
            3 -> "Master System / Mark III Japan"
            4 -> "Master System Export"
            5 -> "Game Gear Japan"
            6 -> "Game Gear Export"
            7 -> "Game Gear International"
            else -> "Unknown (code $regionCode)"
        }
        val title = "Sega ${system.replace('-', ' ')} ROM"
        val fields = linkedMapOf(
            "System" to system.replace('-', ' ').replaceFirstChar { it.uppercase() },
            "Product code" to product,
            "Revision" to version.toString(),
            "Region" to region,
            "Stored checksum" to "0x%04X".format((header[10].toInt() and 0xFF) or ((header[11].toInt() and 0xFF) shl 8)),
            "Header offset" to "0x${offset.toString(16)}"
        )
        romSize?.let { fields["Header size code"] = "$it KiB" }
        length?.let { fields["Image size"] = "$it bytes" }
        return MetadataRecord("sega-${system}-rom", mimeType, title, fields,
            listOf("The ROM size code and stored checksum are reported as encoded; the checksum is not validated."))
    }

    companion object {
        private val headerOffsets = listOf(0x1FF0L, 0x3FF0L, 0x7FF0L)

        fun find(context: MetadataReadContext): SegaEightBitHeader? {
            for (offset in headerOffsets) {
                val bytes = context.readAt(offset, 16)
                if (bytes.size < 16 || !bytes.copyOfRange(0, 8).contentEquals("TMR SEGA".toByteArray(Charsets.US_ASCII))) continue
                val region = (bytes[15].toInt() ushr 4) and 0x0F
                val (system, mime) = when (region) {
                    3, 4 -> "master-system" to "application/x-sms-rom"
                    5, 6, 7 -> "game-gear" to "application/x-gamegear-rom"
                    else -> continue
                }
                return SegaEightBitHeader(offset, bytes, system, mime, region)
            }
            return null
        }

        fun find(prefix: ByteArray): SegaEightBitHeader? {
            for (offset in headerOffsets) {
                val at = offset.toInt()
                if (at > prefix.size - 16 || !prefix.copyOfRange(at, at + 8).contentEquals("TMR SEGA".toByteArray(Charsets.US_ASCII))) continue
                val bytes = prefix.copyOfRange(at, at + 16)
                val region = (bytes[15].toInt() ushr 4) and 0x0F
                val (system, mime) = when (region) {
                    3, 4 -> "master-system" to "application/x-sms-rom"
                    5, 6, 7 -> "game-gear" to "application/x-gamegear-rom"
                    else -> continue
                }
                return SegaEightBitHeader(offset, bytes, system, mime, region)
            }
            return null
        }
    }
}
