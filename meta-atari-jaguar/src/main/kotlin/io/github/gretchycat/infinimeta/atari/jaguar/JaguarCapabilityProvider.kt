package io.github.gretchycat.infinimeta.atari.jaguar

import io.github.gretchycat.infinistream.*

class JaguarCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(JaguarHandler())
}

private class JaguarHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.atari.jaguar", "meta-consoles", "Atari Jaguar cartridge and ABS executable",
        listOf(FormatSupport(setOf(MIME_ROM), setOf("j64")), FormatSupport(setOf(MIME_ABS), setOf("abs"),
            magic = listOf(MagicSignature("601B", 0, "Jaguar ABS executable")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("platform", "imageSize", "imageKind", "loadAddress"),
        limitations = listOf("Headerless J64 cartridge images are recognized by conventional 1/2/4 MiB geometry plus extension; Jaguar server files and unheadered .jag/.bin files are not assigned an identity", "No game-title database or checksum catalog is bundled")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        val length = context.resource.metadata.length
        if (name.endsWith(".abs")) {
            val prefix = context.prefix()
            if (prefix.size < ABS_HEADER_SIZE || prefix.u16be(0) != ABS_MAGIC) return super.probe(context)
            if (length != null && length < ABS_HEADER_SIZE) return HandlerProbe(MatchConfidence.CANDIDATE,
                descriptor.operations, "Jaguar ABS magic is present but its header is truncated")
            return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
                "Validated the Atari Jaguar ABS executable header", mapOf("mimeType" to MIME_ABS, "format" to "atari-jaguar-abs"))
        }
        if (!name.endsWith(".j64")) return super.probe(context)
        val size = length ?: return super.probe(context)
        if (size !in CARTRIDGE_SIZES) return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
            "J64 extension is present but the image is not a documented 1, 2, or 4 MiB cartridge dump")
        return HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "J64 extension and canonical Jaguar cartridge dump size agree", mapOf("mimeType" to MIME_ROM,
                "format" to "atari-jaguar-cartridge"))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        val size = context.length ?: return null
        if (name.endsWith(".abs")) {
            val header = context.readAt(0, ABS_HEADER_SIZE)
            if (header.size < ABS_HEADER_SIZE || header.u16be(0) != ABS_MAGIC) return null
            return MetadataRecord("atari-jaguar-abs", MIME_ABS, fields = linkedMapOf(
                "System" to "Atari Jaguar development executable", "Executable format" to "Jaguar ABS",
                "Header signature" to "0x601B", "File size" to "$size bytes"
            ))
        }
        if (!name.endsWith(".j64") || size !in CARTRIDGE_SIZES) return null
        return MetadataRecord("atari-jaguar-cartridge", MIME_ROM, fields = linkedMapOf(
            "System" to "Atari Jaguar", "Image type" to "Headerless cartridge ROM",
            "ROM size" to "$size bytes", "Mapped address" to "0x800000"
        ), warnings = listOf("The cartridge image has no universal title header; the recognized size and .j64 extension establish format class only"))
    }

    private fun ByteArray.u16be(p: Int) = ((this[p].toInt() and 255) shl 8) or (this[p + 1].toInt() and 255)
    companion object {
        private const val MIME_ROM = "application/x-atari-jaguar-rom"
        private const val MIME_ABS = "application/x-atari-jaguar-executable"
        private const val ABS_MAGIC = 0x601b
        private const val ABS_HEADER_SIZE = 36
        private val CARTRIDGE_SIZES = setOf(1L, 2L, 4L).map { it * 1024 * 1024 }
    }
}
