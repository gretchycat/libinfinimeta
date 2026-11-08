package io.github.gretchycat.infinimeta

import io.github.gretchycat.infinistream.*
import org.junit.Assert.*
import org.junit.Test

class MetadataRegistryTest {
    @Test fun reportsContentEvidenceAndParserMetadataEvenWhenOptionalBackendIsUnavailable() {
        val registry = CapabilityRegistry().apply { register(TestMetadataHandler()) }
        val bytes = "META\ntitle=Real fixture metadata\n".toByteArray()
        val inspection = MetadataRegistry(registry).inspect(resource(bytes))

        assertEquals(1, inspection.formats.size)
        assertEquals(MatchConfidence.SIGNATURE, inspection.formats.single().confidence)
        assertEquals("magic signature", inspection.formats.single().evidence)
        assertEquals(1, inspection.metadata.size)
        val record = inspection.metadata.single().record
        assertEquals("Real fixture metadata", record.title)
        assertEquals(bytes.size.toString(), record.fields["sourceLengthBytes"])
        assertEquals("signature", record.fields["identityConfidence"])
        assertEquals("real-parser", record.fields["parser"])
        assertTrue(inspection.failures.isEmpty())
    }

    @Test fun keepsAnUnverifiedExtensionCandidateButLabelsItAndStillReturnsSourceFacts() {
        val registry = CapabilityRegistry().apply { register(TestMetadataHandler()) }
        val inspection = MetadataRegistry(registry).inspect(resource("not the signature".toByteArray(), "fixture.tst"))
        val match = inspection.formats.single()
        assertEquals(MatchConfidence.CANDIDATE, match.confidence)
        val record = inspection.metadata.single().record
        assertTrue(record.warnings.any { it.contains("candidate") })
        assertEquals("Extension candidate", record.fields["identityEvidence"])
        assertTrue(record.fields["sourceLengthBytes"]!!.toLong() > 0)
    }

    private fun resource(bytes: ByteArray, name: String = "fixture.tst"): CapabilityResource {
        val metadata = StreamMetadata(name = name, length = bytes.size.toLong(),
            capabilities = setOf(Capability.READ, Capability.KNOWN_LENGTH))
        val source = object : SourceResource {
            override val metadata = metadata
            override fun open(): ByteSource = ByteArraySource(metadata, bytes)
        }
        return CapabilityResource(metadata, source = source)
    }

    private class TestMetadataHandler : MetadataCapabilityHandler {
        override val descriptor = HandlerDescriptor("test.fixture", "test", "Fixture metadata parser",
            listOf(FormatSupport(mimeTypes = setOf("application/x-test-fixture"), extensions = setOf("tst"),
                magic = listOf(MagicSignature("4d455441")))),
            setOf(Operations.IDENTIFY, Operations.METADATA), metadata = setOf("title", "parser"))

        override fun availability() = HandlerAvailability(AvailabilityState.UNAVAILABLE, "Optional rendering backend absent")

        override fun probe(context: CapabilityProbeContext): HandlerProbe {
            val prefix = context.prefix()
            if (prefix.size >= 4 && prefix.copyOfRange(0, 4).contentEquals("META".toByteArray()))
                return HandlerProbe(MatchConfidence.SIGNATURE, descriptor.operations, "Fixture signature matched")
            val candidate = descriptor.formats.any { it.score(context.resource) > 0 }
            return HandlerProbe(if (candidate) MatchConfidence.CANDIDATE else MatchConfidence.NONE,
                if (candidate) descriptor.operations else emptySet(), if (candidate) "Extension candidate" else null)
        }

        override fun extractMetadata(context: MetadataReadContext): MetadataRecord? {
            val count = (context.length ?: 0).coerceAtMost(4096).toInt()
            val text = context.readAt(0, count).toString(Charsets.UTF_8)
            if (!text.startsWith("META")) return null
            val title = text.lineSequence().firstOrNull { it.startsWith("title=") }?.substringAfter('=')
                ?.takeIf { it.isNotBlank() }
            return MetadataRecord("test.fixture", "application/x-test-fixture", title,
                mapOf("parser" to "real-parser"))
        }
    }

    private class ByteArraySource(override val metadata: StreamMetadata, private val bytes: ByteArray) : ByteSource, PositionalReadable {
        private var position = 0L
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = readAt(position, buffer, offset, length)
            if (count > 0) position += count
            return count
        }
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            requireRange(position, buffer, offset, length)
            if (length == 0) return 0
            if (position >= bytes.size) return -1
            val start = position.toInt()
            val count = minOf(length, bytes.size - start)
            bytes.copyInto(buffer, offset, start, start + count)
            return count
        }
        override fun close() = Unit
    }
}
