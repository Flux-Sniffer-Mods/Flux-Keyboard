package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameIdsTest {
    private fun ascii(text: String, size: Int) = text.map { it.code } + List(size - text.length) { 0 }

    @Test
    fun aDiscHeaderGivesItsIdAndTitle() {
        val header = ascii("GZLE01", 0x20) + ascii("THE LEGEND OF ZELDA THE WIND WAKER", 0x40)
        val found = GameIds.discFrom(header)
        assertEquals("GZLE01", found?.id)
        assertEquals("The Legend Of Zelda The Wind Waker", found?.title?.let { GameIds.tidy(it) })
        assertNull(GameIds.discFrom(List(0x60) { 0 }))
    }

    @Test
    fun eachKindOfDiscFileHasItsHeaderWhereItKeepsIt() {
        assertEquals(0L, GameIds.discHeaderOffset("a.iso"))
        assertEquals(0x58L, GameIds.discHeaderOffset("a.rvz"))
        assertEquals(0x200L, GameIds.discHeaderOffset("a.wbfs"))
        assertNull(GameIds.discHeaderOffset("a.gcz"))
    }

    @Test
    fun aCartridgeGivesItsProductCode() {
        val header = ascii("NCCH", 0x50) + ascii("CTR-P-AREE", 0x10)
        assertEquals("AREE", GameIds.cartridgeFrom(header)?.id)
    }

    @Test
    fun gameTdbsListIsRead() {
        val titles = GameIds.parse("TITLES = https://www.gametdb.com (type: Wii language: EN)\nGZLE01 = The Legend of Zelda: The Wind Waker\nRMGE01 = Super Mario Galaxy\n")
        assertEquals("Super Mario Galaxy", titles["RMGE01"])
        assertEquals(2, titles.size)
    }

    @Test
    fun aParamSfoGivesItsTitle() {
        // Header, two index entries, the key table, then the data table
        val keys = "DISC_ID\u0000TITLE\u0000".map { it.code }
        val data = ascii("ULUS10041", 16) + ascii("Lumines", 16)
        val keyStart = 20 + 32
        val dataStart = keyStart + keys.size
        fun le(v: Int, n: Int) = List(n) { (v shr (8 * it)) and 0xFF }
        val header = listOf(0, 'P'.code, 'S'.code, 'F'.code) + le(0x101, 4) + le(keyStart, 4) + le(dataStart, 4) + le(2, 4)
        val index = le(0, 2) + le(0x0204, 2) + le(10, 4) + le(16, 4) + le(0, 4) +
            le(8, 2) + le(0x0204, 2) + le(8, 4) + le(16, 4) + le(16, 4)
        val found = GameIds.sfoFrom(header + index + keys + data)
        assertEquals("Lumines", found?.title)
        assertEquals("ULUS10041", found?.id)
    }

    @Test
    fun anIdInTheFilesNameIsUsed() {
        assertEquals("GZLE01", GameIds.fromName("Dolphin", "Zelda Wind Waker [GZLE01]")?.id)
        assertEquals("Zelda Wind Waker", GameIds.fromName("Dolphin", "Zelda Wind Waker [GZLE01]")?.title)
        assertNull(GameIds.fromName("Dolphin", "Zelda Wind Waker (USA)"))
        val switch = GameIds.fromName("Eden", "Super Mario Odyssey [0100000000010000][v0]")
        assertEquals("0100000000010000", switch?.id)
        assertEquals("Super Mario Odyssey", switch?.title)
        assertEquals("ULUS10041", GameIds.fromName("PPSSPP", "Lumines ULUS-10041")?.id)
        assertEquals("AREE", GameIds.fromName("Azahar", "Animal Crossing CTR-P-AREE")?.id)
    }

    @Test
    fun aFilesNameIsTidiedIntoATitle() {
        assertEquals("Super Mario Galaxy", GameIds.cleanName("Super Mario Galaxy (USA) (En,Fr,Es) [!]"))
        assertEquals("The Legend of Zelda - Twilight Princess", GameIds.cleanName("Legend of Zelda, The - Twilight Princess (Europe) (Rev 1)"))
        assertEquals("Metroid Prime", GameIds.cleanName("metroid_prime_NTSC"))
        assertEquals("Super Smash Bros Brawl", GameIds.cleanName("Super.Smash.Bros.Brawl.v1.01"))
    }

    @Test
    fun aTitleIsGuessedLooselyFromTheName() {
        val titles = mapOf(
            "GZLE01" to "The Legend of Zelda: The Wind Waker",
            "GZLP01" to "The Legend of Zelda: The Wind Waker",
            "RMCE01" to "Mario Kart Wii",
            "RSBE01" to "Super Smash Bros. Brawl",
            "SB4E01" to "Super Mario Galaxy 2"
        )
        assertEquals("GZLE01", GameIds.guess(titles, "Zelda Wind Waker")?.id)
        assertEquals("RMCE01", GameIds.guess(titles, "Mario Kart Wii")?.id)
        assertEquals("RSBE01", GameIds.guess(titles, "Super Smash Bros Brawl")?.id)
        assertEquals("SB4E01", GameIds.guess(titles, "Super Mario Galaxy II")?.id)
        assertNull(GameIds.guess(titles, "Animal Crossing"))
    }

    @Test
    fun aGuessTakesTheRegionInTheFilesName() {
        val titles = mapOf("RMCE01" to "Mario Kart Wii", "RMCP01" to "Mario Kart Wii", "RMCJ01" to "Mario Kart Wii")
        val file = "Mario Kart Wii (Europe, Australia) (En,Fr,De,Es,It)"
        assertEquals('P', GameIds.regionOf(file))
        assertEquals("RMCP01", GameIds.guess(titles, GameIds.cleanName(file), GameIds.regionOf(file))?.id)
        assertEquals("RMCE01", GameIds.guess(titles, "Mario Kart Wii", GameIds.regionOf("Mario Kart Wii"))?.id)
    }
}
