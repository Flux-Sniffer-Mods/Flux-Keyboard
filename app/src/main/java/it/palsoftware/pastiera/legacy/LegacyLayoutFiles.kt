package it.palsoftware.pastiera.legacy

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Custom layouts saved before layouts were stored under hashed names: a file named after the
 * layout itself. They're found by that name and moved to safe storage the first time they're used.
 */
internal object LegacyLayoutFiles {
    private const val TAG = "LegacyLayoutFiles"

    fun find(context: Context, layoutName: String): File? {
        val root = LayoutFileStore.getLayoutsDirectory(context).canonicalFile
        return root.listFiles { file ->
            file.isFile && file.name.endsWith(".json") && !LayoutFileStore.isSafeStorageFile(file)
        }?.firstOrNull { file ->
            file.name.removeSuffix(".json") == layoutName &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        }
    }

    fun safeFileFor(context: Context, layoutName: String): File {
        LayoutFileStore.findSafeLayoutFileByExactId(context, layoutName)?.let { return it }

        val primary = LayoutFileStore.safeLayoutFile(context, layoutName)
        if (!primary.exists()) return primary

        var collisionIndex = 0
        while (true) {
            val collisionStorageId = LayoutFileStore.storageIdForOpaqueValue(
                "legacy-layout-collision:$collisionIndex:$layoutName"
            )
            val candidate = LayoutFileStore.safeStorageFile(context, collisionStorageId)
            if (!candidate.exists() || LayoutFileStore.storedLogicalLayoutId(candidate) == layoutName) {
                return candidate
            }
            collisionIndex += 1
        }
    }

    fun migrate(
        context: Context,
        layoutName: String,
        legacyFile: File
    ): File {
        return try {
            val root = LayoutFileStore.getLayoutsDirectory(context).canonicalFile
            val canonicalLegacy = legacyFile.canonicalFile
            val safeFile = safeFileFor(context, layoutName)
            if (canonicalLegacy.parentFile != root || safeFile.parentFile != root) return legacyFile
            if (safeFile.exists()) return safeFile
            val jsonObject = JSONObject(legacyFile.readText()).apply {
                put(LayoutFileStore.LAYOUT_ID_FIELD, layoutName)
                put(LayoutFileStore.STORAGE_ID_FIELD, safeFile.nameWithoutExtension)
            }
            LayoutFileStore.writeAtomically(safeFile, jsonObject.toString(2).toByteArray(StandardCharsets.UTF_8))
            if (!legacyFile.delete()) {
                Log.w(TAG, "Migrated legacy layout but could not delete old file: ${legacyFile.name}")
            }
            Log.i(TAG, "Migrated legacy layout to safe storage: $layoutName")
            safeFile
        } catch (e: Exception) {
            Log.e(TAG, "Could not migrate legacy layout without data loss: $layoutName", e)
            legacyFile
        }
    }
}
