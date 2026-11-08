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

1. Create a module for the format (for example `meta-sega-saturn` or `meta-nintendo-nes`).
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
| `meta-nintendo-nes` | iNES / NES 2.0 mapper, submapper, PRG/CHR sizes, trainer, battery, and mirroring | Does not parse legacy UNIF or headerless dumps; payload checksums are not checked |
| `meta-nintendo-gameboy` | Game Boy / Color cartridge title, Nintendo logo, header checksum, controller, ROM/RAM sizes, region and licensee | Super Game Boy border metadata and ROM payload checksums are not parsed |
| `meta-nintendo-snes` | LoROM / HiROM title, mapping mode, cartridge type, sizes, region and checksum complement | Does not calculate the full payload checksum or parse ExHiROM |
| `meta-nintendo-n64` | z64 / v64 / n64 byte order, title, product ID, region and stored CRC1/CRC2 | CRC1/CRC2 are reported but not recalculated |
| `meta-nintendo-virtualboy` | Raw `.vb` / `.vboy` ROMs, validating power-of-two size and the embedded title, maker/game code and version near EOF | Raw ROMs have no universal magic; extraction needs random access or sufficient sequential budget to reach the embedded header |
| `meta-nintendo-gba` | GBA title, game/maker codes, unit/device codes, version and header complement checksum | Does not parse multiboot extensions or verify payload checksums |
| `meta-nintendo-ds` | DS / DSi title, game/maker codes, unit code, ARM9 offset, used size, Nintendo-logo and header CRC16 | Does not parse Nitro filesystem contents or localized banner titles |
| `meta-nintendo-gamecube` | Raw GameCube disc title, game ID, maker, region, disc number, revision and audio streaming flag | Does not parse Wii, WBFS, Wii U, encrypted packages, or multi-file disc containers |
| `meta-nintendo-wii` | Raw Wii disc title, game ID, maker, region, disc number, revision and audio streaming flag | Does not parse WBFS, Wii U, encrypted packages, or multi-file disc containers |
| `meta-atari-lynx` | Validated 64-byte LNX wrapper, cartridge title/manufacturer, bank pages, rotation and EEPROM flags | Headerless `.lyx`/raw images have no dependable identity and remain unclassified |
| `meta-atari-atr` | ATR sector geometry, density, declared/actual length, and DOS 2.x VTOC marker | Other Atari DOS variants and filesystems are not identified by this metadata parser |
| `meta-sony-psx-exe` | PS-X EXE header, initial PC, GP, load address and payload range | Does not identify game releases or inspect disc tracks |
| `meta-sony-psp-pbp` | PBP version and all eight validated section offsets | PARAM.SFO, icons and embedded disc data are not decoded |
| `meta-sony-pkg` | Legacy PS3/PSP/Vita, PS4 CNT, and PS5 FIH package signatures with public content IDs/range fields | Does not decrypt or unpack package contents; PS5 CNT and encrypted PFS are not parsed |
| `meta-xbox-xbe` | Original Xbox XBE header, title ID, image size and certificate title when present | Does not parse executable sections |
| `meta-xbox-xex` | Xbox 360 XEX1/XEX2 header, image/security offsets and optional-header count | Encrypted/compressed payload and game title metadata are not decoded |
| `meta-xbox-xvd` | Xbox One / Series XVD/XVC signature, volume/version/range fields | Encrypted NTFS data and integrity tree are not decoded; this is metadata identification, not browsing |
| `meta-xbox-stfs` | Xbox 360 CON/LIVE/PIRS package metadata, title ID, display name, content type and size | Package signature/hash validation, decryption and file listing are not implemented |
| `meta-snk-neo` | SNK Neo Geo TerraOnion `.neo` v1 header, title/manufacturer/year/NGH and P/S/M/V/C region lengths | Does not parse MAME/FBNeo sets or validate chip payloads |
| `meta-snk-ngp` | Neo Geo Pocket / Pocket Color copyright header, title, game number, version, cartridge type and ROM size | Does not identify commercial releases or validate save-block protection maps |
| `meta-3do-opera` | Cooked 3DO Opera volume header, label, comment, block geometry and root-directory range | Does not decode raw CD sector framing, ROM tags or encrypted/packed content |
| `meta-nec-pce` | PC Engine/TurboGrafx-16 and SuperGrafx HuCard bank geometry, copier-header clue and image length | Headerless format; reports a candidate from supported size plus extension and cannot extract a ROM title |
| `meta-atari-jaguar` | Jaguar `.j64` cartridge geometry and validated `0x601B` ABS development executable | Headerless `.j64` uses extension plus canonical size; `.jag`/`.bin` are not guessed as ROMs |
| `meta-gamepark-gp32` | GP32 GXB ARM memory ranges, ARM ELF executable metadata and tentative FXE identity | FXE is encrypted and currently only extension/size metadata is available; no decryptor or application-icon decoder |
| `meta-apple2-prodos` | ProDOS volume name, root-directory layout, and declared block count with content validation | Identifies ProDOS sector images; DOS 3.3 images are browsed by Vault but have no universal header signature |

The Sega and Nintendo aggregates depend on their system-specific modules and contain no copied parser implementations. `meta-core` depends on `libinfinistream` for the shared capability and bounded-stream interfaces.

Nintendo coverage currently includes NES, Game Boy, Virtual Boy, SNES, Nintendo 64, Game Boy Advance, Nintendo DS, and raw GameCube and Wii disc headers. 3DS, Wii U, Switch, and other Nintendo systems remain future modules. `meta-consoles` also includes Atari Lynx/Jaguar, PC Engine/TurboGrafx/SuperGrafx, GP32, 3DO, PlayStation, Xbox, and SNK Neo Geo/Neo Geo Pocket modules. Headerless ROMs are only classified when a parser can validate their known geometry; HuCard images are intentionally reported as candidates because they carry no universal magic or internal title.

The console metadata aggregate advertises only `IDENTIFY` and `METADATA`. It does not make ROMs or packages browsable containers. PlayStation CD/DVD images with ISO 9660/UDF are handled by Vault where those filesystems validate. Xbox XVD/XVC and STFS payloads remain encrypted and are not browsed. Vault browses cooked 3DO Opera discs, BBC DFS `.ssd`/`.dsd`, Apple II DOS 3.3/ProDOS 140 KiB images, and Amstrad CPC data/system CP/M images in CPCEMU standard or extended `.dsk` containers. Raw 2352-byte 3DO sectors, Apple II nibble/WOZ images, Amstrad IBM-format DSK layouts and PCW-specific CP/M variants are not decoded. MSX `.dsk` images with a valid FAT12 filesystem are covered by Vault's existing FAT reader.

## Build and verification

From this repository checkout, with its sibling `../libinfinistream` available:

```sh
gradle :meta-sega:build
gradle :meta-sega:test
gradle :meta-nintendo:build
```

The integration tests create small byte-level format fixtures and exercise the real `ServiceLoader` registry and parser code. Optional hardware/firmware-backed emulation is outside this metadata library.

## Integration status

The shared metadata capability interface is available to every `libinfini*` handler through `libinfinistream`. Sega and Nintendo ROM parsers and the Sound playlist parser use it today. Canvas, Sound decoders, Scribe readers, Vault containers, Video and Store still have format-specific APIs that are covered by their library tests; migration of each of those metadata paths into the common record contract remains incremental.
