package io.github.gretchycat.infinimeta

import io.github.gretchycat.infinistream.*

/** A format match keeps overlapping handlers and records the evidence used by each one. */
data class FormatMatch(
    val handlerId: String,
    val library: String,
    val handlerName: String,
    val mimeTypes: Set<String>,
    val extensions: Set<String>,
    val confidence: MatchConfidence,
    val evidence: String,
    val reason: String? = null
)

data class MetadataFinding(val match: FormatMatch, val record: MetadataRecord)
data class MetadataFailure(val handlerId: String, val message: String)
data class MetadataInspection(
    val formats: List<FormatMatch>,
    val metadata: List<MetadataFinding>,
    val failures: List<MetadataFailure>
)

/** Shared identification and metadata entry point for all linked format libraries. */
class MetadataRegistry(private val capabilities: CapabilityRegistry = RuntimeCapabilities.installed()) {
    /** Returns every matching handler; overlapping format support is intentional. */
    fun identify(
        resource: CapabilityResource,
        budget: ProbeBudget = ProbeBudget(),
        cancellation: CancellationToken = CancellationToken.NONE
    ): List<FormatMatch> = capabilities.probeIncludingUnavailable(resource, budget = budget, cancellation = cancellation)
        .map { toMatch(it, resource) }

    /** Identifies the resource and asks each matching metadata handler to extract bounded facts. */
    fun inspect(
        resource: CapabilityResource,
        probeBudget: ProbeBudget = ProbeBudget(),
        metadataBytes: Long = 1024 * 1024,
        metadataNanos: Long = 1_000_000_000,
        cancellation: CancellationToken = CancellationToken.NONE
    ): MetadataInspection {
        require(metadataBytes in 0..(64L * 1024 * 1024) && metadataNanos in 0..60_000_000_000L)
        // Identity and metadata remain useful when an optional viewer/decoder backend is
        // unavailable. Availability constrains execution, not the format parser's report.
        val discovered = capabilities.probeIncludingUnavailable(resource, budget = probeBudget, cancellation = cancellation)
        val formats = discovered.map { toMatch(it, resource) }
        val metadata = mutableListOf<MetadataFinding>()
        val failures = mutableListOf<MetadataFailure>()
        discovered.forEach { found ->
            val match = toMatch(found, resource)
            val base = identityMetadata(found, resource)
            val extractor = found.handler.service(MetadataCapabilityHandler::class.java)
            val extracted = if (extractor != null &&
                (Operations.METADATA in found.descriptor.operations || Operations.METADATA in found.operations)) {
                try {
                    MetadataReadContext(resource, metadataBytes, maxNanos = metadataNanos,
                        cancellation = cancellation).use(extractor::extractMetadata)
                } catch (error: Exception) {
                    failures += MetadataFailure(found.descriptor.id, error.message ?: error.javaClass.simpleName)
                    null
                } catch (error: LinkageError) {
                    failures += MetadataFailure(found.descriptor.id, error.message ?: error.javaClass.simpleName)
                    null
                }
            } else null

            // Every identified/candidate format has a useful metadata result even when its
            // specialized parser has not yet been implemented. The generic fields are source
            // facts and detection evidence; they do not invent dimensions, tags, or codec data.
            if (base != null || extracted != null) {
                val combined = when {
                    base == null -> extracted!!
                    extracted == null -> base
                    else -> base.copy(
                        formatId = extracted.formatId,
                        mimeType = extracted.mimeType,
                        title = extracted.title ?: base.title,
                        fields = base.fields + extracted.fields,
                        warnings = base.warnings + extracted.warnings
                    )
                }
                metadata += MetadataFinding(match, combined)
            }
        }
        return MetadataInspection(formats, metadata, failures)
    }

    private fun identityMetadata(found: DiscoveredCapability, resource: CapabilityResource): MetadataRecord? {
        val probe = found.probe ?: return null
        if (probe.confidence == MatchConfidence.NONE) return null
        val descriptor = found.descriptor
        val profile = descriptor.formats.maxByOrNull { it.score(resource) }
        val contentMime = resource.metadata.content.mimeType?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains('*') }
        val mime = probe.metadata["mimeType"]?.takeIf { it.isNotBlank() }
            ?: contentMime
            ?: profile?.mimeTypes?.singleOrNull { !it.contains('*') }
            ?: "application/octet-stream"
        val fields = buildMap {
            resource.metadata.name?.let { put("sourceName", it) }
            resource.metadata.length?.let { put("sourceLengthBytes", it.toString()) }
            put("identityConfidence", probe.confidence.name.lowercase())
            probe.reason?.takeIf { it.isNotBlank() }?.let { put("identityEvidence", it) }
            probe.metadata.forEach { (key, value) -> if (value.isNotBlank()) put(key, value) }
        }
        val warnings = if (probe.confidence == MatchConfidence.CANDIDATE)
            listOf("Format association is a candidate; content validation is still required")
        else emptyList()
        return MetadataRecord(descriptor.id, mime, descriptor.title, fields, warnings)
    }

    private fun toMatch(found: DiscoveredCapability, source: CapabilityResource): FormatMatch {
        val descriptor = found.descriptor
        val profile = descriptor.formats.maxByOrNull { it.score(source) }
        val declaredMime = found.probe?.metadata?.get("mimeType")
        val confidence = found.probe?.confidence ?: MatchConfidence.CANDIDATE
        val evidence = when {
            confidence == MatchConfidence.VERIFIED -> "parser validation"
            confidence == MatchConfidence.SIGNATURE -> "magic signature"
            profile?.extensions?.isNotEmpty() == true -> "filename extension"
            declaredMime != null -> "MIME association"
            profile?.uriSchemes?.isNotEmpty() == true -> "URI scheme"
            else -> "handler match"
        }
        val mimeTypes = declaredMime?.let(::setOf) ?: profile?.mimeTypes.orEmpty()
        return FormatMatch(descriptor.id, descriptor.library, descriptor.title, mimeTypes,
            profile?.extensions.orEmpty(), confidence, evidence, found.probe?.reason)
    }

    companion object {
        /** Discover all providers on this class path without initializing Android or InfiniFolder. */
        @JvmStatic fun discover(loader: ClassLoader = CapabilityProvider::class.java.classLoader): MetadataRegistry =
            MetadataRegistry(RuntimeCapabilities.discover(loader))
    }
}
