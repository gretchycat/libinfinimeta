package io.github.gretchycat.infinimeta.nec.pce

import io.github.gretchycat.infinistream.*

class PceCapabilityProvider : CapabilityProvider {
    override val library = "meta-consoles"
    override fun handlers(): List<CapabilityHandler> = listOf(PceHandler())
}

/** HuCards have no universal on-disk signature; this handler reports a validated-size candidate. */
private class PceHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor(
        "meta.nec.pce", "meta-consoles", "NEC PC Engine / TurboGrafx-16 cartridge ROM",
        listOf(FormatSupport(setOf(MIME_PCE, MIME_SGX), setOf("pce", "sgx"))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("platform", "imageSize", "payloadSize", "copierHeader", "romBanks"),
        limitations = listOf("HuCard ROMs have no universal magic or embedded title header; size and extension can only establish a plausible cartridge image", "CD-ROM cue/bin/chd images are not identified by this handler")
    )

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        val sgx = name.endsWith(".sgx")
        // HuCards have no reliable in-band signature. Their extension is the
        // required identity clue; plausible bank geometry alone overlaps with
        // unrelated ROMs (including Jaguar's 1/2/4 MiB cartridge images).
        if (!sgx && !name.endsWith(".pce")) return super.probe(context)
        val parsed = parse(context.resource.metadata.length) ?: return super.probe(context)
        val mime = if (sgx) MIME_SGX else MIME_PCE
        return HandlerProbe(MatchConfidence.CANDIDATE, descriptor.operations,
            "Plausible ${if (sgx) "SuperGrafx" else "HuCard"} image geometry; this headerless format has no independent signature",
            mapOf("mimeType" to mime, "format" to if (sgx) "supergrafx-rom" else "pc-engine-rom",
                "payloadBytes" to parsed.payload.toString(), "copierHeaderBytes" to parsed.header.toString()))
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        if (!name.endsWith(".pce") && !name.endsWith(".sgx")) return null
        val parsed = parse(context.length) ?: return null
        val sgx = name.endsWith(".sgx")
        val mime = if (sgx) MIME_SGX else MIME_PCE
        return MetadataRecord(if (sgx) "supergrafx-rom" else "pc-engine-rom", mime, fields = linkedMapOf(
            "System" to if (sgx) "NEC SuperGrafx" else "NEC PC Engine / TurboGrafx-16",
            "Image size" to "${parsed.total} bytes",
            "ROM payload" to "${parsed.payload} bytes",
            "8 KiB ROM banks" to (parsed.payload / BANK_SIZE).toString(),
            "512-byte copier header" to if (parsed.header == 0L) "Absent" else "Present"
        ), warnings = listOf("No cartridge title is stored in the raw HuCard image; the extension and bank-aligned size are clues, not cryptographic or magic-byte confirmation"))
    }

    private data class ImageSize(val total: Long, val payload: Long, val header: Long)
    private fun parse(length: Long?): ImageSize? {
        val total = length ?: return null
        if (total !in MIN_SIZE..MAX_SIZE) return null
        val header = if (total % BANK_SIZE == 512L) 512L else 0L
        val payload = total - header
        if (payload < MIN_SIZE || payload % BANK_SIZE != 0L) return null
        return ImageSize(total, payload, header)
    }

    companion object {
        private const val MIME_PCE = "application/x-pce-rom"
        private const val MIME_SGX = "application/x-supergrafx-rom"
        private const val BANK_SIZE = 8192L
        private const val MIN_SIZE = 32L * 1024
        private const val MAX_SIZE = 16L * 1024 * 1024 + 512
    }
}
