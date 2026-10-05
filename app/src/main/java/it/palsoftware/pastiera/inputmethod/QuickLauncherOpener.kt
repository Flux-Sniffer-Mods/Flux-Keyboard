package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

object QuickLauncherOpener {
    private const val TAG = "QuickLauncherOpener"
    private const val NIAGARA_PACKAGE = "bitpit.launcher"
    private val NIAGARA_SEARCH_URI: Uri = Uri.parse("niagara://search")

    /** [fromPackage]: the app it's opened from, which Back from Niagara's search returns to. */
    fun open(context: Context, fromPackage: String? = null): Boolean {
        // Already in Niagara: re-opening its search there can show it half drawn, so the built-in one opens
        val inNiagara = (fromPackage ?: foregroundPackage) == NIAGARA_PACKAGE
        return when (SettingsManager.getQuickLauncherBehavior(context)) {
            SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA ->
                if (inNiagara) openPastieraQuickLauncher(context) else
                openNiagaraSearch(context).also { opened ->
                    if (opened && SettingsManager.getNiagaraBackReturns(context)) NiagaraReturn.arm(fromPackage ?: foregroundPackage)
                } || openPastieraQuickLauncher(context)
            else -> openPastieraQuickLauncher(context)
        }
    }

    /**
     * Niagara's search is part of the home screen, so Back there lands on Niagara, not the app
     * it was opened from. The keyboard sees Niagara's search field come and go: when it closes
     * without another app opening, the app is brought back.
     */
    /** The app the keyboard last started input in, kept by the keyboard for [NiagaraReturn]. */
    @Volatile var foregroundPackage: String? = null

    object NiagaraReturn {
        private const val TIMEOUT_MS = 5 * 60_000L
        @Volatile private var fromPackage: String? = null
        @Volatile private var openedAt = 0L
        @Volatile private var searchSeen = false
        // Niagara's search field closed: back to the app unless another app starts meanwhile
        @Volatile private var closedAt = 0L

        fun arm(packageName: String?) {
            fromPackage = packageName?.takeIf { it != NIAGARA_PACKAGE }
            openedAt = System.currentTimeMillis()
            searchSeen = false
        }

        private fun clear() {
            fromPackage = null
            searchSeen = false
            closedAt = 0L
        }

        /** How long to wait after Niagara's search closes for an app to start instead. */
        const val DECIDE_AFTER_MS = 600L

        /**
         * Niagara's search field closed. It may close without a new input start (Niagara hides
         * the keyboard), so the keyboard checks back after [DECIDE_AFTER_MS] with [decide].
         */
        fun onInputFinished(packageName: String?, now: Long = System.currentTimeMillis()): Boolean {
            if (fromPackage == null || packageName != NIAGARA_PACKAGE || !searchSeen) return false
            closedAt = now
            return true
        }

        /**
         * Back (the key or the navigation gesture) while Niagara's search has the keyboard: the
         * app to go back to straight away.
         */
        fun onBackPressed(packageName: String?, now: Long = System.currentTimeMillis()): String? {
            val from = fromPackage ?: return null
            if (packageName != NIAGARA_PACKAGE || now - openedAt > TIMEOUT_MS) return null
            clear()
            return from
        }

        /** After the search closed: the app to go back to, if nothing else started since. */
        fun decide(now: Long = System.currentTimeMillis()): String? {
            val from = fromPackage ?: return null
            if (closedAt == 0L || now - closedAt < DECIDE_AFTER_MS - 50) return null
            clear()
            return from
        }

        /** Each input start: the app to go back to, when Niagara's search just closed. */
        fun onInputStarted(packageName: String?, editable: Boolean, now: Long = System.currentTimeMillis()): String? {
            val from = fromPackage ?: return null
            if (now - openedAt > TIMEOUT_MS) {
                clear()
                return null
            }
            return when {
                packageName == NIAGARA_PACKAGE && editable -> { searchSeen = true; closedAt = 0L; null }
                packageName == NIAGARA_PACKAGE -> if (searchSeen) { clear(); from } else null
                // The app it was opened from, still closing: not a choice yet
                packageName == from && !searchSeen -> null
                else -> { clear(); null }
            }
        }
    }

    private fun openPastieraQuickLauncher(context: Context): Boolean {
        return try {
            val intent = QuickLauncherActivity.createToggleIntent(context)
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Error opening Pastiera QuickLauncher", error)
            false
        }
    }

    private fun openNiagaraSearch(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, NIAGARA_SEARCH_URI).apply {
                setPackage(NIAGARA_PACKAGE)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Error opening Niagara search; falling back to Pastiera QuickLauncher", error)
            false
        }
    }
}
