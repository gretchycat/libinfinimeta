package io.github.gretchycat.infinimeta.sega

import io.github.gretchycat.infinimeta.MetadataInspection
import io.github.gretchycat.infinimeta.MetadataRegistry
import io.github.gretchycat.infinistream.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises ServiceLoader discovery, signature probes and format parsers with byte-level fixtures. */
class SegaMetadataIntegrationTest {
    private val registry = MetadataRegistry.discover()

    @Test fun providersAreDiscoveredFromTheAggregateLibrary() {
        val ids = registry.identify(resource("unknown.dat", byteArrayOf())).map { it.handlerId }.toSet()
        assertTrue(ids.none { it.startsWith("meta.sega.") })
        val extensions = RuntimeCapabilities.installed().supportedExtensions(Operations.METADATA)
        assertTrue(extensions.containsAll(setOf("gen", "smd", "sms", "gg", "sg1000", "iso", "gdi", "vms")))
    }

    @Test fun retainsOverlappingCandidatesForAnUnverifiedDiscImageExtension() {
        val handlers = registry.identify(resource("unknown.iso", ByteArray(4096))).map { it.handlerId }.toSet()
        assertTrue(handlers.containsAll(setOf("meta.sega.cd", "meta.sega.saturn", "meta.sega.dreamcast")))
    }

    @Test fun parsesMegaDriveHeaderAndKeepsVerifiedIdentity() {
        val bytes = ByteArray(0x20000)
        putAscii(bytes, 0x100, "SEGA MEGA DRIVE")
        putAscii(bytes, 0x120, "MEGA DRIVE TEST")
        putAscii(bytes, 0x150, "MEGA DRIVE TEST INTERNATIONAL")
        putAscii(bytes, 0x180, "GM TST-00000-00")
        putAscii(bytes, 0x1F0, "JUE")
        val result = inspect("sample.bin", bytes)
        val parsed = result.metadata.single { it.record.formatId == "sega-megadrive-rom" }
        assertEquals("MEGA DRIVE TEST", parsed.record.title)
        assertEquals(MatchConfidence.VERIFIED, parsed.match.confidence)
        assertEquals("application/x-genesis-rom", parsed.record.mimeType)
    }

    @Test fun deinterleavesSmdBeforeReadingTheMegaDriveHeader() {
        val logical = ByteArray(16 * 1024)
        putAscii(logical, 0x100, "SEGA MEGA DRIVE")
        putAscii(logical, 0x120, "INTERLEAVED TEST")
        putAscii(logical, 0x150, "INTERLEAVED TEST INTERNATIONAL")
        val bytes = ByteArray(512 + 16 * 1024)
        putAscii(bytes, 0x280, "EAGN")
        for (i in 0 until 8192) {
            bytes[512 + i] = logical[i * 2 + 1]
            bytes[512 + 8192 + i] = logical[i * 2]
        }
        putAscii(bytes, 0x280, "EAGN")
        val result = inspect("sample.smd", bytes)
        val parsed = result.metadata.single { it.record.formatId == "sega-smd-rom" }
        assertEquals("INTERLEAVED TEST", parsed.record.title)
        assertEquals(MatchConfidence.VERIFIED, parsed.match.confidence)
    }

    @Test fun distinguishesMasterSystemAndGameGearByHeaderRegion() {
        val sms = eightBitRom(0x8000, 0x7FF0, 3)
        val gg = eightBitRom(0x8000, 0x7FF0, 6)
        assertEquals("sega-master-system-rom", inspect("game.sms", sms).metadata.single { it.record.formatId == "sega-master-system-rom" }.record.formatId)
        assertEquals("sega-game-gear-rom", inspect("game.gg", gg).metadata.single { it.record.formatId == "sega-game-gear-rom" }.record.formatId)
    }

    @Test fun recognizesSegaCdSaturnAndDreamcastHeaders() {
        val cd = ByteArray(4096).apply { putAscii(this, 0, "SEGADISCSYSTEM") }
        assertEquals("sega-cd-disc-image", inspect("disc.iso", cd).metadata.single { it.record.formatId == "sega-cd-disc-image" }.record.formatId)

        val saturn = ByteArray(2048).apply {
            putAscii(this, 0, "SEGA SEGASATURN ")
            putAscii(this, 0x40, "SATURN TEST GAME")
            putAscii(this, 0x50, "T-00000G")
        }
        assertEquals("SATURN TEST GAME", inspect("saturn.bin", saturn).metadata.single { it.record.formatId == "sega-saturn-disc" }.record.title)

        val dreamcast = ByteArray(2048).apply {
            putAscii(this, 0, "SEGA SEGAKATANA ")
            putAscii(this, 0x4A, "MK-00000")
            putAscii(this, 0xA0, "DREAMCAST TEST")
        }
        assertEquals("DREAMCAST TEST", inspect("dreamcast.bin", dreamcast).metadata.single { it.record.formatId == "sega-dreamcast-ipbin" }.record.title)
    }

    @Test fun reportsSg1000AsExtensionCandidateWithoutClaimingContentVerification() {
        val candidate = inspect("homebrew.sg", ByteArray(2048))
        val parsed = candidate.metadata.single { it.record.formatId == "sega-sg1000-cartridge" }
        assertEquals(MatchConfidence.CANDIDATE, parsed.match.confidence)
        assertTrue(parsed.record.warnings.any { it.contains("could not be verified") })
    }

    @Test fun readsDreamcastVmsDescriptiveHeader() {
        val vms = ByteArray(4096)
        putAscii(vms, 0x00, "SAVE TITLE")
        putAscii(vms, 0x10, "Dreamcast save title")
        putAscii(vms, 0x30, "TEST TOOL")
        putLe16(vms, 0x40, 1)
        putLe16(vms, 0x42, 10)
        putLe16(vms, 0x44, 0)
        putLe32(vms, 0x48, 1024)
        val result = inspect("save.vms", vms)
        val parsed = result.metadata.single { it.record.formatId == "dreamcast-vms" }
        assertEquals("SAVE TITLE", parsed.record.title)
        assertEquals("1024", parsed.record.fields["Payload bytes"])
    }

    @Test fun boundedReaderRejectsRequestsBeyondTheConfiguredLimit() {
        val source = resource("small.bin", ByteArray(4)).source!!
        MetadataReadContext(CapabilityResource(source.metadata, source = source), maxBytes = 2).use { reader ->
            assertEquals(2, reader.readAt(0, 2).size)
            assertThrows(java.io.IOException::class.java) { reader.readAt(2, 4) }
        }
    }

    @Test fun forwardOnlySourcesCannotSeekBackAndSkippedBytesUseTheBudget() {
        val metadata = StreamMetadata(name = "sequential.rom", length = 16,
            capabilities = setOf(Capability.READ, Capability.KNOWN_LENGTH))
        val source = object : SourceResource {
            override val metadata = metadata
            override fun open(): ByteSource = SequentialSource(metadata, ByteArray(16) { it.toByte() })
        }
        MetadataReadContext(CapabilityResource(metadata, source = source), maxBytes = 4).use { reader ->
            assertArrayEquals(byteArrayOf(0, 1, 2), reader.readAt(0, 3))
            assertThrows(java.io.IOException::class.java) { reader.readAt(2, 1) }
            assertThrows(java.io.IOException::class.java) { reader.readAt(3, 2) }
        }
    }

    private fun inspect(name: String, bytes: ByteArray): MetadataInspection = registry.inspect(resource(name, bytes),
        probeBudget = ProbeBudget(maxBytes = 64 * 1024, maxNanos = 1_000_000_000),
        metadataBytes = 64 * 1024, metadataNanos = 1_000_000_000)

    private fun resource(name: String, bytes: ByteArray): CapabilityResource {
        val metadata = StreamMetadata(content = ContentType(mimeType = "application/octet-stream"), name = name,
            length = bytes.size.toLong(), finiteness = Finiteness.FINITE,
            capabilities = setOf(Capability.READ, Capability.KNOWN_LENGTH))
        val source = object : SourceResource {
            override val metadata = metadata
            override fun open(): ByteSource = MemorySource(metadata, bytes)
        }
        return CapabilityResource(metadata, source = source)
    }

    private class MemorySource(override val metadata: StreamMetadata, private val bytes: ByteArray) : ByteSource, PositionalReadable {
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
            val count = minOf(length, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() {}
    }

    private class SequentialSource(override val metadata: StreamMetadata, private val bytes: ByteArray) : ByteSource {
        private var position = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            requireRange(position.toLong(), buffer, offset, length)
            if (length == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun close() {}
    }

    private fun eightBitRom(size: Int, offset: Int, region: Int): ByteArray = ByteArray(size).apply {
        putAscii(this, offset, "TMR SEGA")
        this[offset + 10] = 0x34
        this[offset + 11] = 0x12
        this[offset + 12] = 0x56
        this[offset + 13] = 0x34
        this[offset + 14] = 0x21
        this[offset + 15] = (region shl 4).toByte()
    }

    private fun putAscii(bytes: ByteArray, offset: Int, text: String) {
        text.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
    }
    private fun putLe16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun putLe32(bytes: ByteArray, offset: Int, value: Int) {
        putLe16(bytes, offset, value); putLe16(bytes, offset + 2, value ushr 16)
    }
}
