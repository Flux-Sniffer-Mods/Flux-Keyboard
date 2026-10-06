package it.palsoftware.pastiera

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

// SettingsManager: typing sounds and haptic feedback. The keys and defaults live in SettingsManager.kt.

fun SettingsManager.getTypingSoundMode(context: Context): String {
    val mode = getPreferences(context).getString(KEY_TYPING_SOUND_MODE, DEFAULT_TYPING_SOUND_MODE)
    return when (mode) {
        TYPING_SOUND_MODE_CLICK,
        TYPING_SOUND_MODE_TYPEWRITER,
        TYPING_SOUND_MODE_CUSTOM -> mode
        else -> TYPING_SOUND_MODE_OFF
    }
}

fun SettingsManager.setTypingSoundMode(context: Context, mode: String) {
    val normalized = when (mode) {
        TYPING_SOUND_MODE_CLICK,
        TYPING_SOUND_MODE_TYPEWRITER,
        TYPING_SOUND_MODE_CUSTOM -> mode
        else -> TYPING_SOUND_MODE_OFF
    }
    getPreferences(context).edit()
        .putString(KEY_TYPING_SOUND_MODE, normalized)
        .apply()
}

fun SettingsManager.getTypingSoundOutputMode(context: Context): String {
    val mode = getPreferences(context).getString(KEY_TYPING_SOUND_OUTPUT_MODE, DEFAULT_TYPING_SOUND_OUTPUT_MODE)
    return when (mode) {
        TYPING_SOUND_OUTPUT_SYSTEM,
        TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
        else -> TYPING_SOUND_OUTPUT_MEDIA
    }
}

fun SettingsManager.setTypingSoundOutputMode(context: Context, mode: String) {
    val normalized = when (mode) {
        TYPING_SOUND_OUTPUT_SYSTEM,
        TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
        else -> TYPING_SOUND_OUTPUT_MEDIA
    }
    getPreferences(context).edit()
        .putString(KEY_TYPING_SOUND_OUTPUT_MODE, normalized)
        .apply()
}

fun SettingsManager.getTapHapticUseSystem(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TAP_HAPTIC_USE_SYSTEM, DEFAULT_TAP_HAPTIC_USE_SYSTEM)

fun SettingsManager.setTapHapticUseSystem(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TAP_HAPTIC_USE_SYSTEM, enabled)
        .apply()
}

fun SettingsManager.getTapHapticDurationMs(context: Context): Long =
    getPreferences(context)
        .getLong(KEY_TAP_HAPTIC_DURATION_MS, DEFAULT_TAP_HAPTIC_DURATION_MS)
        .coerceIn(MIN_TAP_HAPTIC_DURATION_MS, MAX_TAP_HAPTIC_DURATION_MS)

fun SettingsManager.setTapHapticDurationMs(context: Context, durationMs: Long) {
    getPreferences(context).edit()
        .putLong(
            KEY_TAP_HAPTIC_DURATION_MS,
            durationMs.coerceIn(MIN_TAP_HAPTIC_DURATION_MS, MAX_TAP_HAPTIC_DURATION_MS)
        )
        .apply()
}

fun SettingsManager.getMinTapHapticDurationMs(): Long = MIN_TAP_HAPTIC_DURATION_MS

fun SettingsManager.getMaxTapHapticDurationMs(): Long = MAX_TAP_HAPTIC_DURATION_MS

fun SettingsManager.getTypingSoundCustomDisplayName(context: Context): String? {
    return getPreferences(context)
        .getString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, null)
        ?.takeIf { it.isNotBlank() }
}

fun SettingsManager.getTypingSoundCustomFile(context: Context): File? {
    val fileName = getPreferences(context)
        .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), fileName).takeIf { it.isFile }
}

fun SettingsManager.getTypingSoundCustomGroupFiles(context: Context): Map<String, List<File>> {
    val storedName = getPreferences(context)
        .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
        ?.takeIf { it.isNotBlank() }
        ?: return emptyMap()
    val root = File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), storedName)
    if (!root.isDirectory) {
        getTypingSoundCustomFile(context)?.let { file ->
            return mapOf("normal" to listOf(file))
        }
        return emptyMap()
    }

    return TYPING_SOUND_GROUPS.associateWith { group ->
        File(root, group)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase() in TYPING_SOUND_AUDIO_EXTENSIONS }
            .sortedBy { it.name }
    }.filterValues { it.isNotEmpty() }
}

fun SettingsManager.importTypingSoundPack(context: Context, uri: Uri): Boolean {
    val displayName = queryDisplayName(context, uri) ?: return false
    val extension = displayName.substringAfterLast('.', "").lowercase()
    if (extension != "zip") {
        return false
    }

    val targetDir = File(context.filesDir, TYPING_SOUND_CUSTOM_DIR).apply { mkdirs() }
    val stagingDir = File(targetDir, "${TYPING_SOUND_CUSTOM_PACK_DIR}_staging").apply {
        deleteRecursively()
        mkdirs()
    }
    val finalDir = File(targetDir, TYPING_SOUND_CUSTOM_PACK_DIR)

    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            extractTypingSoundPack(input, stagingDir)
        } ?: return false

        if (File(stagingDir, "normal").listFiles().orEmpty().none { it.isFile }) {
            stagingDir.deleteRecursively()
            return false
        }

        finalDir.deleteRecursively()
        if (!stagingDir.renameTo(finalDir)) {
            stagingDir.copyRecursively(finalDir, overwrite = true)
            stagingDir.deleteRecursively()
        }

        getPreferences(context).edit()
            .putString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, finalDir.name)
            .putString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, displayName)
            .putString(KEY_TYPING_SOUND_MODE, TYPING_SOUND_MODE_CUSTOM)
            .putLong(KEY_TYPING_SOUND_UPDATED_AT, System.currentTimeMillis())
            .apply()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error importing typing sound", e)
        stagingDir.deleteRecursively()
        false
    }
}

fun SettingsManager.importTypingSound(context: Context, uri: Uri): Boolean = importTypingSoundPack(context, uri)

private fun SettingsManager.queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

private fun SettingsManager.extractTypingSoundPack(input: InputStream, targetDir: File) {
    var fileCount = 0
    var totalBytes = 0L
    ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        val canonicalTargetRoot = targetDir.canonicalFile

        while (entry != null) {
            val entryName = entry.name.removePrefix("./")
            if (!entry.isDirectory && !entryName.startsWith("__MACOSX/")) {
                val group = resolveTypingSoundGroup(entryName)
                val extension = entryName.substringAfterLast('.', "").lowercase()
                if (group != null && extension in TYPING_SOUND_AUDIO_EXTENSIONS) {
                    fileCount += 1
                    if (fileCount > TYPING_SOUND_MAX_PACK_FILES) {
                        throw IllegalArgumentException("Typing sound pack has too many files")
                    }

                    val outFile = File(File(targetDir, group).apply { mkdirs() }, "${fileCount.toString().padStart(3, '0')}.$extension")
                    val canonicalOutFile = outFile.canonicalFile
                    if (!canonicalOutFile.path.startsWith(canonicalTargetRoot.path)) {
                        throw IllegalStateException("Refusing to unzip entry outside target dir: $entryName")
                    }

                    canonicalOutFile.outputStream().use { output ->
                        totalBytes += copyWithLimit(zip, output, TYPING_SOUND_MAX_FILE_BYTES, TYPING_SOUND_MAX_PACK_BYTES - totalBytes)
                    }
                }
            }

            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}

private fun SettingsManager.resolveTypingSoundGroup(entryName: String): String? {
    val parts = entryName.split('/').filter { it.isNotBlank() }
    return parts.firstOrNull { it.lowercase() in TYPING_SOUND_GROUPS }?.lowercase()
}

private fun SettingsManager.copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long, remainingPackBytes: Long = Long.MAX_VALUE): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        copied += read
        if (copied > maxBytes || copied > remainingPackBytes) {
            throw IllegalArgumentException("Typing sound import exceeds size limits")
        }
        output.write(buffer, 0, read)
    }
    return copied
}
