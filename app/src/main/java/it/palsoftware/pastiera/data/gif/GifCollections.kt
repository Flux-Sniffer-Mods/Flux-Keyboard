package it.palsoftware.pastiera.data.gif

import android.content.Context
import java.io.File

/**
 * The user's own GIFs: recently sent (newest first, up to [MAX_RECENTS]) and favourites
 * (long-press a GIF in GIF search to add or remove it; newest first). Kept in Pastiera's own
 * files on the phone.
 */
object GifCollections {
    const val MAX_RECENTS = 30
    const val MAX_FAVOURITES = 200
    private const val RECENTS_FILE = "gif-recents.json"
    private const val FAVOURITES_FILE = "gif-favourites.json"

    fun recents(context: Context): List<GifResult> = read(context, RECENTS_FILE)

    fun favourites(context: Context): List<GifResult> = read(context, FAVOURITES_FILE)

    fun isFavourite(context: Context, id: String): Boolean = favourites(context).any { it.id == id }

    /** A GIF was sent: it goes to the top of the recents. */
    fun addRecent(context: Context, gif: GifResult) {
        if (it.palsoftware.pastiera.core.IncognitoTyping.active) return
        write(context, RECENTS_FILE, (listOf(gif) + recents(context).filter { it.id != gif.id }).take(MAX_RECENTS))
    }

    /** Adds [gif] to the favourites, or takes it out; returns whether it's a favourite now. */
    fun toggleFavourite(context: Context, gif: GifResult): Boolean {
        val current = favourites(context)
        return if (current.any { it.id == gif.id }) {
            write(context, FAVOURITES_FILE, current.filter { it.id != gif.id })
            false
        } else {
            write(context, FAVOURITES_FILE, (listOf(gif) + current).take(MAX_FAVOURITES))
            true
        }
    }

    /** The user's GIFs whose titles contain every word of [query], favourites first. */
    fun matching(context: Context, query: String): List<GifResult> =
        matching(favourites(context) + recents(context), query)

    fun matching(gifs: List<GifResult>, query: String): List<GifResult> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return gifs.distinctBy { it.id }.filter { gif ->
            val title = gif.description.lowercase()
            words.all { it in title }
        }
    }

    private fun read(context: Context, name: String): List<GifResult> = runCatching {
        File(context.filesDir, name).takeIf { it.isFile }?.readText()?.let(KlipyGifs::resultsFromJson)
    }.getOrNull().orEmpty()

    private fun write(context: Context, name: String, gifs: List<GifResult>) {
        // Written whole then swapped in: an interrupted write never loses the favourites
        runCatching {
            val file = File(context.filesDir, name)
            val temp = File(context.filesDir, "$name.tmp")
            temp.writeText(KlipyGifs.resultsToJson(gifs))
            if (!temp.renameTo(file)) temp.delete()
        }
    }
}
