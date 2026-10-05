package it.palsoftware.pastiera.data.symbols

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaomojiTest {
    private val lines = File("src/main/assets/common/kaomoji.txt").takeIf { it.isFile }?.readLines()
        ?: File("app/src/main/assets/common/kaomoji.txt").readLines()
    private val entries = Kaomoji.parse(lines)

    private fun search(query: String) = SymbolSearch.search(query, entries).map { it.symbol }

    @Test
    fun famousNamesFindTheirKaomoji() {
        assertTrue("( ͡° ͜ʖ ͡°)" in search("lenny face").take(3))
        assertEquals("(╯°□°）╯︵ ┻━┻", search("table flip").first())
        assertTrue("¯\\_(ツ)_/¯" in search("shrug"))
        assertTrue("ಠ_ಠ" in search("disapproval"))
    }

    @Test
    fun moodsActionsAndExpressionsAreSearchable() {
        assertTrue("(╥﹏╥)" in search("sad"))
        assertTrue("(づ｡◕‿‿◕｡)づ" in search("hug"))
        assertTrue("(〃▽〃)" in search("blush"))
        assertTrue("(=^･ω･^=)" in search("cat"))
        assertTrue(search("mood").size > 100)
    }

    @Test
    fun notesAreNotKaomojiAndNoneRepeat() {
        assertTrue(entries.none { it.symbol.startsWith("##") || '\t' in it.symbol })
        assertEquals(entries.size, entries.map { it.symbol }.distinct().size)
        assertTrue(entries.size > 300)
    }

    @Test
    fun searchTabsJumpToTheStartOfTheirOwnGroup() {
        val starts = Kaomoji.groupStarts(lines)
        // The picker's kaomoji tabs (Joy, Love, Sad, Mad, Wow, Shy, Hug, Hi) by their words
        for ((word, heading) in listOf(
            "joy" to "Mood · Joy", "love" to "Mood · Love", "sad" to "Mood · Sad",
            "angry" to "Mood · Angry", "surprise" to "Mood · Surprise", "shy" to "Expression · Shy",
            "hug" to "Action · Hugs", "hello" to "Action · Greetings"
        )) {
            val start = starts.first { (words, _) -> word in words }.second
            val firstOfGroup = lines.dropWhile { !it.startsWith("## $heading") }.drop(1)
                .first { it.isNotBlank() && !it.startsWith("## ") }.substringBefore('\t')
            assertEquals(word, entries.indexOfFirst { it.symbol == firstOfGroup }, start)
        }
    }
}
