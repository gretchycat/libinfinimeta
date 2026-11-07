package io.github.gretchycat.infinimeta.sega.vmu

import io.github.gretchycat.infinimeta.sega.SegaText
import io.github.gretchycat.infinistream.*

class VmuCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(VmsHandler())
}

/** Parses the standard header embedded at byte zero (data saves) or 0x200 (VMU mini-games). */
private class VmsHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.vmu.vms", "meta-sega", "Dreamcast VMU VMS save / mini-game",
        listOf(FormatSupport(setOf("application/x-dreamcast-vms"), setOf("vms"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("description", "applicationId", "iconCount", "iconSpeed", "eyecatchType", "payloadSize"),
        limitations = listOf("Parses the VMS description header; VMU filesystem images and VMI companion files are not parsed"))

    private data class Header(val offset: Int, val bytes: ByteArray)
    private fun parse(bytes: ByteArray): Header? {
        for (offset in listOf(0, 0x200)) {
            if (offset > bytes.size - 0x80) continue
            val iconCount = SegaText.le16(bytes, offset + 0x40)
            val eyecatch = SegaText.le16(bytes, offset + 0x44)
            val payload = (bytes[offset + 0x48].toLong() and 0xff) or
                ((bytes[offset + 0x49].toLong() and 0xff) shl 8) or
                ((bytes[offset + 0x4A].toLong() and 0xff) shl 16) or
                ((bytes[offset + 0x4B].toLong() and 0xff) shl 24)
            if (iconCount in 1..3 && eyecatch in 0..3 && payload <= 32L * 1024 * 1024) return Header(offset, bytes.copyOfRange(offset, offset + 0x80))
        }
        return null
    }
    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = parse(context.prefix()) ?: return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated VMS header fields",
            mapOf("mimeType" to "application/x-dreamcast-vms", "format" to "dreamcast-vms"))
    }
    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val bytes = context.readAt(0, 0x280)
        val header = parse(bytes) ?: return null
        val b = header.bytes
        val iconCount = SegaText.le16(b, 0x40)
        val iconSpeed = SegaText.le16(b, 0x42)
        val eyecatch = SegaText.le16(b, 0x44)
        val payloadSize = SegaText.le16(b, 0x48) or (SegaText.le16(b, 0x4a) shl 16)
        val fields = linkedMapOf(
            "Description" to SegaText.field(b, 0, 16),
            "Dreamcast menu description" to SegaText.field(b, 0x10, 32),
            "Creating application" to SegaText.field(b, 0x30, 16),
            "Icon count" to iconCount.toString(),
            "Icon animation speed" to iconSpeed.toString(),
            "Eyecatch type" to eyecatch.toString(),
            "Payload bytes" to payloadSize.toString(),
            "Header offset" to "0x${header.offset.toString(16)}"
        )
        return MetadataRecord("dreamcast-vms", "application/x-dreamcast-vms", fields["Description"], fields,
            listOf("VMS checksum is not verified."))
    }
}
