package io.github.gretchycat.infinimeta.sega.sg1000

import io.github.gretchycat.infinistream.*

class Sg1000CapabilityProvider : CapabilityProvider {
    override val library = "meta-sega"
    override fun handlers(): List<CapabilityHandler> = listOf(Sg1000Handler())
}

/** SG-1000 cartridge dumps have no universal in-band identification header. */
private class Sg1000Handler : MetadataCapabilityHandler {
    override val descriptor = HandlerDescriptor("meta.sega.sg1000", "meta-sega", "Sega SG-1000 cartridge image",
        listOf(FormatSupport(setOf("application/x-sega-sg1000-rom"), setOf("sg1000", "sg", "sc"))),
        setOf(Operations.IDENTIFY, Operations.METADATA), metadata = setOf("system", "imageSize"),
        limitations = listOf("No universal cartridge header exists; extension-based identification is only a candidate, not content verification"))

    override fun probe(context: CapabilityProbeContext): HandlerProbe {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        return if (listOf(".sg1000", ".sg", ".sc").any(name::endsWith)) HandlerProbe(
            MatchConfidence.CANDIDATE, descriptor.operations,
            "Filename extension suggests SG-1000; this format has no universal in-band signature",
            mapOf("mimeType" to "application/x-sega-sg1000-rom", "format" to "sega-sg1000-cartridge"))
        else HandlerProbe(MatchConfidence.NONE, emptySet())
    }

    override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
        val name = context.resource.metadata.name.orEmpty().lowercase()
        if (!listOf(".sg1000", ".sg", ".sc").any(name::endsWith)) return null
        val size = context.length ?: return MetadataRecord("sega-sg1000-cartridge", "application/x-sega-sg1000-rom",
            fields = mapOf("System" to "Sega SG-1000", "Image size" to "Unknown"),
            warnings = listOf("Extension-based candidate only; the content could not be verified."))
        return MetadataRecord("sega-sg1000-cartridge", "application/x-sega-sg1000-rom",
            fields = mapOf("System" to "Sega SG-1000", "Image size" to "$size bytes"),
            warnings = listOf("Extension-based candidate only; the content could not be verified."))
    }
}
