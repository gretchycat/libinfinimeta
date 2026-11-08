# libinfinimeta

`libinfinimeta` identifies byte streams and extracts bounded, format-specific metadata. It has no Android or InfiniFolder dependency. Its public registry and parser contract live in `meta-core`; parser implementations are separate Gradle modules and register through `ServiceLoader<CapabilityProvider>`.

## Querying formats

```kotlin
val metadata = MetadataRegistry.discover()
val inspection = metadata.inspect(capabilityResource)
inspection.formats       // every matching handler, including overlaps and candidates
inspection.metadata     // records emitted by handlers with the metadata operation
inspection.failures     // parser/read errors, kept separate from “no match”
```

The resource combines immutable stream metadata and an optional reopenable `SourceResource`. A parser receives `MetadataReadContext`, which closes its source and bounds bytes read (64 MiB maximum, 1 MiB default), read calls, elapsed time, and cancellation. Positional sources may read any bounded range; forward-only sources can only advance, and skipped bytes count against the budget. Parsers return `MetadataRecord` facts and warnings; they do not modify the input.

Identification confidence distinguishes `CANDIDATE` (extension or MIME association), `SIGNATURE` (magic match), and `VERIFIED` (the parser checked structure). Registries retain overlapping matches so callers can present ambiguity or apply their own policy. A file extension is never treated as content validation.

## Adding a parser

1. Create a module for the format (for example `meta-sega-saturn`).
2. Implement a `MetadataCapabilityHandler`, its `HandlerDescriptor`, and bounded `extractMetadata` logic.
3. Add a `CapabilityProvider` service entry at `META-INF/services/io.github.gretchycat.infinistream.CapabilityProvider`.
4. Add the module to `meta-sega` (or another family aggregate). The registry and catalog discover it without changes to aggregation code.
5. Add fixtures exercising signature detection, parser output, and false-positive behavior.

Optional dependencies should be reported through handler availability; unsupported fields/formats belong in `limitations` or record warnings rather than placeholder values.

Every new format module must provide bounded content confirmation (a magic signature or real parser validation) and typed metadata extraction through `MetadataCapabilityHandler`. Extension-only results stay `CANDIDATE`. See the common [Stream format-module requirements](../libinfinistream/CAPABILITIES.md#format-module-validation-and-metadata-requirements). Metadata tests must read real fixture bytes and verify both positive and negative cases; a generic source-length record from `MetadataRegistry` is useful fallback data, not a substitute for format-specific fields the parser can produce.

## Current format modules

| Module | Detection / extracted fields | Limits |
|---|---|---|
| `meta-sega-megadrive` | Mega Drive / Genesis cartridge headers, 32X marker, Pico and Teradrive console fields, title, product code, stored checksum, ROM address range, region | Reports but does not validate the stored ROM checksum |
| `meta-sega-smd` | Interleaved SMD marker, deinterleaves the first block and reads its Mega Drive header | Reads the first 16 KiB block for metadata; stored checksum is not validated |
| `meta-sega-sms` | Master System / Mark III `TMR SEGA` headers, product/revision/region/checksum/size code | Header checksum and size code are not verified |
| `meta-sega-gamegear` | Game Gear `TMR SEGA` headers and corresponding header fields | Header checksum and size code are not verified |
| `meta-sega-sg1000` | `.sg1000`, `.sg`, and `.sc` extension candidates | SG-1000 cartridge dumps lack a universal in-band signature, so this is never reported as verified |
| `meta-sega-cd` | Sega CD boot signature in cooked or raw data-sector images | Does not parse cue sheets, CHD metadata, or multi-track layouts |
| `meta-sega-saturn` | Saturn boot identifier and title/product/version/date/publisher/area fields | Reads the embedded boot header; does not parse disc track tables |
| `meta-sega-dreamcast` | Dreamcast IP.BIN identifier and product/version/date/boot-file/publisher/title/area fields | Does not parse GDI/CDI containers or track tables |
| `meta-sega-vmu` | VMS description, creator, icon counts, eyecatch type, and payload length | Does not parse VMU filesystem images or VMI companion files; checksum is not validated |

The Sega aggregate depends on the modules above and contains no copied parser implementations. `meta-core` depends on `libinfinistream` for the shared capability and bounded-stream interfaces.

## Build and verification

From this repository checkout, with its sibling `../libinfinistream` available:

```sh
gradle :meta-sega:build
gradle :meta-sega:test
```

The integration tests create small byte-level format fixtures and exercise the real `ServiceLoader` registry and parser code. Optional hardware/firmware-backed emulation is outside this metadata library.

## Integration status

The shared metadata capability interface is available to every `libinfini*` handler through `libinfinistream`. Sega ROM parsers and the Sound playlist parser use it today. Canvas, Sound decoders, Scribe readers, Vault containers, Video and Store still have format-specific APIs that are covered by their library tests; migration of each of those metadata paths into the common record contract remains incremental.
