package io.github.gretchycat.infinimeta.sony.psx

import io.github.gretchycat.infinistream.*

class PlayStationExeCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(PsxExeHandler())
}

private class PsxExeHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.sony.psx.exe", "meta-consoles", "Sony PlayStation PS-X EXE",
        listOf(FormatSupport(setOf("application/x-psx-executable"), setOf("exe"),
            magic = listOf(MagicSignature("50532D5820455845", 0, "PS-X EXE")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("initialPc", "globalPointer", "loadAddress", "payloadSize", "region"),
        limitations = listOf("Identifies PS1 executable files only; does not identify the game release or parse disc track layouts")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val b = context.prefix()
        if (!b.startsWith(MAGIC)) return super.probe(context)
        val h = parse(b, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "PS-X EXE signature found but the executable header or declared payload size is invalid")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated the PS-X EXE header and loadable payload range",
            mapOf("mimeType" to MIME, "format" to "psx-executable", "payloadBytes" to h.payload.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val h = parse(context.readAt(0, 0x800), context.length) ?: return null
        return MetadataRecord("psx-executable", MIME, fields = linkedMapOf(
            "System" to "Sony PlayStation",
            "Executable format" to "PS-X EXE",
            "Initial PC" to hex(h.pc),
            "Global pointer" to hex(h.gp),
            "Load address" to hex(h.address),
            "Payload size" to "${h.payload} bytes",
            "File size" to "${context.length ?: 0} bytes"
        ))
    }

    private data class Header(val pc: Long, val gp: Long, val address: Long, val payload: Long)
    private fun parse(b: ByteArray, length: Long?): Header? {
        if (!b.startsWith(MAGIC) || b.size < 0x800) return null
        val pc = u32(b, 0x10); val gp = u32(b, 0x14); val address = u32(b, 0x18); val size = u32(b, 0x1c)
        if (size == 0L || size > MAX_PAYLOAD || address > 0xffffffffL - size ||
            length != null && (length < 0x800 || size > length - 0x800) || pc == 0L) return null
        return Header(pc, gp, address, size)
    }
    private fun u32(b: ByteArray, p: Int): Long = (b[p].toLong() and 255) or ((b[p + 1].toLong() and 255) shl 8) or
        ((b[p + 2].toLong() and 255) shl 16) or ((b[p + 3].toLong() and 255) shl 24)
    private fun hex(value: Long) = "0x%08X".format(value)
    private fun ByteArray.startsWith(m: ByteArray) = m.indices.all { getOrNull(it) == m[it] }
    companion object {
        private const val MIME = "application/x-psx-executable"
        private const val MAX_PAYLOAD = 512L * 1024 * 1024
        private val MAGIC = "PS-X EXE".toByteArray(Charsets.US_ASCII)
    }
}
