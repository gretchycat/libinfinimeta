package io.github.gretchycat.infinimeta.xbox.xvd

import io.github.gretchycat.infinistream.*

class XvdCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(XvdHandler())
}

private class XvdHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.xbox.xvd", "meta-consoles", "Microsoft Xbox One / Series XVD or XVC volume",
        listOf(FormatSupport(setOf("application/x-xbox-xvd"), setOf("xvd", "xvc"),
            magic = listOf(MagicSignature("6D7366742D787664", 0x200, "msft-xvd")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("formatVersion", "volumeFlags", "driveSize", "volumeType", "contentType", "blockSize", "userDataSize", "xvcDataSize"),
        limitations = listOf("Reads public volume header fields only; encrypted NTFS contents, XVC region maps and integrity hash trees are not decoded")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (b.size < SIGNATURE_OFFSET + MAGIC.size || !b.matches(SIGNATURE_OFFSET, MAGIC)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "msft-xvd signature found but the header version or declared volume size is invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated the Xbox virtual-disk header",
            mapOf("mimeType" to MIME, "format" to "xbox-xvd", "volumeType" to h.type.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, HEADER_SIZE), context.length) ?: return null
        return MetadataRecord("xbox-xvd", MIME, fields = linkedMapOf(
            "System" to "Xbox One / Xbox Series X|S",
            "Format version" to h.version.toString(),
            "Volume flags" to "0x%08X".format(h.flags),
            "Drive size" to "${h.driveSize} bytes",
            "Volume type" to h.type.toString(),
            "Content type" to h.contentType.toString(),
            "Embedded XVD length" to "${h.embedded} bytes",
            "User data length" to "${h.userData} bytes",
            "XVC data length" to "${h.xvcData} bytes",
            "Block size" to "${h.blockSize} bytes",
            "Image size" to "${context.length ?: 0} bytes"
        ), warnings = listOf("The payload remains encrypted; filesystem browsing is not supported"))
    }

    private data class Header(val flags: Long, val version: Long, val driveSize: Long, val type: Long,
        val contentType: Long, val embedded: Long, val userData: Long, val xvcData: Long, val blockSize: Long)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (b.size < HEADER_SIZE || !b.matches(SIGNATURE_OFFSET, MAGIC)) return null
        val version = u32le(b, 0x20c)
        val driveSize = u64le(b, 0x218) ?: return null
        val type = u32le(b, 0x280); val contentType = u32le(b, 0x284)
        val embedded = u32le(b, 0x288); val userData = u32le(b, 0x28c); val xvc = u32le(b, 0x290); val block = u32le(b, 0x298)
        val actualLength = length ?: return null
        if (version !in 1..16 || driveSize == 0L || driveSize > MAX_DRIVE_SIZE || actualLength < HEADER_SIZE ||
            block != 0L && block !in setOf(0x200L, 0x1000L, 0xaa000L) ||
            embedded > actualLength || userData > actualLength || xvc > actualLength) return null
        return Header(u32le(b, 0x208), version, driveSize, type, contentType, embedded, userData, xvc, block)
    }

    private fun u32le(b: ByteArray, p: Int): Long = (b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
        ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24)
    private fun u64le(b: ByteArray, p: Int): Long? {
        if (b.size < p + 8 || b[p + 7].toInt() and 0x80 != 0) return null
        var v = 0L
        for (i in 0 until 8) v = v or ((b[p + i].toLong() and 255) shl (8 * i))
        return v
    }
    private fun ByteArray.matches(p: Int, value: ByteArray) = p >= 0 && p + value.size <= size && value.indices.all { this[p + it] == value[it] }
    companion object {
        private const val MIME = "application/x-xbox-xvd"
        private const val SIGNATURE_OFFSET = 0x200
        private const val HEADER_SIZE = 0x1000
        private const val MAX_DRIVE_SIZE = 1L shl 60
        private val MAGIC = "msft-xvd".toByteArray(Charsets.US_ASCII)
    }
}
