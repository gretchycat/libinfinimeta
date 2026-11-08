package io.github.gretchycat.infinimeta.apple2.prodos

import io.github.gretchycat.infinistream.*

/** Content validator for ProDOS volumes; a .po suffix alone is never treated as a disk image. */
class AppleProDosCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(ProDosHandler())
}

private class ProDosHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.apple2.prodos", "meta-consoles", "Apple II ProDOS volume",
        listOf(FormatSupport(setOf(MIME), setOf("po"), magic = VOLUME_ENTRY_MARKERS)),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("volumeName", "totalBlocks", "rootDirectoryBlock", "entrySize", "entriesPerBlock"),
        limitations = listOf("Identifies valid ProDOS volume headers in 140 KiB sector images; Apple DOS 3.3 is browsed by Vault but has no fixed magic signature", "Nibble-encoded WOZ/NIB and 2IMG wrappers are not decoded"),
        attributes = mapOf("probePrefixBytes" to "4096")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val parsed = parse(context.prefix(), context.resource.metadata.length)
            ?: return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated ProDOS volume-directory header and geometry", mapOf(
                "mimeType" to MIME, "format" to "apple2-prodos", "volumeName" to parsed.name,
                "totalBlocks" to parsed.blocks.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val parsed = parse(context.readAt(0, 4096), context.length) ?: return null
        return MetadataRecord("apple2-prodos", MIME, parsed.name, linkedMapOf(
            "System" to "Apple II ProDOS", "Volume name" to parsed.name,
            "Declared volume blocks" to parsed.blocks.toString(),
            "Root directory entry size" to "${parsed.entrySize} bytes",
            "Entries per directory block" to parsed.entriesPerBlock.toString(),
            "Image size" to "${context.length ?: 0} bytes"
        ))
    }

    private data class Volume(val name: String, val blocks: Int, val entrySize: Int, val entriesPerBlock: Int)

    private fun parse(bytes: ByteArray, length: Long?): Volume? {
        if (bytes.size < MIN_HEADER_BYTES || length == null || length < BLOCK_SIZE * 3L) return null
        val p = ROOT_BLOCK * BLOCK_SIZE
        val storageAndName = u8(bytes, p + DIRECTORY_HEADER_OFFSET)
        val storage = storageAndName ushr 4
        val nameLength = storageAndName and 0x0f
        if (storage != STORAGE_VOLUME || nameLength !in 1..15) return null
        val nameBytes = bytes.copyOfRange(p + DIRECTORY_HEADER_OFFSET + 1, p + DIRECTORY_HEADER_OFFSET + 1 + nameLength)
        if (nameBytes.any { (it.toInt() and 255) !in 0x21..0x7e }) return null
        val name = String(nameBytes, Charsets.US_ASCII)
        val entrySize = u8(bytes, p + DIRECTORY_HEADER_OFFSET + 0x23)
        val entriesPerBlock = u8(bytes, p + DIRECTORY_HEADER_OFFSET + 0x24)
        val blocks = u16le(bytes, p + DIRECTORY_HEADER_OFFSET + 0x29)
        val actualBlocks = length / BLOCK_SIZE
        if (entrySize != ENTRY_SIZE || entriesPerBlock != ENTRIES_PER_BLOCK ||
            blocks !in 3..actualBlocks.toInt() || actualBlocks !in 280..MAX_BLOCKS) return null
        return Volume(name, blocks, entrySize, entriesPerBlock)
    }

    private fun u8(bytes: ByteArray, p: Int) = bytes[p].toInt() and 255
    private fun u16le(bytes: ByteArray, p: Int) = u8(bytes, p) or (u8(bytes, p + 1) shl 8)

    companion object {
        private const val MIME = "application/x-apple2-disk-image"
        private const val BLOCK_SIZE = 512
        private const val ROOT_BLOCK = 2
        private const val DIRECTORY_HEADER_OFFSET = 4
        private const val MIN_HEADER_BYTES = ROOT_BLOCK * BLOCK_SIZE + DIRECTORY_HEADER_OFFSET + 0x2b
        private const val ENTRY_SIZE = 39
        private const val ENTRIES_PER_BLOCK = 13
        private const val STORAGE_VOLUME = 0x0f
        private const val MAX_BLOCKS = 65535
        private val VOLUME_ENTRY_MARKERS = (1..15).map {
            MagicSignature("%02X".format(0xf0 or it), (ROOT_BLOCK * BLOCK_SIZE + DIRECTORY_HEADER_OFFSET).toLong(), "ProDOS volume directory marker")
        }
    }
}
