package it.palsoftware.pastiera.gaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.isOfflineMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Xbox Cloud Gaming's games, from Microsoft's public catalogue (no sign-in): the cloud games
 * list, then each game's title and box art. Kept a week; never fetched in offline mode. Each
 * game starts in the Xbox app (Game Pass) through its play link, so it can have settings of its
 * own like any other game.
 */
object XboxCloud {
    /** The catalogue's list of every cloud game. */
    private const val CLOUD_LIST = "https://catalog.gamepass.com/sigls/v2?id=29a81209-df6f-41fd-a528-2ae6b91f719c&language=en-us&market=US"
    private const val PRODUCTS = "https://displaycatalog.mp.microsoft.com/v7.0/products?market=US&languages=en-us&bigIds="
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    data class CloudGame(val id: String, val name: String, val art: String?)

    private fun file(context: Context) = File(context.filesDir, "xbox_cloud.json")

    /** The Xbox app installed that plays cloud games, if any. */
    fun app(context: Context): String? = listOf("com.gamepass", "com.gamepass.beta", "com.microsoft.xboxone.smartglass", "com.microsoft.xboxone.smartglass.beta")
        .firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }

    /** Every cloud game, A to Z (blocking: fetched when the copy kept is a week old). */
    fun games(context: Context): List<CloudGame> {
        val kept = file(context)
        val fresh = kept.exists() && System.currentTimeMillis() - kept.lastModified() < KEEP_MS
        if (!fresh && !SettingsManager.isOfflineMode(context)) fetch()?.let { games ->
            kept.writeText(JSONArray(games.map { JSONObject().put("id", it.id).put("name", it.name).put("art", it.art ?: "") }).toString())
        }
        return runCatching {
            val list = JSONArray(kept.readText())
            List(list.length()) { i ->
                val o = list.getJSONObject(i)
                CloudGame(o.getString("id"), o.getString("name"), o.optString("art").ifEmpty { null })
            }
        }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }

    private fun fetch(): List<CloudGame>? = runCatching {
        val ids = parseIds(get(CLOUD_LIST) ?: return null)
        ids.chunked(50).flatMap { chunk -> parseProducts(get(PRODUCTS + chunk.joinToString(",")) ?: return@flatMap emptyList()) }
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** The cloud list's game IDs (its first entry describes the list itself). */
    internal fun parseIds(json: String): List<String> {
        val list = JSONArray(json)
        return (0 until list.length()).mapNotNull { list.getJSONObject(it).optString("id").ifEmpty { null } }
    }

    /** Each product's title and box art (the poster, else the box art), from the catalogue's answer. */
    internal fun parseProducts(json: String): List<CloudGame> {
        val products = JSONObject(json).optJSONArray("Products") ?: return emptyList()
        return (0 until products.length()).mapNotNull { i ->
            val product = products.getJSONObject(i)
            val props = product.optJSONArray("LocalizedProperties")?.optJSONObject(0) ?: return@mapNotNull null
            val images = props.optJSONArray("Images")
            val art = listOf("Poster", "BoxArt").firstNotNullOfOrNull { purpose ->
                images?.let { list -> (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("ImagePurpose") == purpose } }
                    ?.optString("Uri")?.let { if (it.startsWith("//")) "https:$it" else it }
            }
            CloudGame(product.getString("ProductId"), props.optString("ProductTitle").ifEmpty { return@mapNotNull null }, art)
        }
    }

    /** What starts [game]: its play link, opened by the Xbox app [pkg]. */
    fun launchFor(game: CloudGame, pkg: String): String {
        val slug = game.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "game" }
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.xbox.com/play/launch/$slug/${game.id}"))
            .setPackage(pkg).toUri(Intent.URI_INTENT_SCHEME)
    }

    /** The cloud game a launch starts, by its play link's product ID. */
    internal fun idOf(launch: String): String? = Regex("xbox\\.com/play/launch/[^/]+/([A-Z0-9]{12})", RegexOption.IGNORE_CASE).find(launch)?.groupValues?.get(1)

    /** A cloud game's box art, for [launch], from the copy kept. */
    fun artFor(context: Context, launch: String): String? {
        val id = idOf(launch) ?: return null
        return games(context).firstOrNull { it.id.equals(id, ignoreCase = true) }?.art
    }

    /** Gaming mode's game for [game], played in the Xbox app [pkg]. */
    fun asGame(game: CloudGame, pkg: String): GameLibrary.Game = GameLibrary.Game(game.name, pkg, launch = launchFor(game, pkg))

    private fun get(url: String): String? = runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Flux Keyboard")
        try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
