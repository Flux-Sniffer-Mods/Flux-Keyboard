package it.palsoftware.pastiera.data.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SymbolSearchTest {
    // The bundled list (on the phone, the fonts then decide what's shown)
    private val entries = SymbolSearch.readAsset(org.robolectric.RuntimeEnvironment.getApplication())

    @Before
    fun forgetGlyphChecks() {
        SymbolSearch.clearVerdicts()
    }

    private fun first(query: String): String = SymbolSearch.search(query, entries).first().symbol

    @Test
    fun findsSymbolsByTheirNames() {
        assertEquals("°", first("degree"))
        assertEquals("€", first("euro"))
        assertEquals("✓", first("check mark"))
        assertEquals("π", first("pi"))
        assertEquals("×", first("multiplication"))
    }

    @Test
    fun ranksTheClosestNameFirst() {
        // "rightwards arrow" beats "left right arrow" and longer arrow names
        assertEquals("→", first("right arrow"))
        assertEquals("←", first("left arrow"))
    }

    @Test
    fun everydayWordsFindSymbolsWithOtherUnicodeNames() {
        assertEquals("™", first("trademark"))
        assertEquals("¶", first("paragraph"))
        assertEquals("≈", first("approx"))
        assertEquals("✓", first("tick"))
    }

    @Test
    fun theSymbolItselfAndABlankQueryWork() {
        assertEquals("€", first("€"))
        assertEquals(entries.size, SymbolSearch.search("  ", entries).size)
        assertTrue(entries.size > 1000)
    }

    @Test
    fun bundledListHasEveryKindOfSymbolButNotColourEmoji() {
        assertTrue(entries.size > 8000)
        val names = entries.associate { it.symbol to it.name }
        assertEquals("degree sign", names["°"])
        assertEquals("black chess knight", names["\u265E"])
        assertEquals("degree celsius", names["\u2103"])
        assertEquals("roman numeral twelve", names["\u216B"])
        assertEquals("alchemical symbol for quintessence", names[String(Character.toChars(0x1F700))])
        // Colour emoji have the emoji picker (and its search)
        assertNull(names[String(Character.toChars(0x1F600))])
        assertNull(names[String(Character.toChars(0x1F431))])
    }

    @Test
    fun onlyAboutAScreenfulIsCheckedBeforeShowing() {
        val found = entries.take(200)
        val cantDraw = found[3].symbol
        var checks = 0

        val shown = SymbolSearch.renderable(found, checkFirst = 10) { checks++; it != cantDraw }

        assertEquals(10, checks)
        assertEquals(199, shown.size)
        assertTrue(shown.none { it.symbol == cantDraw })
    }

    @Test
    fun answersAreRememberedSoNothingIsCheckedTwice() {
        val found = entries.take(200)
        SymbolSearch.renderable(found, checkFirst = 10) { it != found[3].symbol }
        var checks = 0

        // The first 10 are known; the next 10 are checked now (and can't be drawn here)
        val shown = SymbolSearch.renderable(found, checkFirst = 10) { checks++; false }

        assertEquals(10, checks)
        assertEquals(200 - 1 - 10, shown.size)
        assertTrue(SymbolSearch.canRender(found[0].symbol) { false })
    }

    @Test
    fun savedAnswersAreUsedOnlyOnTheSameAndroidBuild() {
        val file = File.createTempFile("glyphs", ".tsv")
        val answers = mapOf(0xB0 to true, 0x1D800 to false)

        SymbolSearch.saveVerdicts(file, "build-1", answers)

        assertEquals(answers, SymbolSearch.loadVerdicts(file, "build-1"))
        // After a system update the fonts may differ: check again
        assertNull(SymbolSearch.loadVerdicts(file, "build-2"))
    }

    @Test
    fun recentlyUsedSymbolsComeFirstNewestFirst() {
        val found = listOf("→", "←", "↑", "↓").map { SymbolSearch.Entry(it, "arrow $it") }

        val ordered = SymbolSearch.recentsFirst(found, recents = listOf("↓", "x", "←"))

        assertEquals(listOf("↓", "←", "→", "↑"), ordered.map { it.symbol })
    }

    @Test
    fun pickedSymbolsAreRememberedNewestFirst() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        File(context.filesDir, "symbol-recents.txt").delete()

        SymbolSearch.addRecent(context, "°")
        SymbolSearch.addRecent(context, "™")
        SymbolSearch.addRecent(context, "°")

        assertEquals(listOf("°", "™"), SymbolSearch.recentSymbols(context))
    }
}
