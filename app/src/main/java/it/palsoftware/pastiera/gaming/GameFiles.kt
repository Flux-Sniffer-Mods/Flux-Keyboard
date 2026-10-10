package it.palsoftware.pastiera.gaming

import android.content.Intent
import it.palsoftware.pastiera.adb.AdbShell

/** A game's file: where a profile's shortcut points on the phone, and whether a Dolphin game is a Wii one. */
object GameFiles {
    /** The game file a profile's shortcut starts, as a path on the phone. */
    internal fun gameFile(launch: String): String? {
        val uri = runCatching { Intent.parseUri(launch, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?.let { it.getStringExtra("AutoStartFile") ?: it.dataString } ?: return null
        return pathOf(uri)
    }

    /** A storage document's path (".../document/primary%3AGames%2Fx.rvz" -> /storage/emulated/0/Games/x.rvz). */
    internal fun pathOf(uri: String): String? {
        if (uri.startsWith("/")) return uri
        if (uri.startsWith("file://")) return java.net.URLDecoder.decode(uri.removePrefix("file://"), "UTF-8")
        val id = java.net.URLDecoder.decode(uri.substringAfterLast("/document/", "").ifEmpty { return null }, "UTF-8")
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        return (if (volume == "primary") "/storage/emulated/0" else "/storage/$volume") + "/" + relative
    }

    /** A Wii game: by its kind of file, the folder it's kept in, or its disc's own mark. */
    fun isWii(path: String): Boolean {
        byName(path)?.let { return it }
        val head = AdbShell.run("od -A n -t x1 -N 96 \"${path.replace("\"", "")}\"; true", 3_000) ?: return false
        return byHeader(head.split(Regex("\\s+")).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }) ?: false
    }

    /** What the file's kind or folder says, if it says. */
    internal fun byName(path: String): Boolean? {
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension == "wbfs" || extension == "wad") return true
        if (extension == "gcm" || extension == "tgc") return false
        val folders = path.lowercase().split('/').dropLast(1)
        if ("wii" in folders) return true
        if ("gc" in folders || "gamecube" in folders || "ngc" in folders) return false
        return null
    }

    /**
     * What the start of the file says: a disc image's own Wii or GameCube mark, or the disc type
     * an RVZ or WIA file records. Null when it's neither (an executable, an unknown kind).
     */
    internal fun byHeader(bytes: List<Int>): Boolean? {
        fun word(at: Int) = if (bytes.size < at + 4) -1L else
            (bytes[at].toLong() shl 24) or (bytes[at + 1].toLong() shl 16) or (bytes[at + 2].toLong() shl 8) or bytes[at + 3].toLong()
        if (word(0x18) == 0x5D1C9EA3L) return true
        if (word(0x1C) == 0xC2339F3DL) return false
        // RVZ and WIA: "RVZ\u0001" or "WIA\u0001", then their second header's disc type (1 GameCube, 2 Wii)
        val magic = bytes.take(3).map { it.toChar() }.joinToString("")
        if ((magic == "RVZ" || magic == "WIA") && bytes.getOrNull(3) == 1) {
            return when (word(0x48)) { 2L -> true; 1L -> false; else -> null }
        }
        return null
    }
}
