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
 * offline mode, or when nothing is found (the app's icon shows then). Art the user picks for a
 * game comes before all of these.
 */
object GameArt {
    private fun dir(context: Context) = File(context.filesDir, "game_art")

    /** The art for [profile], fetched if need be (blocking); null when there's none. */
    fun load(context: Context, profile: GameProfile): Bitmap? {
        custom(context, profile)?.let { return it }
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

    /**
     * [profile]'s art upright, as Moonlight's game picker shows it (2:3): its own upright art
     * where there is some, else its art over a dimmed, filled copy of itself.
     */
    fun loadPortrait(context: Context, profile: GameProfile): Bitmap? {
        uprightFile(context, profile).takeIf { it.length() > 0 }?.let { file -> BitmapFactory.decodeFile(file.path)?.let { return it } }
        custom(context, profile)?.let { return portrait(it) }
        val file = File(dir(context), profile.id + "-portrait" + (if (key.isEmpty()) "" else "-sgdb") + ".img")
        if (file.exists()) return if (file.length() > 0) BitmapFactory.decodeFile(file.path) else load(context, profile)?.let(::portrait)
        if (!SettingsManager.isOfflineMode(context)) {
            dir(context).mkdirs()
            for (url in steamGridDbPortrait(profile) + urls(context, profile)) {
                val bitmap = fetch(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: continue
                if (bitmap.width >= bitmap.height) continue
                val upright = portrait(bitmap)
                file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                return upright
            }
            file.writeBytes(ByteArray(0))
        }
        return load(context, profile)?.let(::portrait)
    }

    /**
     * What SteamGridDB may list the known launchers and emulators as (several names each: the
     * emulator's own, a longer one, or a family member's), best first.
     */
    private val APP_NAMES: Map<String, List<String>> = run {
        val dolphin = listOf("Dolphin Emulator", "Dolphin")
        val gamehub = listOf("GameHub", "GameSir GameHub", "GameHub Lite")
        val citra = listOf("Azahar", "Citra", "Lime3DS")
        val eden = listOf("Eden", "Eden Emulator", "Yuzu", "Suyu")
        val ps2 = listOf("AetherSX2", "NetherSX2", "PCSX2")
        mapOf(
            "app.gamenative" to listOf("GameNative", "Winlator"),
            "gamehub.lite" to gamehub, "emuready.gamehub.lite" to gamehub, "com.xiaoji.egggame" to gamehub,
            "org.dolphinemu.dolphinemu" to dolphin, "org.dolphinemu.mmjr" to listOf("Dolphin MMJR") + dolphin,
            "org.ppsspp.ppsspp" to listOf("PPSSPP"), "org.ppsspp.ppssppgold" to listOf("PPSSPP Gold", "PPSSPP"),
            "org.azahar_emu.azahar" to citra, "io.github.lime3ds.android" to listOf("Lime3DS", "Citra"),
            "org.citra.citra_emu" to listOf("Citra"), "org.citra.emu" to listOf("Citra"),
            "dev.eden.eden_emulator" to eden, "dev.legacy.eden_emulator" to eden,
            "com.retroarch" to listOf("RetroArch"), "com.retroarch.aarch64" to listOf("RetroArch"),
            "org.es_de.frontend" to listOf("ES-DE", "EmulationStation Desktop Edition", "EmulationStation"),
            "xyz.aethersx2.android" to ps2, "xyz.nethersx2.android" to ps2,
            "com.github.stenzek.duckstation" to listOf("DuckStation"),
            "me.magnum.melonds" to listOf("melonDS"), "org.vita3k.emulator" to listOf("Vita3K"),
            "com.swordfish.lemuroid" to listOf("Lemuroid"), "org.mupen64plusae.v3.fzurita" to listOf("Mupen64Plus FZ", "Mupen64Plus"),
            "info.cemu.cemu" to listOf("Cemu"), "org.flycast.emulator" to listOf("Flycast"),
            "com.explusalpha.Snes9xPlus" to listOf("Snes9x EX+", "Snes9x"), "com.winlator" to listOf("Winlator"),
            "org.ryujinx.android" to listOf("Ryujinx")
        )
    }

    /** A name as compared: lower case, without "emulator", brackets or punctuation. */
    internal fun plainName(name: String): String = name.lowercase()
        .replace(Regex("\\(.*?\\)"), " ").replace(Regex("\\bemulator\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * SteamGridDB's entry for an app (a launcher or emulator) named any of [names]: only one
     * whose name is that name (give or take "Emulator" and punctuation), one calling itself an
     * emulator first, so an emulator never gets a game's art.
     */
    private fun sgdbApp(names: List<String>): Int? {
        if (key.isEmpty()) return null
        for (name in names) {
            val found = api("search/autocomplete/" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
                ?.optJSONArray("data") ?: continue
            val wanted = plainName(name)
            val matches = (0 until found.length()).mapNotNull { found.optJSONObject(it) }
                .filter { plainName(it.optString("name")) == wanted && it.optInt("id") > 0 }
            (matches.firstOrNull { it.optString("name").contains("emulator", ignoreCase = true) } ?: matches.firstOrNull())
                ?.let { return it.optInt("id") }
        }
        return null
    }

    /** The names an app is looked up by: its known names, else its own. */
    private fun appNames(profile: GameProfile, packageName: String?): List<String> =
        ((packageName?.let { APP_NAMES[it] } ?: profile.packages.firstNotNullOfOrNull { APP_NAMES[it] }).orEmpty() + profile.name)
            .filter { it.isNotBlank() }.distinctBy { plainName(it) }

    /** SteamGridDB's upright grids for an entry, best first. */
    private fun sgdbUpright(id: Int): List<String> = api("grids/game/$id?dimensions=600x900")?.optJSONArray("data")?.let { data ->
        (0 until data.length()).mapNotNull { data.optJSONObject(it)?.let { item -> item.optString("url").ifEmpty { item.optString("thumb") } } }
    }.orEmpty().filter { it.startsWith("https://") }

    /** Art with a plain white frame (some covers have one): looks wrong inside a rounded tile. */
    internal fun framed(art: Bitmap): Boolean {
        val w = art.width; val h = art.height
        if (w < 40 || h < 40) return false
        fun light(x: Int, y: Int): Boolean {
            val c = art.getPixel(x, y)
            return android.graphics.Color.red(c) > 230 && android.graphics.Color.green(c) > 230 && android.graphics.Color.blue(c) > 230
        }
        val inset = 3
        val edges = listOf(w / 4, w / 2, 3 * w / 4).flatMap { x -> listOf(light(x, inset), light(x, h - 1 - inset)) } +
            listOf(h / 4, h / 2, 3 * h / 4).flatMap { y -> listOf(light(inset, y), light(w - 1 - inset, y)) }
        return edges.count { it } >= edges.size - 1 && !light(w / 2, h / 2)
    }

    /** The first of [urls] that decodes and isn't framed in white, upright. */
    private fun firstUnframed(urls: List<String>): Bitmap? {
        for (url in urls.take(8)) {
            val art = fetch(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: continue
            if (!framed(art)) return portrait(art)
        }
        return null
    }

    /**
     * A launcher's or emulator's tile, upright like the games': art picked for it, else
     * SteamGridDB's for the app (fetched once), else its icon over a dimmed fill of itself.
     */
    fun appPortrait(context: Context, profile: GameProfile, packageName: String): Bitmap? {
        uprightFile(context, profile).takeIf { it.length() > 0 }?.let { file -> BitmapFactory.decodeFile(file.path)?.let { return it } }
        custom(context, profile)?.let { return portrait(it) }
        val file = File(dir(context), "app2-" + packageName + (if (key.isEmpty()) "" else "-sgdb") + ".img")
        if (file.exists() && file.length() > 0) BitmapFactory.decodeFile(file.path)?.let { return it }
        if (!file.exists() && !SettingsManager.isOfflineMode(context)) {
            dir(context).mkdirs()
            val upright = sgdbApp(appNames(profile, packageName))?.let { firstUnframed(sgdbUpright(it)) }
            if (upright != null) {
                file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                return upright
            }
            file.writeBytes(ByteArray(0))
        }
        return runCatching {
            val icon = context.packageManager.getApplicationIcon(packageName)
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
            portrait(bitmap)
        }.getOrNull()
    }

    /** Xbox Cloud Gaming's tile in the Games app, for art picked for it. */
    val XBOX_TILE: GameProfile get() = GameProfiles.newProfile("Xbox Game Pass", emptySet()).copy(id = "xbox-cloud-tile")

    /**
     * Xbox Cloud Gaming's tile: art picked for it, else Game Pass's upright art from SteamGridDB
     * (the first without a white frame; fetched once, blocking).
     */
    fun xboxTile(context: Context): Bitmap? {
        val profile = XBOX_TILE
        uprightFile(context, profile).takeIf { it.length() > 0 }?.let { file -> BitmapFactory.decodeFile(file.path)?.let { return it } }
        custom(context, profile)?.let { return portrait(it) }
        val file = File(dir(context), "xbox-cloud-tile-2" + (if (key.isEmpty()) "" else "-sgdb") + ".img")
        if (file.exists()) return if (file.length() > 0) BitmapFactory.decodeFile(file.path) else null
        if (SettingsManager.isOfflineMode(context)) return null
        dir(context).mkdirs()
        val upright = sgdbApp(listOf("Xbox Game Pass", "Xbox Cloud Gaming"))?.let { firstUnframed(sgdbUpright(it)) }
        if (upright != null) {
            file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return upright
        }
        file.writeBytes(ByteArray(0))
        return null
    }

    /** [art] at 600×900: cropped when it's nearly that shape, else fitted over a dimmed fill of itself. */
    internal fun portrait(art: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(PORTRAIT_W, PORTRAIT_H, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        val ratio = art.width.toFloat() / art.height
        val target = PORTRAIT_W.toFloat() / PORTRAIT_H
        fun fill() {
            val scale = maxOf(PORTRAIT_W.toFloat() / art.width, PORTRAIT_H.toFloat() / art.height)
            val w = art.width * scale
            val h = art.height * scale
            canvas.drawBitmap(art, null, android.graphics.RectF((PORTRAIT_W - w) / 2, (PORTRAIT_H - h) / 2, (PORTRAIT_W + w) / 2, (PORTRAIT_H + h) / 2), paint)
        }
        if (kotlin.math.abs(ratio - target) / target < 0.15f) {
            fill()
            return out
        }
        fill()
        canvas.drawColor(0xB0000000.toInt())
        val scale = minOf(PORTRAIT_W.toFloat() / art.width, PORTRAIT_H.toFloat() / art.height)
        val w = art.width * scale
        val h = art.height * scale
        canvas.drawBitmap(art, null, android.graphics.RectF((PORTRAIT_W - w) / 2, (PORTRAIT_H - h) / 2, (PORTRAIT_W + w) / 2, (PORTRAIT_H + h) / 2), paint)
        return out
    }

    /** Forgets [profile]'s art, so it's looked for again (art the user picked stays). */
    fun forget(context: Context, profile: GameProfile) {
        for (name in listOf(".img", "-sgdb.img", "-portrait.img", "-portrait-sgdb.img")) File(dir(context), profile.id + name).delete()
    }

    // ---- Art the user picked ----

    private fun customFile(context: Context, profile: GameProfile) = File(dir(context), profile.id + "-custom.img")

    private fun custom(context: Context, profile: GameProfile): Bitmap? =
        customFile(context, profile).takeIf { it.length() > 0 }?.let { BitmapFactory.decodeFile(it.path) }

    /** Whether [profile] has art the user picked. */
    fun hasCustom(context: Context, profile: GameProfile): Boolean = customFile(context, profile).length() > 0

    /** Changes when [profile]'s art does, for redrawing it. */
    fun version(context: Context, profile: GameProfile): Long =
        maxOf(customFile(context, profile).lastModified(), uprightFile(context, profile).lastModified())

    /** Upright art picked for Moonlight, apart from the list's. */
    private fun uprightFile(context: Context, profile: GameProfile) = File(dir(context), profile.id + "-upright-custom.img")

    // ---- Every art found, to pick from ----

    /** One art found: a small copy to show, and the full one to keep. */
    data class Choice(val thumb: String, val full: String)

    /**
     * Every art found for [profile] (blocking): icon-shaped ones for its list entry and shortcut,
     * and upright ones for Moonlight. SteamGridDB's many, then Steam's, Xbox's, GameTDB's and
     * libretro's.
     */
    fun choices(context: Context, profile: GameProfile, search: String? = null): Pair<List<Choice>, List<Choice>> {
        val query = search?.trim().orEmpty()
        val icons = mutableListOf<Choice>()
        val upright = mutableListOf<Choice>()
        if (key.isNotEmpty()) {
            val launch = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            // A launcher or emulator (or the Xbox tile): its own entry, not a game of its name
            // Or whatever title was searched for, its closest match
            val entry = when {
                query.isNotEmpty() -> api("search/autocomplete/" + java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
                    ?.optJSONArray("data")?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
                profile.isGame -> sgdbGame(launch, profile)
                else -> sgdbApp(appNames(profile, null))
            }
            entry?.let { game ->
                fun all(path: String) = api(path)?.optJSONArray("data")?.let { data ->
                    List(data.length()) { data.optJSONObject(it) }.mapNotNull { item ->
                        val full = item?.optString("url").orEmpty()
                        if (!full.startsWith("https://") || full.endsWith(".ico")) null
                        else Choice(item.optString("thumb").ifEmpty { full }, full)
                    }
                }.orEmpty()
                icons += all("icons/game/$game")
                icons += all("grids/game/$game?dimensions=512x512,1024x1024")
                upright += all("grids/game/$game?dimensions=600x900")
            }
        }
        if (query.isEmpty()) for (url in urls(context, profile)) {
            // Steam's header is wide (fine as an icon, cropped); the rest are covers
            if (url.endsWith("header.jpg")) icons += Choice(url, url) else upright += Choice(url, url)
        }
        return icons.distinctBy { it.full }.take(40) to upright.distinctBy { it.full }.take(40)
    }

    private val thumbnails = android.util.LruCache<String, Bitmap>(80)

    /** A small copy of the art at [url], to show (blocking); null when it can't be had. */
    fun thumbnail(url: String): Bitmap? {
        thumbnails.get(url)?.let { return it }
        val bytes = fetch(url) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 320) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { thumbnails.put(url, it) }
    }

    /** Keeps the art at [url] as [profile]'s icon or, [uprightArt], its upright art for Moonlight. */
    fun choose(context: Context, profile: GameProfile, url: String, uprightArt: Boolean): Boolean = runCatching {
        val bytes = fetch(url) ?: return false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        dir(context).mkdirs()
        if (uprightArt) uprightFile(context, profile).outputStream().use { portrait(bitmap).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        else customFile(context, profile).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    /** Keeps the image at [uri] as [profile]'s art, at most 1200 px; false when it can't be read. */
    fun setCustom(context: Context, profile: GameProfile, uri: android.net.Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        dir(context).mkdirs()
        customFile(context, profile).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    /** Goes back to the art found for [profile]. */
    fun clearCustom(context: Context, profile: GameProfile) {
        customFile(context, profile).delete()
        uprightFile(context, profile).delete()
    }

    /** Where [profile]'s game art may be, best first. */
    internal fun urls(context: Context, profile: GameProfile): List<String> {
        val launch = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: return emptyList()
        // An Xbox cloud game: its box art from Microsoft's catalogue
        profile.launch?.let { XboxCloud.artFor(context, it) }?.let { return listOf(it) }
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
        val game = sgdbGame(launch, profile) ?: return emptyList()
        fun first(path: String) = api(path)?.optJSONArray("data")?.optJSONObject(0)?.let { it.optString("thumb").ifEmpty { it.optString("url") } }
        return listOfNotNull(
            first("icons/game/$game"),
            first("grids/game/$game?dimensions=512x512,1024x1024"),
            first("grids/game/$game")
        ).filter { it.startsWith("https://") && !it.endsWith(".ico") }
    }

    /** SteamGridDB's upright (600×900) grid art for [profile]'s game. */
    internal fun steamGridDbPortrait(profile: GameProfile): List<String> {
        if (key.isEmpty()) return emptyList()
        val launch = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val game = sgdbGame(launch, profile) ?: return emptyList()
        return listOfNotNull(
            api("grids/game/$game?dimensions=600x900")?.optJSONArray("data")?.optJSONObject(0)?.let { it.optString("url").ifEmpty { it.optString("thumb") } }
        ).filter { it.startsWith("https://") }
    }

    private fun sgdbGame(launch: Intent?, profile: GameProfile): Int? =
        (launch?.let { steamId(it) }?.let { id -> api("games/steam/$id")?.optJSONObject("data")?.optInt("id") }
            ?: api("search/autocomplete/" + java.net.URLEncoder.encode(profile.name, "UTF-8").replace("+", "%20"))
                ?.optJSONArray("data")?.optJSONObject(0)?.optInt("id"))?.takeIf { it > 0 }

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

    private const val PORTRAIT_W = 600
    private const val PORTRAIT_H = 900

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
