package it.palsoftware.pastiera.gaming

import android.content.Context
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.isOfflineMode
import java.io.File

/**
 * A game's real name from the game itself, whatever its file is called: the ID on a Wii or
 * GameCube disc or a 3DS cartridge, looked up in GameTDB's list of titles (fetched once, kept),
 * else the name the game carries (a disc's own title, a PSP game's PARAM.SFO). Files are read
 * through the shell; what's found is remembered per file.
 */
object GameIds {
    data class Found(val id: String?, val title: String?)

    private const val PREFS = "game_titles"

    /** The name to show for [path], played in [player]; null to keep the file's name. */
    fun title(context: Context, player: String, path: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(path, null)?.let { return it.ifEmpty { null } }
        val file = path.substringAfterLast('/').substringBeforeLast('.')
        val read = when (player) {
            "Dolphin" -> disc(path)
            "Azahar" -> cartridge(path)
            "PPSSPP" -> psp(path)
            else -> null
        }
        // The game's own ID first, else one written in the file's name ("Zelda [GZLE01]"),
        // and the file's name without its tags ("[0100F2C0115B6000][v0]") as its title
        val named = fromName(player, file)
        val found = when {
            read?.id != null -> read
            named != null -> Found(named.id, read?.title ?: named.title)
            else -> read
        }
        val database = when (player) { "Dolphin" -> "wiitdb"; "Azahar" -> "3dstdb"; else -> null }
        found?.id?.let { prefs.edit().putString("id:$path", it).apply() }
        val name = found?.id?.let { id -> database?.let { lookup(context, it, id) } } ?: found?.title?.let { tidy(it) }
        // Not known yet (no list to look in): asked again next time
        if (name != null || found == null || database == null || listFile(context, database).exists()) {
            prefs.edit().putString(path, name ?: "").apply()
        }
        return name
    }

    /** The game ID read from [path] (a disc's GZLE01, a cartridge's AREE), if it has one. */
    fun id(context: Context, player: String, path: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("id:$path", null)?.let { return it }
        if (prefs.contains(path)) return null
        title(context, player, path)
        return prefs.getString("id:$path", null)
    }

    /**
     * An ID in a game file's name: a Wii or GameCube ID ("[GZLE01]"), a 3DS product code
     * ("CTR-P-AREE"), a PSP disc ID ("ULUS10041") or a Switch title ID ("0100F2C0115B6000"),
     * with the name left once its tags are gone. Null when the name holds none.
     */
    internal fun fromName(player: String, file: String): Found? {
        val id = when (player) {
            "Dolphin" -> Regex("[\\[(]([A-Z0-9]{6})[\\])]").findAll(file).map { it.groupValues[1] }.firstOrNull { it.any(Char::isDigit) && it.any(Char::isLetter) }
            "Azahar" -> Regex("CTR-[A-Z]-([A-Z0-9]{4})").find(file)?.groupValues?.get(1)
            "PPSSPP" -> Regex("\\b([A-Z]{4})-?(\\d{5})\\b").find(file)?.let { it.groupValues[1] + it.groupValues[2] }
            "Eden" -> Regex("\\b(01[0-9A-Fa-f]{14})\\b").find(file)?.groupValues?.get(1)?.uppercase()
            else -> null
        } ?: return null
        val title = file.replace(Regex("\\[[^\\]]*\\]"), " ").replace(id, " ").replace(Regex("[_]+"), " ")
            .replace(Regex("\\s+"), " ").trim(' ', '-').ifEmpty { null }
        return Found(id, title)
    }

    // ---- Reading the game ----

    /** [count] bytes of [path] from [offset], through the shell. */
    private fun bytes(path: String, offset: Long, count: Int): List<Int> {
        val out = AdbShell.run("od -A n -t x1 -j $offset -N $count \"${path.replace("\"", "")}\"; true", 4_000) ?: return emptyList()
        return out.split(Regex("\\s+")).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
    }

    private fun ascii(bytes: List<Int>): String = bytes.takeWhile { it != 0 }.map { it.toChar() }.joinToString("").trim()

    /** A Wii or GameCube disc's header: where it sits in each kind of file. */
    internal fun discHeaderOffset(path: String): Long? = when (path.substringAfterLast('.').lowercase()) {
        "iso", "gcm" -> 0L
        "rvz", "wia" -> 0x58L
        "wbfs" -> 0x200L
        "ciso" -> 0x8000L
        else -> null
    }

    /** A disc header's game ID (GZLE01) and title. */
    internal fun discFrom(header: List<Int>): Found? {
        if (header.size < 0x60) return null
        val id = ascii(header.subList(0, 6))
        if (!Regex("[A-Z0-9]{6}").matches(id)) return null
        return Found(id, ascii(header.subList(0x20, 0x60)).ifEmpty { null })
    }

    private fun disc(path: String): Found? {
        val offset = discHeaderOffset(path) ?: return null
        return discFrom(bytes(path, offset, 0x60))
    }

    /** A 3DS cartridge's product code (CTR-P-AREE -> AREE), from its first partition. */
    private fun cartridge(path: String): Found? {
        val extension = path.substringAfterLast('.').lowercase()
        val ncch = when (extension) {
            "3ds", "cci" -> {
                val table = bytes(path, 0x100, 0x28)
                if (ascii(table.take(4)) != "NCSD" || table.size < 0x28) return null
                le32(table, 0x20) * 0x200L
            }
            "cxi" -> 0L
            else -> return null
        }
        return cartridgeFrom(bytes(path, ncch + 0x100, 0x60))
    }

    /** An NCCH header (from its "NCCH" mark): the product code's last part. */
    internal fun cartridgeFrom(header: List<Int>): Found? {
        if (header.size < 0x60 || ascii(header.take(4)) != "NCCH") return null
        val code = ascii(header.subList(0x50, 0x60))
        val id = code.substringAfterLast('-').takeIf { Regex("[A-Z0-9]{4}").matches(it) } ?: return null
        return Found(id, null)
    }

    /** A PSP ISO's PARAM.SFO: its title and disc ID, found through the disc's folders. */
    private fun psp(path: String): Found? {
        if (!path.endsWith(".iso", ignoreCase = true)) return null
        val root = bytes(path, 0x8000 + 156, 34).takeIf { it.size == 34 } ?: return null
        val game = entry(path, le32(root, 2), le32(root, 10), "PSP_GAME") ?: return null
        val sfo = entry(path, game.first, game.second, "PARAM.SFO") ?: return null
        return sfoFrom(bytes(path, sfo.first * 2048L, minOf(sfo.second, 4096)))
    }

    /** A file or folder [name] in the ISO folder at sector [lba]: its sector and size. */
    private fun entry(path: String, lba: Int, size: Int, name: String): Pair<Int, Int>? {
        val dir = bytes(path, lba * 2048L, minOf(size, 8192))
        var at = 0
        while (at < dir.size) {
            val length = dir[at]
            if (length == 0) { at = (at / 2048 + 1) * 2048; continue }
            if (at + 33 > dir.size) break
            val nameLength = dir[at + 32]
            val entryName = dir.subList(at + 33, minOf(dir.size, at + 33 + nameLength)).map { it.toChar() }.joinToString("").substringBefore(';')
            if (entryName.equals(name, ignoreCase = true)) return le32(dir, at + 2) to le32(dir, at + 10)
            at += length
        }
        return null
    }

    /** A PARAM.SFO's TITLE and DISC_ID. */
    internal fun sfoFrom(sfo: List<Int>): Found? {
        if (sfo.size < 20 || sfo[1] != 'P'.code || sfo[2] != 'S'.code || sfo[3] != 'F'.code) return null
        val keys = le32(sfo, 8)
        val data = le32(sfo, 12)
        val count = le32(sfo, 16)
        val values = mutableMapOf<String, String>()
        for (i in 0 until count) {
            val at = 20 + i * 16
            if (at + 16 > sfo.size) break
            val keyAt = keys + (sfo[at] or (sfo[at + 1] shl 8))
            val length = le32(sfo, at + 4)
            val dataAt = data + le32(sfo, at + 12)
            if (keyAt >= sfo.size || dataAt + length > sfo.size) continue
            val key = ascii(sfo.subList(keyAt, minOf(sfo.size, keyAt + 32)))
            values[key] = String(sfo.subList(dataAt, dataAt + length).takeWhile { it != 0 }.map { it.toByte() }.toByteArray(), Charsets.UTF_8).trim()
        }
        val title = values["TITLE"]?.ifEmpty { null } ?: return null
        return Found(values["DISC_ID"], title)
    }

    private fun le32(bytes: List<Int>, at: Int): Int =
        if (bytes.size < at + 4) 0 else bytes[at] or (bytes[at + 1] shl 8) or (bytes[at + 2] shl 16) or (bytes[at + 3] shl 24)

    /** A disc's own title, readable: "THE LEGEND OF ZELDA" -> "The Legend Of Zelda". */
    internal fun tidy(title: String): String {
        val clean = title.replace(Regex("\\s+"), " ").trim()
        if (clean.any { it.isLowerCase() }) return clean
        return clean.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    // ---- GameTDB's titles ----

    private val lists = mutableMapOf<String, Map<String, String>>()

    private fun listFile(context: Context, database: String) = File(context.filesDir, "gametdb/$database.txt")

    /** [id]'s title in GameTDB's [database] (wiitdb: Wii and GameCube; 3dstdb: 3DS). */
    @Synchronized
    fun lookup(context: Context, database: String, id: String): String? {
        val titles = lists.getOrPut(database) { load(context, database) }
        return titles[id] ?: titles[id.take(4)]
    }

    private fun load(context: Context, database: String): Map<String, String> {
        val file = listFile(context, database)
        if (!file.exists()) fetch(context, database, file)
        return if (file.exists()) parse(file.readText()) else emptyMap()
    }

    /** "GZLE01 = The Legend of Zelda: The Wind Waker" lines; the first line names the list. */
    internal fun parse(text: String): Map<String, String> = text.lineSequence().drop(1).mapNotNull { line ->
        val id = line.substringBefore(" = ", "").trim()
        val title = line.substringAfter(" = ", "").trim()
        if (id.isEmpty() || title.isEmpty()) null else id to title
    }.toMap()

    private fun fetch(context: Context, database: String, file: File) {
        if (SettingsManager.isOfflineMode(context)) return
        runCatching {
            val connection = java.net.URL("https://www.gametdb.com/$database.txt?LANG=EN").openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Flux Keyboard")
            connection.inputStream.use { input ->
                file.parentFile?.mkdirs()
                val partial = File(file.path + ".part")
                partial.outputStream().use { input.copyTo(it) }
                if (partial.length() > 1000) partial.renameTo(file) else partial.delete()
            }
            connection.disconnect()
        }
    }
}
