package io.github.gretchycat.infinimeta.sony.pkg

import io.github.gretchycat.infinistream.*

class SonyPkgCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(SonyPkgHandler())
}

private class SonyPkgHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.sony.pkg", "meta-consoles", "Sony PlayStation package",
        listOf(FormatSupport(setOf("application/x-sony-package"), setOf("pkg"), magic = listOf(
            MagicSignature("7F504B47", 0, "Sony PS3 / PSP / Vita package"),
            MagicSignature("7F434E54", 0, "Sony PS4 package"),
            MagicSignature("7F464948", 0, "Sony PS5 finalized package")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("packageFamily", "packageType", "revision", "contentId", "itemCount", "dataRange"),
        limitations = listOf("Extracts public package header facts only; encrypted package payloads are not unpacked", "PS5 support validates the FIH wrapper ranges; it does not parse the embedded CNT or encrypted PFS")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!isSignature(b)) return super.probe(context)
        val parsed = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "Sony package signature found but the declared file ranges are inconsistent")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the Sony package signature and bounded header ranges",
            mapOf("mimeType" to MIME, "format" to parsed.format, "packageFamily" to parsed.family))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val parsed = parse(context.readAt(0, 0x100), context.length) ?: return null
        return MetadataRecord(parsed.format, MIME, parsed.contentId.takeIf(String::isNotBlank), linkedMapOf(
            "System family" to parsed.family,
            "Header signature" to parsed.signature,
            "Header revision" to parsed.revision,
            "Package type" to (parsed.packageType ?: "Not present in this header variant"),
            "Content ID" to parsed.contentId.ifBlank { "Not present" },
            "Item count" to (parsed.itemCount?.toString() ?: "Not present in this header variant"),
            "Package file size" to "${context.length ?: 0} bytes",
            "Data range" to (parsed.dataRange ?: "Not available")
        ))
    }

    private data class Header(val signature: String, val format: String, val family: String, val revision: String,
        val packageType: String?, val contentId: String, val itemCount: Long?, val dataRange: String?)

    private fun parse(b: ByteArray, length: Long?): Header? {
        if (isPs5Signature(b)) {
            if (b.size < 0xA8 || (b[5].toInt() and 255) !in setOf(0x00, 0x80)) return null
            val actualLength = length ?: return null
            val pfsOffset = u64leSafe(b, 0x10) ?: return null
            val pfsSize = u64leSafe(b, 0x18) ?: return null
            val cntOffset = u64leSafe(b, 0x58) ?: return null
            val cntSize = u64leSafe(b, 0xA0) ?: return null
            if (pfsOffset != 0x10000L || pfsSize <= 0 || pfsOffset > actualLength || pfsSize > actualLength - pfsOffset ||
                cntOffset < 0 || cntSize <= 0 || cntOffset > actualLength || cntSize > actualLength - cntOffset) return null
            return Header("7F464948", "sony-ps5-fih", "PlayStation 5", "FIH ${if ((b[5].toInt() and 255) == 0x80) "retail" else "debug"}",
                null, "", null, "PFS $pfsOffset–${pfsOffset + pfsSize}; embedded CNT $cntOffset–${cntOffset + cntSize}")
        }
        if (!isSignature(b) || b.size < 84 || length != null && length < 84) return null
        val signature = if (b[1].toInt() == 0x50) "7F504B47" else "7F434E54"
        val revision = "%04X".format(u16be(b, 4))
        return if (signature == "7F504B47") {
            if (b.size < 84) return null
            val type = u16be(b, 6)
            val total = u64beSafe(b, 0x18) ?: return null
            val dataOffset = u32be(b, 0x20)
            val dataSize = u32be(b, 0x28)
            val contentId = ascii(b, 0x30, 36)
            val actualLength = length ?: return null
            if (total < 84 || total > actualLength || dataOffset > total || dataSize > total - dataOffset ||
                contentId.length < 8 || contentId.any { !(it.isLetterOrDigit() || it in "-_") }) return null
            val family = when (type) { 1 -> "PlayStation 3"; 2 -> "PlayStation Portable / Vita package family"; else -> "Sony PKG (type $type)" }
            Header(signature, "sony-pkg-legacy", family, revision, type.toString(), contentId,
                u32be(b, 0x14), "$dataOffset–${dataOffset + dataSize} ($dataSize bytes)")
        } else {
            val contentId = ascii(b, 0x40, 36)
            if (contentId.length < 8 || contentId.any { !(it.isLetterOrDigit() || it in "-_") }) return null
            Header(signature, "sony-pkg-modern", "PlayStation 4 package", revision, null,
                contentId, null, null)
        }
    }

    private fun isSignature(b: ByteArray) = b.size >= 4 &&
        (b.copyOfRange(0, 4).contentEquals(LEGACY_MAGIC) || b.copyOfRange(0, 4).contentEquals(PS4_MAGIC) || isPs5Signature(b))
    private fun isPs5Signature(b: ByteArray) = b.size >= 4 && b.copyOfRange(0, 4).contentEquals(PS5_FIH_MAGIC)
    private fun ascii(b: ByteArray, p: Int, n: Int) = String(b, p, n, Charsets.US_ASCII).substringBefore('\u0000').trim()
    private fun u16be(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    private fun u32be(b: ByteArray, p: Int): Long = ((b[p].toLong() and 255) shl 24) or ((b[p + 1].toLong() and 255) shl 16) or
        ((b[p + 2].toLong() and 255) shl 8) or (b[p + 3].toLong() and 255)
    private fun u64beSafe(b: ByteArray, p: Int): Long? {
        if (b.size < p + 8 || b[p].toInt() and 0x80 != 0) return null
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[p + i].toLong() and 255)
        return value
    }
    private fun u64leSafe(b: ByteArray, p: Int): Long? {
        if (b.size < p + 8 || b[p + 7].toInt() and 0x80 != 0) return null
        var value = 0L
        for (i in 7 downTo 0) value = (value shl 8) or (b[p + i].toLong() and 255)
        return value
    }
    companion object {
        private const val MIME = "application/x-sony-package"
        private val LEGACY_MAGIC = byteArrayOf(0x7f, 0x50, 0x4b, 0x47)
        private val PS4_MAGIC = byteArrayOf(0x7f, 0x43, 0x4e, 0x54)
        private val PS5_FIH_MAGIC = byteArrayOf(0x7f, 0x46, 0x49, 0x48)
    }
}
