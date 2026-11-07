package io.github.gretchycat.infinimeta.sega.sms

import io.github.gretchycat.infinimeta.sega.SegaEightBitHeader
import io.github.gretchycat.infinistream.*

class SmsCapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(SmsHandler())
}

private class SmsHandler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.sms", "meta-sega", "Sega Master System / Mark III ROM",
        listOf(FormatSupport(setOf("application/x-sms-rom"), setOf("sms"), magic = listOf(
            MagicSignature("544D522053454741", 0x1ff0, "TMR SEGA header"),
            MagicSignature("544D522053454741", 0x3ff0, "TMR SEGA header"),
            MagicSignature("544D522053454741", 0x7ff0, "TMR SEGA header")))),
        setOf(Operations.IDENTIFY, Operations.METADATA),
        metadata = setOf("system", "productCode", "revision", "region", "storedChecksum", "romSizeCode"),
        limitations = listOf("The header's checksum and size code are reported but not verified against ROM contents"))

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val header = SegaEightBitHeader.find(context.prefix())?.takeIf { it.system == "master-system" }
        return if (header != null) HandlerProbe(MatchConfidence.VERIFIED, descriptor.operations,
            "Validated TMR SEGA header and Master System region code", mapOf("mimeType" to header.mimeType, "format" to "sega-master-system-rom"))
        else super.probe(context)
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? =
        SegaEightBitHeader.find(context)?.takeIf { it.system == "master-system" }?.metadata(context.length)
}
