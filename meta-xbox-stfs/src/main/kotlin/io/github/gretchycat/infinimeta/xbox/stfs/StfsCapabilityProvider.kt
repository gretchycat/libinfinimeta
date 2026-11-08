package io.github.gretchycat.infinimeta.xbox.stfs

import io.github.gretchycat.infinistream.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class StfsCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(StfsHandler())
}

/** Xbox 360 STFS metadata parser. It does not verify signatures or decrypt the package. */
private class StfsHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.xbox.stfs", "meta-consoles", "Xbox 360 STFS content package",
        listOf(FormatSupport(setOf(MIME), setOf("con", "live", "pirs"), magic = listOf(
            MagicSignature("434F4E20", 0, "Xbox console-signed package"),
            MagicSignature("4C495645", 0, "Xbox LIVE package"),
            MagicSignature("50495253", 0, "Xbox PIRS package")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("signature", "title", "titleId", "contentType", "contentSize", "fileCount", "metadataVersion"),
        limitations = listOf("Validates the STFS header and metadata bounds only; does not verify RSA/SHA-1 signatures, decrypt blocks, or browse package files")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val bytes = context.prefix()
        if (!hasSignature(bytes)) return super.probe(context)
        val parsed = parse(bytes, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "STFS signature is present but the package metadata header is inconsistent")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated Xbox STFS signature, version, title metadata, and declared header bounds",
            mapOf("mimeType" to MIME, "format" to "xbox-360-stfs", "titleId" to parsed.titleId, "title" to parsed.title))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val data = context.readAt(0, HEADER_BYTES)
        val parsed = parse(data, context.length) ?: return null
        val fields = linkedMapOf(
            "System" to "Xbox 360",
            "Package signature" to parsed.signature,
            "Package class" to when (parsed.signature) { "CON " -> "Console signed"; "LIVE" -> "Xbox LIVE"; else -> "Microsoft PIRS" },
            "Content type" to parsed.contentTypeLabel,
            "Title ID" to parsed.titleId,
            "Content size" to "${parsed.contentSize} bytes",
            "Data file count" to parsed.fileCount.toString(),
            "Metadata version" to parsed.version.toString(),
            "Package header size" to "${parsed.headerSize} bytes"
        )
        return MetadataRecord("xbox-360-stfs", MIME, parsed.title.takeIf(String::isNotBlank), fields,
            listOf("Package signatures and encrypted file contents are not verified or extracted."))
    }

    private data class Header(val signature: String, val title: String, val titleId: String, val contentType: Long,
        val contentTypeLabel: String, val contentSize: Long, val fileCount: Long, val version: Long, val headerSize: Long)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!hasSignature(b) || b.size < MIN_HEADER || length == null || length < MIN_HEADER) return null
        val headerSize = u32be(b, 0x340)
        val contentType = u32be(b, 0x344)
        val version = u32be(b, 0x348)
        val contentSize = u64be(b, 0x34c) ?: return null
        val descriptorSize = b[0x379].toInt() and 255
        val fileCount = u32be(b, 0x39d)
        if (headerSize !in MIN_HEADER.toLong()..length || version !in 1L..2L || descriptorSize != 0x24 ||
            fileCount > MAX_FILES || headerSize < DISPLAY_NAME_OFFSET + DISPLAY_NAME_BYTES) return null
        val titleId = "%08X".format(u32be(b, 0x360))
        if (titleId == "00000000" || titleId == "FFFFFFFF") return null
        val title = utf8(b, DISPLAY_NAME_OFFSET, DISPLAY_NAME_BYTES)
        val label = CONTENT_TYPES[contentType] ?: "Unknown Xbox content (0x%08X)".format(contentType)
        return Header(String(b, 0, 4, Charsets.US_ASCII), title, titleId, contentType, label, contentSize,
            fileCount, version, headerSize)
    }

    private fun hasSignature(b: ByteArray): Boolean = b.size >= 4 &&
        (b.copyOfRange(0, 4).contentEquals(CON) || b.copyOfRange(0, 4).contentEquals(LIVE) || b.copyOfRange(0, 4).contentEquals(PIRS))

    private fun utf8(b: ByteArray, offset: Int, length: Int): String {
        val region = b.copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }.toByteArray()
        return runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(region)).toString().trim() }
            .getOrDefault("")
    }

    private fun u32be(b: ByteArray, p: Int): Long = ((b[p].toLong() and 255) shl 24) or ((b[p + 1].toLong() and 255) shl 16) or
        ((b[p + 2].toLong() and 255) shl 8) or (b[p + 3].toLong() and 255)
    private fun u64be(b: ByteArray, p: Int): Long? {
        if (b.size < p + 8 || b[p].toInt() and 0x80 != 0) return null
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[p + i].toLong() and 255)
        return value
    }

    companion object {
        private const val MIME = "application/x-xbox-stfs-package"
        private const val MIN_HEADER = 0x491
        private const val HEADER_BYTES = 0x600
        private const val DISPLAY_NAME_OFFSET = 0x411
        private const val DISPLAY_NAME_BYTES = 0x80
        private const val MAX_FILES = 1_000_000L
        private val CON = "CON ".toByteArray(Charsets.US_ASCII)
        private val LIVE = "LIVE".toByteArray(Charsets.US_ASCII)
        private val PIRS = "PIRS".toByteArray(Charsets.US_ASCII)
        private val CONTENT_TYPES = mapOf(
            0x00000001L to "Saved game", 0x00000002L to "Marketplace content", 0x00001000L to "Xbox 360 title",
            0x00004000L to "Installed game", 0x00005000L to "Original Xbox title", 0x00007000L to "Game on Demand",
            0x00009000L to "Avatar item", 0x00010000L to "Profile", 0x00020000L to "Gamer picture",
            0x00030000L to "Theme", 0x00040000L to "Cache file", 0x00080000L to "Game demo",
            0x000A0000L to "Game title", 0x000D0000L to "Arcade title", 0x000E0000L to "XNA game"
        )
    }
}
