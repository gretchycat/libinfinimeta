package io.github.gretchycat.infinimeta.threedo.opera

import io.github.gretchycat.infinistream.*
import java.nio.charset.StandardCharsets

class ThreeDoOperaCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(OperaVolumeHandler())
}

private class OperaVolumeHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.3do.opera-volume", "meta-consoles", "3DO Opera filesystem volume",
        listOf(FormatSupport(setOf(MIME), setOf("3do", "opera"),
            magic = listOf(MagicSignature("015A5A5A5A5A", 0, "Opera volume header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("label", "comment", "version", "blockSize", "blockCount", "rootDirectoryBlocks"),
        limitations = listOf("Validates the cooked 2048-byte-block Opera volume header; does not inspect the disc boot tags or raw CD sector framing")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val parsed = parse(context.prefix(), context.resource.metadata.length)
            ?: return super.probe(context)
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the 3DO Opera volume signature, geometry, and root-directory copy range",
            mapOf("mimeType" to MIME, "format" to "3do-opera", "label" to parsed.label,
                "blockSize" to parsed.blockSize.toString(), "blockCount" to parsed.blockCount.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val length = context.length ?: return null
        val header = context.readAt(0, 2048)
        val parsed = parse(header, length) ?: return null
        return MetadataRecord("3do-opera", MIME, parsed.label.ifBlank { "3DO Opera disc" }, linkedMapOf(
            "System" to "The 3DO Company 3DO Interactive Multiplayer",
            "Filesystem" to "Opera",
            "Volume label" to parsed.label.ifBlank { "Not set" },
            "Comment" to parsed.comment.ifBlank { "Not set" },
            "Format version" to parsed.version.toString(),
            "Block size" to "${parsed.blockSize} bytes",
            "Declared blocks" to parsed.blockCount.toString(),
            "Root directory blocks" to parsed.rootBlocks.toString(),
            "Image size" to "$length bytes"
        ))
    }

    private data class Volume(val label: String, val comment: String, val version: Int,
        val blockSize: Long, val blockCount: Long, val rootBlocks: Long)

    private fun parse(bytes: ByteArray, length: Long?): Volume? {
        if (bytes.size < HEADER_MIN_SIZE || bytes[0].toInt() != 1 ||
            !bytes.copyOfRange(1, 6).contentEquals("ZZZZZ".toByteArray(StandardCharsets.US_ASCII)) || length == null) return null
        val blockSize = u32be(bytes, 0x4c)
        val blockCount = u32be(bytes, 0x50)
        val rootBlocks = u32be(bytes, 0x58)
        val copiesLast = u32be(bytes, 0x60)
        if (blockSize != OPERA_BLOCK_SIZE.toLong() || blockCount < 2 || blockCount > length / blockSize ||
            rootBlocks !in 1..MAX_ROOT_BLOCKS || copiesLast !in 0..MAX_COPIES_LAST_INDEX) return null
        val copyCount = copiesLast + 1
        if (0x64L + copyCount * 4L > bytes.size) return null
        val copies = (0 until copyCount.toInt()).map { u32be(bytes, 0x64 + it * 4) }
        if (copies.any { it <= 0 || it >= blockCount || it + rootBlocks > blockCount }) return null
        return Volume(text(bytes, 40, 32), text(bytes, 8, 32), bytes[6].toInt() and 255,
            blockSize, blockCount, rootBlocks)
    }

    private fun text(bytes: ByteArray, offset: Int, size: Int): String =
        String(bytes, offset, size, StandardCharsets.ISO_8859_1).substringBefore('\u0000')
            .filterNot(Char::isISOControl).trim()

    private fun u32be(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> (value shl 8) or (bytes[offset + index].toLong() and 255) }

    companion object {
        private const val MIME = "application/x-3do-opera-image"
        private const val OPERA_BLOCK_SIZE = 2048
        private const val HEADER_MIN_SIZE = 0x68
        private const val MAX_ROOT_BLOCKS = 4096L
        private const val MAX_COPIES_LAST_INDEX = 15L
    }
}
