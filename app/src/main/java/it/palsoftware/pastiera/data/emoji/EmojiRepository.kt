package it.palsoftware.pastiera.data.emoji

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads emoji categories from asset text files.
 * Files live under assets/common/emoji (one emoji per line; first token is the base, the rest are variants).
 * Applies minApi filtering based on minApi.txt where each line starts with the API level followed by emojis.
 *
 * UI-agnostic: can be reused for the picker dialog and future emoji keyboard.
 */
object EmojiRepository {
    private const val EMOJI_ASSET_DIR = "common/emoji"
    const val RECENTS_CATEGORY_ID = "RECENTS"

    data class EmojiEntry(val base: String, val variants: List<String>)
    data class EmojiCategory(
        val id: String,
        val displayNameRes: Int?,
        val emojis: List<EmojiEntry>
    )

    /** Everything in the assets, unfiltered, plus which sequences the system font can draw. */
    private class LoadedData(
        val all: List<EmojiCategory>,
        val known: Set<String>,
        val systemAvailable: Set<String>,
        val availability: EmojiAvailability
    )

    @Volatile
    private var loadedData: LoadedData? = null
    private var cachedCategories: List<EmojiCategory>? = null

    /** Emoji the phone's own font can draw. */
    suspend fun getEmojiCategories(context: Context): List<EmojiCategory> {
        return cachedCategories
            ?: filterCategories(loadAll(context)) { false }.also { cachedCategories = it }
    }

    /**
     * Emoji the phone's own font can draw, plus any for which [extraAvailable] returns true
     * (e.g. emoji the current text field renders through EmojiCompat).
     */
    suspend fun getEmojiCategories(
        context: Context,
        extraAvailable: ((String) -> Boolean)?
    ): List<EmojiCategory> {
        if (extraAvailable == null) return getEmojiCategories(context)
        return filterCategories(loadAll(context), extraAvailable)
    }

    /** Every emoji in the assets, including ones this phone cannot draw. */
    suspend fun getAllEmojiCategories(context: Context): List<EmojiCategory> = loadAll(context).all

    /** True if the phone's own font can draw [emoji]. True for everything before the data loads. */
    fun isSystemAvailable(emoji: String): Boolean {
        val data = loadedData ?: return true
        if (emoji in data.systemAvailable) return true
        return emoji !in data.known && data.availability.isAvailable(emoji)
    }

    /** [entry] with only the allowed variants, or null if its base emoji isn't allowed. */
    fun filterEntry(entry: EmojiEntry, isAllowed: (String) -> Boolean): EmojiEntry? {
        if (!isAllowed(entry.base)) return null
        return entry.copy(variants = entry.variants.filter(isAllowed))
    }

    fun clearCache() {
        cachedCategories = null
        loadedData = null
    }

    /**
     * Looks up variants for an emoji from the cached categories.
     * Returns empty list if not found or cache not loaded.
     */
    fun getVariantsForEmoji(emoji: String): List<String> {
        val categories = loadedData?.all ?: cachedCategories ?: return emptyList()
        for (category in categories) {
            for (entry in category.emojis) {
                if (entry.base == emoji) {
                    return entry.variants
                }
                // Also check if the emoji is a variant
                if (entry.variants.contains(emoji)) {
                    // Return other variants plus the base
                    return (listOf(entry.base) + entry.variants).filter { it != emoji }
                }
            }
        }
        return emptyList()
    }

    /**
     * Utility for future keyboard pagination/chunking without re-parsing assets.
     */
    fun asPaged(
        categories: List<EmojiCategory>,
        pageSize: Int = 50
    ): Map<String, List<List<EmojiEntry>>> {
        return categories.associate { category ->
            category.id to category.emojis.chunked(pageSize)
        }
    }

    private fun filterCategories(
        data: LoadedData,
        extraAvailable: (String) -> Boolean
    ): List<EmojiCategory> {
        val allowed: (String) -> Boolean = { it in data.systemAvailable || extraAvailable(it) }
        return data.all.mapNotNull { category ->
            val emojis = category.emojis.mapNotNull { filterEntry(it, allowed) }
            if (emojis.isEmpty()) null else category.copy(emojis = emojis)
        }
    }

    private suspend fun loadAll(context: Context): LoadedData =
        loadedData ?: loadEmojiData(context).also { loadedData = it }

    private suspend fun loadEmojiData(context: Context): LoadedData = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val files = assetManager.list(EMOJI_ASSET_DIR)
            ?.filter { it.endsWith(".txt") && it != "minApi.txt" }
            .orEmpty()

        // Define custom category order
        val categoryOrder = listOf(
            "SMILEYS_AND_EMOTION.txt",
            "PEOPLE_AND_BODY.txt",
            "ANIMALS_AND_NATURE.txt",
            "FOOD_AND_DRINK.txt",
            "TRAVEL_AND_PLACES.txt",
            "ACTIVITIES.txt",
            "OBJECTS.txt",
            "SYMBOLS.txt",
            "FLAGS.txt"
        )

        val sortedFiles = files.sortedBy { fileName ->
            val index = categoryOrder.indexOf(fileName)
            if (index >= 0) index else Int.MAX_VALUE // Unknown files go to the end
        }

        val availability = EmojiAvailability.fromAssets(assetManager)

        val all = sortedFiles.mapNotNull { fileName ->
            val emojis = parseEmojiFile(context, fileName)
            if (emojis.isEmpty()) return@mapNotNull null
            EmojiCategory(
                id = fileName.substringBefore(".txt"),
                displayNameRes = mapCategoryRes(fileName),
                emojis = emojis
            )
        }
        val known = all.flatMap { category ->
            category.emojis.flatMap { listOf(it.base) + it.variants }
        }.toSet()
        LoadedData(
            all = all,
            known = known,
            systemAvailable = known.filterTo(HashSet()) { availability.isAvailable(it) },
            availability = availability
        )
    }

    private fun parseEmojiFile(context: Context, fileName: String): List<EmojiEntry> {
        val assetPath = "$EMOJI_ASSET_DIR/$fileName"

        return runCatching {
            context.assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input)).lineSequence().mapNotNull { line ->
                    val tokens = line.split(" ").filter { it.isNotBlank() }
                    if (tokens.isEmpty()) return@mapNotNull null
                    EmojiEntry(base = tokens.first(), variants = tokens.drop(1))
                }.toList()
            }
        }.getOrElse { emptyList() }
    }

    private fun mapCategoryRes(fileName: String): Int? {
        return when (fileName.substringBefore(".txt")) {
            "SMILEYS_AND_EMOTION" -> it.palsoftware.pastiera.R.string.emoji_category_smileys_and_emotion
            "PEOPLE_AND_BODY" -> it.palsoftware.pastiera.R.string.emoji_category_people_and_body
            "ANIMALS_AND_NATURE" -> it.palsoftware.pastiera.R.string.emoji_category_animals_and_nature
            "FOOD_AND_DRINK" -> it.palsoftware.pastiera.R.string.emoji_category_food_and_drink
            "TRAVEL_AND_PLACES" -> it.palsoftware.pastiera.R.string.emoji_category_travel_and_places
            "ACTIVITIES" -> it.palsoftware.pastiera.R.string.emoji_category_activities
            "OBJECTS" -> it.palsoftware.pastiera.R.string.emoji_category_objects
            "SYMBOLS" -> it.palsoftware.pastiera.R.string.emoji_category_symbols
            "FLAGS" -> it.palsoftware.pastiera.R.string.emoji_category_flags
            else -> null
        }
    }

    /**
     * Maps category ID to a Material icon drawable resource ID for tab display.
     */
    fun getCategoryIconRes(categoryId: String): Int {
        return when (categoryId) {
            RECENTS_CATEGORY_ID -> it.palsoftware.pastiera.R.drawable.ic_schedule_24
            "SMILEYS_AND_EMOTION" -> it.palsoftware.pastiera.R.drawable.ic_sentiment_satisfied_24
            "PEOPLE_AND_BODY" -> it.palsoftware.pastiera.R.drawable.ic_emoji_people_24
            "ANIMALS_AND_NATURE" -> it.palsoftware.pastiera.R.drawable.ic_pets_24
            "FOOD_AND_DRINK" -> it.palsoftware.pastiera.R.drawable.ic_restaurant_24
            "TRAVEL_AND_PLACES" -> it.palsoftware.pastiera.R.drawable.ic_flight_24
            "ACTIVITIES" -> it.palsoftware.pastiera.R.drawable.ic_sports_soccer_24
            "OBJECTS" -> it.palsoftware.pastiera.R.drawable.ic_lightbulb_24
            "SYMBOLS" -> it.palsoftware.pastiera.R.drawable.ic_emoji_symbols_24
            "FLAGS" -> it.palsoftware.pastiera.R.drawable.ic_flag_24
            else -> it.palsoftware.pastiera.R.drawable.ic_schedule_24
        }
    }
}
