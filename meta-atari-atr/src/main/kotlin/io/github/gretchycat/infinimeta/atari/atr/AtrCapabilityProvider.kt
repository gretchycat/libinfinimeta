package io.github.gretchycat.infinimeta.atari.atr

import io.github.gretchycat.infinistream.*

class AtrCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(AtrHandler())
}

private class AtrHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.atari.atr", "meta-consoles", "Atari 8-bit ATR disk image",
        listOf(FormatSupport(setOf(MIME), setOf("atr"), magic = listOf(MagicSignature("9602", 0, "Atari ATR header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("sectorSize", "sectorCount", "density", "dosVersion", "declaredLength"),
        limitations = listOf("Validates the ATR wrapper and reports a DOS 2.x VTOC when present; does not identify every Atari DOS variant"),
        attributes = mapOf("probePrefixBytes" to "46224")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val bytes = context.prefix()
        if (!starts(bytes, MAGIC)) return super.probe(context)
        val parsed = parse(bytes, context.resource.metadata.length)
            ?: return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "ATR signature is present but the sector geometry does not match the image length")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated ATR signature, sector geometry, and declared image length",
            mapOf("mimeType" to MIME, "format" to "atari-atr", "sectorSize" to parsed.sectorSize.toString(),
                "sectorCount" to parsed.sectorCount.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val length = context.length ?: return null
        val header = context.readAt(0, 16)
        val parsed = parse(header, length) ?: return null
        val vtocOffset = 16L + if (parsed.sectorSize == 128) 359L * 128L else 3L * 128L + 356L * 256L
        val vtoc = runCatching { context.readAt(vtocOffset, 16) }.getOrNull() ?: ByteArray(0)
        val dosVersion = vtoc.firstOrNull()?.toInt()?.and(255)?.takeIf { it == 2 }
        return MetadataRecord("atari-atr", MIME, "Atari 8-bit disk image", linkedMapOf(
            "System" to "Atari 400/800/XL/XE family",
            "Image format" to "ATR sector image",
            "Sector size" to "${parsed.sectorSize} bytes${if (parsed.sectorSize == 256) " (first three sectors are 128 bytes)" else ""}",
            "Sector count" to parsed.sectorCount.toString(),
            "Density" to parsed.density,
            "DOS 2.x VTOC" to (dosVersion?.toString() ?: "Not detected"),
            "Actual image size" to "$length bytes",
            "Header-declared size" to if (parsed.declaredBytes > 0) "${parsed.declaredBytes} bytes" else "Not stated"
        ), if (parsed.declaredBytes > 0 && parsed.declaredBytes != length - 16) listOf(
            "The ATR paragraph count differs from actual image length; geometry is based on actual bytes.") else emptyList())
    }

    private data class Parsed(val sectorSize: Int, val sectorCount: Long, val density: String, val declaredBytes: Long)

    private fun parse(b: ByteArray, length: Long?): Parsed? {
        if (!starts(b, MAGIC) || b.size < 6 || length == null || length < 16 + 368L * 128L) return null
        val sectorSize = u16le(b, 4)
        if (sectorSize !in setOf(128, 256)) return null
        val payloadBytes = length - 16
        val sectorCount = when (sectorSize) {
            128 -> if (payloadBytes % 128L == 0L) payloadBytes / 128L else return null
            else -> if (payloadBytes >= 384L && (payloadBytes - 384L) % 256L == 0L)
                3L + (payloadBytes - 384L) / 256L else return null
        }
        if (sectorCount < 368 || sectorCount > 65535) return null
        val paragraphs = (u16le(b, 2).toLong() or (u16le(b, 6).toLong() shl 16)) * 16L
        val density = when {
            sectorSize == 128 && sectorCount in 710..720 -> "Single density"
            sectorSize == 128 && sectorCount > 720 -> "Enhanced density / extended"
            sectorSize == 256 -> "Double density"
            else -> "Non-standard geometry"
        }
        return Parsed(sectorSize, sectorCount, density, paragraphs)
    }

    private fun starts(bytes: ByteArray, prefix: ByteArray): Boolean = prefix.indices.all { bytes.getOrNull(it) == prefix[it] }
    private fun u16le(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    companion object {
        private const val MIME = "application/x-atari-atr"
        private val MAGIC = byteArrayOf(0x96.toByte(), 0x02)
    }
}
