package io.github.gretchycat.infinimeta.sony.psp

import io.github.gretchycat.infinistream.*

class PbpCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(PbpHandler())
}

private class PbpHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.sony.psp.pbp", "meta-consoles", "Sony PSP PBP package",
        listOf(FormatSupport(setOf("application/x-sony-pbp"), setOf("pbp"),
            magic = listOf(MagicSignature("00504250", 0, "PBP package")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("version", "paramSfo", "icon", "dataPsp", "dataPsar"),
        limitations = listOf("Reports PBP section ranges only; PARAM.SFO and embedded artwork are not decoded")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(MAGIC)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "PBP signature found but one or more section offsets are invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the 40-byte PBP header and its section offsets",
            mapOf("mimeType" to MIME, "format" to "sony-pbp", "version" to h.version.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, 40), context.length) ?: return null
        val names = listOf("PARAM.SFO", "ICON0", "ICON1", "PIC0", "PIC1", "SND0", "DATA.PSP", "DATA.PSAR")
        val fields = linkedMapOf("System" to "Sony PSP / PBP container", "Version" to "0x%08X".format(h.version))
        names.forEachIndexed { i, name ->
            val start = h.offsets[i]
            val end = h.offsets.getOrNull(i + 1) ?: (context.length ?: start)
            fields["$name range"] = if (start == end) "Empty" else "$start–$end (${end - start} bytes)"
        }
        return MetadataRecord("sony-pbp", MIME, fields = fields)
    }

    private data class Header(val version: Long, val offsets: List<Long>)
    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!b.startsWith(MAGIC) || b.size < 40 || length != null && length < 40) return null
        val version = u32(b, 4)
        val offsets = (0 until 8).map { u32(b, 8 + it * 4) }
        val bound = length ?: Long.MAX_VALUE
        if (offsets.any { it < 40 || it > bound } || offsets.zipWithNext().any { (a, z) -> z < a }) return null
        return Header(version, offsets)
    }
    private fun u32(b: ByteArray, p: Int): Long = (b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
        ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }
    companion object { private const val MIME = "application/x-sony-pbp"; private val MAGIC = byteArrayOf(0, 0x50, 0x42, 0x50) }
}
