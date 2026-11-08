package io.github.gretchycat.infinimeta.snk.neogeo

import io.github.gretchycat.infinistream.*

class NeoCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(NeoHandler())
}

/** TerraOnion NeoSD / MiSTer merged ROM container; this is not a MAME ZIP set. */
private class NeoHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.snk.neogeo.neo", "meta-consoles", "SNK Neo Geo merged .neo ROM",
        listOf(FormatSupport(setOf("application/x-neo-geo-neo"), setOf("neo"),
            magic = listOf(MagicSignature("4E454F01", 0, "NEO v1 ROM container")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("title", "manufacturer", "year", "genre", "ngh", "programRom", "textRom", "audioRom", "sampleRom", "spriteRom"),
        limitations = listOf("Parses the 4 KiB TerraOnion .neo header; does not parse MAME/FBNeo ZIP sets or validate ROM chip contents")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(MAGIC)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "NEO signature found but region lengths do not fit in the file")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations, "Validated the .neo header and all declared ROM regions",
            mapOf("mimeType" to MIME, "format" to "neo-geo-neo", "title" to h.title))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, HEADER_SIZE), context.length) ?: return null
        return MetadataRecord("neo-geo-neo", MIME, h.title.takeIf(String::isNotBlank), linkedMapOf(
            "System" to "SNK Neo Geo",
            "Manufacturer" to h.manufacturer.ifBlank { "Unspecified" },
            "Year" to if (h.year in 1970..2100) h.year.toString() else "Unspecified",
            "Genre code" to h.genre.toString(),
            "NGH number" to h.ngh.toString(),
            "Program ROM" to size(h.p), "Text ROM" to size(h.s), "Audio CPU ROM" to size(h.m),
            "Sample ROM" to size(h.v), "Sprite ROM" to size(h.c),
            "Declared region payload" to "${h.payload} bytes",
            "File size" to "${context.length ?: 0} bytes"
        ))
    }

    private data class Header(val title: String, val manufacturer: String, val year: Long, val genre: Long,
        val ngh: Long, val p: Long, val s: Long, val m: Long, val v: Long, val c: Long, val payload: Long)
    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!b.startsWith(MAGIC) || b.size < HEADER_SIZE || length == null || length < HEADER_SIZE) return null
        val p = u32le(b, 4); val s = u32le(b, 8); val m = u32le(b, 12)
        val v = Math.addExact(u32le(b, 16), u32le(b, 20)); val c = u32le(b, 24)
        val payload = try { Math.addExact(Math.addExact(Math.addExact(p, s), Math.addExact(m, v)), c) }
            catch (_: ArithmeticException) { return null }
        if (p == 0L || payload > length - HEADER_SIZE || payload > MAX_PAYLOAD) return null
        return Header(text(b, 0x2c, 33), text(b, 0x4d, 17), u32le(b, 0x1c), u32le(b, 0x20),
            u32le(b, 0x28), p, s, m, v, c, payload)
    }
    private fun size(n: Long) = if (n == 0L) "Absent" else "$n bytes"
    private fun text(b: ByteArray, p: Int, n: Int) = String(b, p, n, Charsets.ISO_8859_1)
        .substringBefore('\u0000').filterNot(Char::isISOControl).trim()
    private fun u32le(b: ByteArray, p: Int): Long = (b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
        ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }
    companion object {
        private const val MIME = "application/x-neo-geo-neo"
        private const val HEADER_SIZE = 0x1000
        private const val MAX_PAYLOAD = 2L * 1024 * 1024 * 1024
        private val MAGIC = byteArrayOf(0x4e, 0x45, 0x4f, 0x01)
    }
}
