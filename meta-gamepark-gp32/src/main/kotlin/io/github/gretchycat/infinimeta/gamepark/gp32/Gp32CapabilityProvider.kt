package io.github.gretchycat.infinimeta.gamepark.gp32

import io.github.gretchycat.infinistream.*

class Gp32CapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(Gp32Handler())
}

private class Gp32Handler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.gamepark.gp32", "meta-consoles", "GamePark GP32 executable",
        listOf(FormatSupport(setOf(MIME), setOf("gxb", "axf"), magic = listOf(
            MagicSignature("11004544", 0x1c, "GP32 ARM executable signature"),
            MagicSignature("7F454C46", 0, "ELF executable"))),
            FormatSupport(setOf(MIME_FXE), setOf("fxe"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("platform", "executableFormat", "entryBranch", "readOnlyRange", "readWriteRange", "zeroRange", "encrypted"),
        limitations = listOf("Validates ARM GXB headers or ARM ELF images; FXE is encrypted and has no confirmed public signature here, so that extension yields candidate identity and file-size metadata only", "FXE decryption and application icon extraction are not implemented")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        val bytes = context.prefix()
        val gxb = parseGxb(bytes, context.resource.metadata.length)
        if (gxb != null) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated GamePark ARM executable header and memory ranges", mapOf("mimeType" to MIME, "format" to "gp32-gxb"))
        val elf = parseElf(bytes)
        if (elf) return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated ARM ELF executable header", mapOf("mimeType" to MIME, "format" to "gp32-arm-elf"))
        if (name.endsWith(".fxe") && (context.resource.metadata.length ?: 0L) >= 20L) {
            return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
                "FXE extension and minimum package size match an encrypted GP32 executable; opaque payload signature is not independently verified",
                mapOf("mimeType" to MIME_FXE, "format" to "gp32-fxe-candidate"))
        }
        return super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        val bytes = context.readAt(0, 64)
        val gxb = parseGxb(bytes, context.length)
        if (gxb != null) return MetadataRecord("gp32-gxb", MIME, fields = linkedMapOf(
            "System" to "GamePark GP32", "Executable format" to "ARM GXB",
            "Read-only range" to "0x%08X–0x%08X".format(gxb.roStart, gxb.roEnd),
            "Read/write range" to "0x%08X–0x%08X".format(gxb.rwStart, gxb.rwEnd),
            "Zero-initialized range" to "0x%08X–0x%08X".format(gxb.ziStart, gxb.ziEnd),
            "File size" to "${context.length ?: 0} bytes"
        ))
        if (parseElf(bytes)) return MetadataRecord("gp32-arm-elf", MIME, fields = linkedMapOf(
            "System" to "GamePark GP32", "Executable format" to "ARM ELF", "ELF class" to if (bytes[4].toInt() == 1) "32-bit" else "64-bit",
            "File size" to "${context.length ?: 0} bytes"
        ))
        if (name.endsWith(".fxe") && (context.length ?: 0L) >= 20L) return MetadataRecord("gp32-fxe-candidate", MIME_FXE,
            fields = linkedMapOf("System" to "GamePark GP32", "Executable format" to "FXE encrypted executable",
                "File size" to "${context.length ?: 0} bytes", "Payload inspection" to "Encrypted; not decoded"),
            warnings = listOf("FXE classification is based on extension and minimum size; no FXE signature was validated"))
        return null
    }

    private data class Gxb(val roStart: Long, val roEnd: Long, val rwStart: Long, val rwEnd: Long,
        val ziStart: Long, val ziEnd: Long)

    private fun parseGxb(b: ByteArray, length: Long?): Gxb? {
        if (b.size < 32 || b.u32le(0x1c) != GXB_MAGIC) return null
        val branch = b.u32le(0)
        if (branch ushr 24 !in 0xeaL..0xebL) return null
        val displacement = signExtend24(branch and 0x00ffffff) * 4 + 8
        if (displacement !in 0..(length ?: Int.MAX_VALUE.toLong())) return null
        val roStart = b.u32le(4)
        val roEnd = b.u32le(8)
        val rwStart = b.u32le(12)
        val rwEnd = b.u32le(16)
        val ziStart = b.u32le(20)
        val ziEnd = b.u32le(24)
        if (roStart >= roEnd || rwStart > rwEnd || ziStart > ziEnd ||
            listOf(roStart, roEnd, rwStart, rwEnd, ziStart, ziEnd).any { it > 0x10000000L }) return null
        return Gxb(roStart, roEnd, rwStart, rwEnd, ziStart, ziEnd)
    }

    private fun parseElf(b: ByteArray): Boolean {
        if (b.size < 52 || !b.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) return false
        val cls = b[4].toInt() and 255
        val endian = b[5].toInt() and 255
        if (cls !in 1..2 || endian !in 1..2 || b.u16(18, endian == 1) != EM_ARM) return false
        val type = b.u16(16, endian == 1)
        return type == ET_EXEC || type == ET_DYN
    }

    private fun ByteArray.u32le(p: Int): Long = (this[p].toLong() and 255) or ((this[p + 1].toLong() and 255) shl 8) or
        ((this[p + 2].toLong() and 255) shl 16) or ((this[p + 3].toLong() and 255) shl 24)
    private fun ByteArray.u16(p: Int, little: Boolean): Int = if (little) (this[p].toInt() and 255) or ((this[p + 1].toInt() and 255) shl 8)
        else ((this[p].toInt() and 255) shl 8) or (this[p + 1].toInt() and 255)
    private fun signExtend24(n: Long): Long = if (n and 0x00800000L != 0L) n or -0x01000000L else n

    companion object {
        private const val MIME = "application/x-gp32-executable"
        private const val MIME_FXE = "application/x-gp32-encrypted-executable"
        private const val GXB_MAGIC = 0x44450011L
        private const val EM_ARM = 40
        private const val ET_EXEC = 2
        private const val ET_DYN = 3
    }
}
