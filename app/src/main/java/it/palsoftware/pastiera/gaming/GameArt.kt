package it.palsoftware.pastiera.gaming

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.isOfflineMode
import java.io.File

/**
 * A profile's game art for the list: a Steam game's from Steam's own store images (GameNative
 * and GameHub), a Wii, GameCube or 3DS game's cover from GameTDB by its game ID, else the
 * libretro thumbnail sets' box art by the game file's name. Fetched once and kept; none in
 * offline mode, or when nothing is found (the app's icon shows then).
 */
object GameArt {
    private fun dir(context: Context) = File(context.filesDir, "game_art")

    /** The art for [profile], fetched if need be (blocking); null when there's none. */
    fun load(context: Context, profile: GameProfile): Bitmap? {
        // Kept apart with SteamGridDB's key, so art not found without it is looked for again
        val file = File(dir(context), profile.id + (if (key.isEmpty()) "" else "-sgdb") + ".img")
        // An empty file: looked for before and nothing found
        if (file.exists()) return if (file.length() > 0) BitmapFactory.decodeFile(file.path) else null
        if (SettingsManager.isOfflineMode(context)) return null
        val urls = steamGridDb(profile) + urls(context, profile)
        if (urls.isEmpty()) return null
        dir(context).mkdirs()
        for (url in urls) {
            val bytes = fetch(url) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            file.writeBytes(bytes)
            return bitmap
        }
        file.writeBytes(ByteArray(0))
        return null
    }

    /** Forgets [profile]'s art, so it's looked for again. */
    fun forget(context: Context, profile: GameProfile) {
        File(dir(context), profile.id + ".img").delete()
        File(dir(context), profile.id + "-sgdb.img").delete()
    }

    /** Where [profile]'s game art may be, best first. */
    internal fun urls(context: Context, profile: GameProfile): List<String> {
        val launch = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: return emptyList()
        steamId(launch)?.let { id ->
            return listOf("library_600x900.jpg", "header.jpg").map { "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/$it" }
        }
        val app = GameApps.appFor(profile) ?: return emptyList()
        val path = profile.launch?.let { GameFiles.gameFile(it) } ?: return emptyList()
        val urls = mutableListOf<String>()
        val id = GameIds.id(context, app, path)
        if (id != null) when (app) {
            "Dolphin" -> listOf("US", "EN", "JA", "FR", "DE").forEach { urls += "https://art.gametdb.com/wii/cover/$it/$id.png" }
            "Azahar" -> listOf("US", "EN", "JA").forEach { urls += "https://art.gametdb.com/3ds/box/$it/$id.png" }
        }
        val system = when (app) {
            "Dolphin" -> if (GameFiles.isWii(path)) "Nintendo - Wii" else "Nintendo - GameCube"
            "PPSSPP" -> "Sony - PlayStation Portable"
            "Azahar" -> "Nintendo - Nintendo 3DS"
            else -> null
        }
        if (system != null) urls += libretro(system, path.substringAfterLast('/').substringBeforeLast('.'))
        return urls
    }

    // ---- SteamGridDB (with the key the build was given) ----

    private val key get() = it.palsoftware.pastiera.BuildConfig.STEAMGRIDDB_API_KEY

    /** SteamGridDB's square icon, else its grid art, for [profile]'s game: by Steam ID, else by name. */
    internal fun steamGridDb(profile: GameProfile): List<String> {
        if (key.isEmpty()) return emptyList()
        val launch = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val game = launch?.let { steamId(it) }?.let { id -> api("games/steam/$id")?.optJSONObject("data")?.optInt("id") }
            ?: api("search/autocomplete/" + java.net.URLEncoder.encode(profile.name, "UTF-8").replace("+", "%20"))
                ?.optJSONArray("data")?.optJSONObject(0)?.optInt("id")
            ?: return emptyList()
        if (game <= 0) return emptyList()
        fun first(path: String) = api(path)?.optJSONArray("data")?.optJSONObject(0)?.let { it.optString("thumb").ifEmpty { it.optString("url") } }
        return listOfNotNull(
            first("icons/game/$game"),
            first("grids/game/$game?dimensions=512x512,1024x1024"),
            first("grids/game/$game")
        ).filter { it.startsWith("https://") && !it.endsWith(".ico") }
    }

    private fun api(path: String): org.json.JSONObject? = runCatching {
        val connection = java.net.URL("https://www.steamgriddb.com/api/v2/$path").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Authorization", "Bearer $key")
        try {
            if (connection.responseCode != 200) null
            else org.json.JSONObject(connection.inputStream.use { it.readBytes().decodeToString() }).takeIf { it.optBoolean("success") }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** A Steam game's ID: GameNative's app_id with its Steam source, or GameHub's steamAppId. */
    internal fun steamId(launch: Intent): String? {
        launch.getStringExtra("steamAppId")?.takeIf { id -> id.all { it.isDigit() } }?.let { return it }
        val source = launch.getStringExtra("game_source")
        val id = launch.getIntExtra("app_id", -1)
        return if (id > 0 && (source == null || source == "STEAM")) id.toString() else null
    }

    /** libretro's box art for [name] (a No-Intro name, as game files are usually called). */
    internal fun libretro(system: String, name: String): String {
        val repo = system.replace(' ', '_')
        // libretro's own rule: these characters become _ in thumbnail names
        val file = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
        val encoded = java.net.URLEncoder.encode(file, "UTF-8").replace("+", "%20")
        return "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/Named_Boxarts/$encoded.png"
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Flux Keyboard")
        try {
            if (connection.responseCode != 200) null
            else connection.inputStream.use { it.readBytes() }.takeIf { it.size in 1..4_000_000 }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
