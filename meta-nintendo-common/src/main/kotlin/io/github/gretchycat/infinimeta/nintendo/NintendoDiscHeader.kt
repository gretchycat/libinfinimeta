package io.github.gretchycat.infinimeta.nintendo

/** Shared boot-header facts for raw GameCube and Wii disc images. */
data class NintendoDiscHeader(
    val system: String,
    val formatId: String,
    val mimeType: String,
    val title: String,
    val gameId: String,
    val makerCode: String,
    val region: String,
    val discNumber: Int,
    val revision: Int,
    val streamingAudio: Boolean
)

object NintendoDiscHeaderParser {
    /** Returns only the requested console type; the two console magic words are mutually exclusive. */
    fun parse(bytes: ByteArray, wii: Boolean): NintendoDiscHeader? {
        if (bytes.size < 0x440) return null
        val gameId = NintendoBytes.text(bytes, 0, 6)
        if (gameId.length != 6 || !gameId.all { it.isLetterOrDigit() }) return null
        val gameCubeMagic = NintendoBytes.u32be(bytes, 0x1c) == GAMECUBE_MAGIC
        val wiiMagic = NintendoBytes.u32be(bytes, 0x18) == WII_MAGIC
        if ((wii && (!wiiMagic || gameCubeMagic)) || (!wii && (!gameCubeMagic || wiiMagic))) return null
        val title = NintendoBytes.text(bytes, 0x20, 0x3e0)
        if (title.length < 2 || NintendoBytes.printableScore(title) < 0.8) return null
        val region = when (gameId.getOrNull(3)?.uppercaseChar()) {
            'J' -> "Japan"; 'E' -> "North America"; 'P' -> "Europe / Australia"; 'D' -> "Germany"
            'F' -> "France"; 'I' -> "Italy"; 'S' -> "Spain"; 'K' -> "Korea"; 'W' -> "Taiwan"
            else -> "Unknown"
        }
        return if (wii) NintendoDiscHeader("Nintendo Wii", "wii-disc-image", "application/x-wii-disc-image", title,
            gameId, NintendoBytes.text(bytes, 4, 2), region, bytes[6].toInt() and 0xff,
            bytes[7].toInt() and 0xff, bytes[8].toInt() and 1 != 0)
        else NintendoDiscHeader("Nintendo GameCube", "gamecube-disc-image", "application/x-gamecube-disc-image", title,
            gameId, NintendoBytes.text(bytes, 4, 2), region, bytes[6].toInt() and 0xff,
            bytes[7].toInt() and 0xff, bytes[8].toInt() and 1 != 0)
    }

    private const val GAMECUBE_MAGIC = 0xc2339f3dL
    private const val WII_MAGIC = 0x5d1c9ea3L
}
