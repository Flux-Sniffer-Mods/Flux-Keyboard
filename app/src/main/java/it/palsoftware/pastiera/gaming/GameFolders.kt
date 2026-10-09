package it.palsoftware.pastiera.gaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Games from folders: Flux Keyboard's own (Download/Flux Keyboard/Games, a folder per launcher
 * and emulator) and ES-DE's ROMs folder. GameNative's (and GameHub Lite's) Export for frontend
 * writes a small file per game there, holding its ID; emulators' folders hold the games
 * themselves. Each is started the way ES-DE starts it, straight into the game.
 */
object GameFolders {
    const val ROOT = "/storage/emulated/0/Download/Flux Keyboard/Games"
    private const val ES_DE_ROMS = "/storage/emulated/0/ROMs"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** Who starts a folder's games: the apps that may be installed, each with its activity. */
    internal enum class Player(
        val label: String,
        /** Flux Keyboard's folder, then ES-DE's system folders that hold its games. */
        val folders: List<String>,
        val extensions: Set<String>,
        val activities: List<Pair<String, String>>
    ) {
        GAMENATIVE("GameNative", listOf("GameNative", "steam", "epic", "gog", "amazon", "windows"),
            setOf("steam", "epic", "gog", "amazon", "pcgame"),
            listOf("app.gamenative" to "app.gamenative.MainActivity")),
        GAMEHUB("GameHub", listOf("GameHub"),
            setOf("steam", "local"),
            listOf("gamehub.lite", "emuready.gamehub.lite", "com.xiaoji.egggame").map { it to "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity" }),
        DOLPHIN("Dolphin", listOf("Dolphin", "gc", "wii"),
            setOf("iso", "gcm", "ciso", "rvz", "wbfs", "wia", "tgc", "gcz", "dol", "elf", "wad"),
            listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr").map { it to "org.dolphinemu.dolphinemu.ui.main.TvMainActivity" }),
        PPSSPP("PPSSPP", listOf("PPSSPP", "psp"),
            setOf("iso", "cso", "chd", "pbp", "elf", "prx"),
            listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp").map { it to "org.ppsspp.ppsspp.PpssppActivity" }),
        AZAHAR("Azahar", listOf("Azahar", "n3ds", "3ds"),
            setOf("3ds", "3dsx", "cci", "cia", "cxi", "app", "elf", "axf"),
            listOf("org.azahar_emu.azahar", "io.github.lime3ds.android", "org.citra.citra_emu").map { it to "org.citra.citra_emu.activities.EmulationActivity" }),
        EDEN("Eden", listOf("Eden", "switch"),
            setOf("nsp", "xci", "nca", "nro", "nso"),
            listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator").map { it to "org.yuzu.yuzu_emu.activities.EmulationActivity" });

        /** The installed app that plays these, if any. */
        fun installed(context: Context): Pair<String, String>? = activities.firstOrNull { (pkg, _) ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /** A folder of the user's own (the one set in a launcher or emulator), and who plays it (null: by its names). */
    data class Custom(val path: String, val player: String? = null)

    private const val KEY_CUSTOM = "game_folders_custom"

    fun custom(context: Context): List<Custom> = runCatching {
        val array = org.json.JSONArray(it.palsoftware.pastiera.SettingsManager.getPreferences(context).getString(KEY_CUSTOM, "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            Custom(item.getString("path"), item.optString("player").ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    fun setCustom(context: Context, folders: List<Custom>) {
        val array = org.json.JSONArray()
        folders.forEach { folder -> array.put(org.json.JSONObject().put("path", folder.path).put("player", folder.player ?: "")) }
        it.palsoftware.pastiera.SettingsManager.getPreferences(context).edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    /** The path of a folder picked with Android's folder picker (on the phone or an SD card). */
    fun pathOf(tree: Uri): String? {
        if (tree.authority != EXTERNAL_STORAGE) return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "").trimEnd('/')
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (relative.isEmpty()) base else "$base/$relative"
    }

    /** The players a folder can be set to, by name. */
    val playerNames: List<String> get() = Player.entries.map { it.label }

    /** A game found in a folder: what's shown, which player, and its file (and what it holds). */
    internal data class Found(val name: String, val player: Player, val path: String, val content: String)

    /** Makes Flux Keyboard's folders, with a note on what goes where; false without the shell. */
    fun create(): Boolean {
        val dirs = Player.entries.joinToString(" ") { "\"$ROOT/${it.folders.first()}\"" }
        val note = "Flux Keyboard's game folders: gaming mode lists the games here and can put each on the home screen, " +
            "its profile on. GameNative and GameHub Lite: set Export for frontend (GameNative: Settings > Interface > " +
            "Frontend Sync) to their folder here. Emulators: keep your games in their folder and add the folder in the emulator too."
        return AdbShell.run("mkdir -p $dirs && printf '%s\\n' '${note.replace("'", "")}' > \"$ROOT/About these folders.txt\"", 5_000) != null
    }

    /** Every game in the folders whose player is installed (blocking: reads the shell). */
    fun games(context: Context): List<GameLibrary.Game> {
        val players = Player.entries.mapNotNull { player -> player.installed(context)?.let { player to it } }.toMap()
        if (players.isEmpty()) return emptyList()
        // Each file with its first line: an export file holds its game's ID
        // Flux Keyboard's and ES-DE's folders hold a folder per player; your own may hold the games themselves
        val custom = custom(context)
        val ownRoots = custom.joinToString(" ") { "\"${it.path.replace("\"", "")}\"" }
        val listing = AdbShell.run(
            "{ for d in \"$ROOT\" \"$ES_DE_ROMS\"; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 2 -maxdepth 3 -type f ! -name '.*' 2>/dev/null; done; " +
                (if (custom.isEmpty()) "" else "for d in $ownRoots; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -maxdepth 3 -type f ! -name '.*' 2>/dev/null; done; ") +
                "} | " +
                "while IFS= read -r f; do s=\$(head -c 64 \"\$f\" 2>/dev/null | head -n 1 | tr -cd '[:alnum:]_-'); printf '%s\\t%s\\n' \"\$f\" \"\$s\"; done; true",
            10_000
        ) ?: return emptyList()
        return parse(listing, custom).map { found ->
            // Steam games exported where GameNative's go, with only GameHub Lite to start them
            if (found.player == Player.GAMENATIVE && found.player !in players && Player.GAMEHUB in players &&
                found.path.endsWith(".steam", ignoreCase = true)
            ) found.copy(player = Player.GAMEHUB) else found
        }.filter { it.player in players }.mapNotNull { found ->
            val (pkg, activity) = players[found.player] ?: return@mapNotNull null
            GameLibrary.Game(found.name, pkg, launch = launchIntent(found, pkg, activity)?.toUri(Intent.URI_INTENT_SCHEME))
        }
    }

    /** The games in a listing of "path<tab>first line" lines. */
    internal fun parse(listing: String, custom: List<Custom> = emptyList()): List<Found> = listing.lines().mapNotNull { line ->
        val path = line.substringBefore('\t').trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val content = line.substringAfter('\t', "").trim()
        // The folder it's in, or one above it (games kept in folders of their own)
        val folders = path.split('/').dropLast(1).map { it.lowercase() }.reversed()
        val file = path.substringAfterLast('/')
        val extension = file.substringAfterLast('.', "").lowercase()
        // In a folder of your own set to a player: that player's games, whatever the folders are called
        val assigned = custom.filter { it.player != null && path.startsWith(it.path.trimEnd('/') + "/") }
            .maxByOrNull { it.path.length }?.let { folder -> Player.entries.firstOrNull { it.label == folder.player } }
        val player = assigned?.takeIf { extension in it.extensions } ?: folders.firstNotNullOfOrNull { folder ->
            Player.entries.firstOrNull { p -> p.folders.any { it.lowercase() == folder } && extension in p.extensions }
        } ?: return@mapNotNull null
        // An export file holds a game ID: without one there's nothing to start
        if ((player == Player.GAMENATIVE || player == Player.GAMEHUB) && content.isEmpty()) return@mapNotNull null
        Found(file.substringBeforeLast('.'), player, path, content)
    }.distinctBy { it.path }

    /** The game's storage document, as the emulator was given its folder: Android's own form. */
    private fun documentUri(path: String): Uri? {
        val (volume, relative) = when {
            path.startsWith("/storage/emulated/0/") -> "primary" to path.removePrefix("/storage/emulated/0/")
            path.startsWith("/storage/") -> path.removePrefix("/storage/").substringBefore('/') to
                path.removePrefix("/storage/").substringAfter('/')
            else -> return null
        }
        val tree = DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE, "$volume:${relative.substringBeforeLast('/')}")
        return DocumentsContract.buildDocumentUriUsingTree(tree, "$volume:$relative")
    }

    /** ES-DE's way of starting each (es_systems.xml), straight into the game. */
    private fun launchIntent(game: Found, pkg: String, activity: String): Intent? {
        val component = ComponentName(pkg, activity)
        val intent = when (game.player) {
            Player.GAMENATIVE -> {
                val id = game.content.toIntOrNull() ?: return null
                val source = when (game.path.substringAfterLast('.').lowercase()) {
                    "epic" -> "EPIC"; "gog" -> "GOG"; "amazon" -> "AMAZON"; "pcgame" -> "CUSTOM_GAME"; else -> "STEAM"
                }
                Intent("app.gamenative.LAUNCH_GAME").putExtra("app_id", id).putExtra("game_source", source)
            }
            Player.GAMEHUB -> Intent("gamehub.lite.LAUNCH_GAME").putExtra("autoStartGame", true).apply {
                if (game.path.endsWith(".local", ignoreCase = true)) putExtra("localGameId", game.content)
                else putExtra("steamAppId", game.content)
            }
            Player.DOLPHIN -> Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER")
                .putExtra("AutoStartFile", documentUri(game.path)?.toString() ?: return null)
            Player.PPSSPP -> Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_DEFAULT).setData(documentUri(game.path) ?: return null)
            Player.AZAHAR -> Intent().setData(documentUri(game.path) ?: return null)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            Player.EDEN -> Intent("android.nfc.action.TECH_DISCOVERED").setData(documentUri(game.path) ?: return null)
        }
        return intent.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
