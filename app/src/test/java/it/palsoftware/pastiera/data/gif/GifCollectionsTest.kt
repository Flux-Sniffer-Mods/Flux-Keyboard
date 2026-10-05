package it.palsoftware.pastiera.data.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GifCollectionsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun gif(id: String, title: String = "gif $id") =
        GifResult(id, title, "https://p/$id.webp", "https://g/$id.gif", 200, 150)

    @Before
    fun startEmpty() {
        listOf("gif-recents.json", "gif-favourites.json").forEach { File(context.filesDir, it).delete() }
    }

    @Test
    fun recentsAreNewestFirstWithoutRepeatsAndCapped() {
        (1..GifCollections.MAX_RECENTS + 5).forEach { GifCollections.addRecent(context, gif("$it")) }
        GifCollections.addRecent(context, gif("7"))

        val recents = GifCollections.recents(context).map { it.id }
        assertEquals(GifCollections.MAX_RECENTS, recents.size)
        assertEquals("7", recents.first())
        assertEquals(1, recents.count { it == "7" })
    }

    @Test
    fun longPressTogglesAFavourite() {
        assertTrue(GifCollections.toggleFavourite(context, gif("1")))
        assertTrue(GifCollections.isFavourite(context, "1"))

        assertFalse(GifCollections.toggleFavourite(context, gif("1")))
        assertFalse(GifCollections.isFavourite(context, "1"))
    }

    @Test
    fun ownGifsMatchEveryWordOfTheSearchInTheirTitle() {
        val gifs = listOf(gif("1", "Happy Cat dancing"), gif("2", "Sad cat"), gif("3", "Happy dog"))

        assertEquals(listOf("1"), GifCollections.matching(gifs, "happy cat").map { it.id })
        assertEquals(listOf("1", "2"), GifCollections.matching(gifs, "CAT").map { it.id })
        assertTrue(GifCollections.matching(gifs, " ").isEmpty())
    }
}
