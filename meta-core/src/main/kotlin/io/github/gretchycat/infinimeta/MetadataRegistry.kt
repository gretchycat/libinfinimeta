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
    ): List<FormatMatch> = capabilities.probe(resource, budget = budget, cancellation = cancellation)
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
        val discovered = capabilities.probe(resource, budget = probeBudget, cancellation = cancellation)
        val formats = discovered.map { toMatch(it, resource) }
        val metadata = mutableListOf<MetadataFinding>()
        val failures = mutableListOf<MetadataFailure>()
        discovered.filter { Operations.METADATA in it.operations }.forEach { found ->
            val extractor = found.handler.service(MetadataCapabilityHandler::class.java) ?: return@forEach
            try {
                val record = MetadataReadContext(resource, metadataBytes, maxNanos = metadataNanos,
                    cancellation = cancellation).use(extractor::extractMetadata)
                if (record != null) metadata += MetadataFinding(toMatch(found, resource), record)
            } catch (error: Exception) {
                failures += MetadataFailure(found.descriptor.id, error.message ?: error.javaClass.simpleName)
            } catch (error: LinkageError) {
                failures += MetadataFailure(found.descriptor.id, error.message ?: error.javaClass.simpleName)
            }
        }
        return MetadataInspection(formats, metadata, failures)
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
