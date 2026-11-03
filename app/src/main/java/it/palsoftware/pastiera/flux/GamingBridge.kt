package it.palsoftware.pastiera.flux

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import io.github.fluxsniffermods.core.FluxApp
import io.github.fluxsniffermods.core.link.FluxLink
import io.github.fluxsniffermods.core.link.LinkMethods
import java.util.concurrent.Executors

/**
 * The keyboard and Flux Gaming: gaming mode is told when a game's text field is typed in (so keys
 * type there, not play), and the keyboard leaves a game's keys alone while gaming mode has them.
 * Nothing happens without Flux Gaming installed.
 */
object GamingBridge {
    /** The game gaming mode plays while its keys are gaming mode's (its package), else null. */
    @Volatile private var keysFor: String? = null
    /** Flux Gaming's own Xbox player is in front: its fields' keys and text are its own. */
    @Volatile var playerInFront = false
        private set

    /** Calls to Flux Gaming, in order, never on the keyboard's own thread. */
    private val worker = Executors.newSingleThreadExecutor()
    private var observer: ContentObserver? = null
    @Volatile private var app: Context? = null
    @Volatile private var installed: Boolean? = null
    @Volatile private var installedCheckedAt = 0L
    @Volatile private var askedAt = 0L

    fun start(context: Context) {
        val app = context.applicationContext
        this.app = app
        worker.execute { installed(app) }
    }

    /**
     * Whether Flux Gaming is installed, looked at again every half minute on [worker] (it may be
     * installed after the keyboard started, or removed): found, its state is asked and watched;
     * gone, nothing of it is kept.
     */
    private fun installed(app: Context): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val known = installed
        if (known != null && now - installedCheckedAt < 30_000) return known
        val found = FluxApp.GAMING.installed(app)
        installed = found
        installedCheckedAt = now
        if (found && known != true) {
            refresh(app)
            refreshLauncher(app)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (observer == null) observer = FluxLink.watch(app, FluxApp.GAMING, LinkMethods.TOPIC_GAME_KEYS) {
                    worker.execute { refresh(app) }
                }
            }
        } else if (!found) {
            keysFor = null
            playerInFront = false
            launcherGames = emptyList()
            launcherEntries = null to null
        }
        return found
    }

    private fun refresh(context: Context) {
        askedAt = android.os.SystemClock.elapsedRealtime()
        val state = FluxLink.call(context, FluxApp.GAMING, LinkMethods.GAME_STATE)
        keysFor = state?.takeIf { it.getBoolean("keys") }?.let { it.getString("playing") ?: "" }
        playerInFront = state?.getBoolean("player") == true
    }

    /**
     * Whether gaming mode has the keys of [packageName], the app they go to: the game it plays,
     * or anywhere there's no field to type in ([field] false). While it says so, it's asked
     * again every few seconds, so a Flux Gaming that stopped without a word (ended, removed)
     * never keeps the keyboard's keys from it for long.
     */
    fun passesKeys(packageName: String?, field: Boolean): Boolean {
        val game = keysFor ?: return false
        val app = app
        if (app != null && android.os.SystemClock.elapsedRealtime() - askedAt > 5_000) {
            askedAt = android.os.SystemClock.elapsedRealtime()
            worker.execute { if (installed(app)) refresh(app) }
        }
        return packageName == game || !field
    }

    /** One of the keyboard's moments in a field: "input_started", "editor_started", "keyboard_requested", "editor_finished". */
    fun editor(context: Context, moment: String, info: EditorInfo? = null) {
        val app = context.applicationContext
        val extras = info?.let {
            Bundle().apply {
                putString("package", it.packageName)
                putInt("inputType", it.inputType)
                putInt("fieldId", it.fieldId)
                putString("hint", it.hintText?.toString())
            }
        }
        worker.execute {
            if (installed(app)) FluxLink.call(app, FluxApp.GAMING, LinkMethods.GAME_EDITOR, moment, extras)
        }
    }

    /** One of the Flux apps' own screens is in front (the Games app, the Xbox player, the TV's screen): their keys are theirs. */
    fun fluxAppInFront(packageName: String?): Boolean =
        packageName != null && FluxApp.entries.any { it != FluxApp.KEYBOARD && it.packageName == packageName }

    /** Flux Gaming's games for the quick launcher (none while it isn't to list them there). Blocking. */
    class Game(val id: String, val name: String, val packages: Set<String>, val launch: android.content.Intent)

    fun games(context: Context): List<Game> {
        val answer = FluxLink.call(context, FluxApp.GAMING, LinkMethods.GAMES, "launcher") ?: return emptyList()
        val ids = answer.getStringArray("ids").orEmpty()
        val names = answer.getStringArray("names").orEmpty()
        val packages = answer.getStringArray("packages").orEmpty()
        val launches = answer.getStringArray("launches").orEmpty()
        return ids.indices.mapNotNull { i ->
            val launch = launches.getOrNull(i)?.let { runCatching { android.content.Intent.parseUri(it, android.content.Intent.URI_INTENT_SCHEME) }.getOrNull() }
                ?: return@mapNotNull null
            Game(ids[i], names.getOrElse(i) { "" }, packages.getOrElse(i) { "" }.split(',').filter { it.isNotEmpty() }.toSet(), launch)
        }
    }

    /** Flux Gaming's Xbox Cloud Gaming entry and Games app, as the quick launcher opens them. Blocking. */
    fun entries(context: Context): Pair<android.content.Intent?, android.content.Intent?> {
        val answer = FluxLink.call(context, FluxApp.GAMING, LinkMethods.GAMES, "launcher") ?: return null to null
        fun intent(uri: String?) = uri?.let { runCatching { android.content.Intent.parseUri(it, android.content.Intent.URI_INTENT_SCHEME) }.getOrNull() }
        return intent(answer.getString("xbox_launch")) to intent(answer.getString("games_app"))
    }

    /** The games and Flux Gaming's entries, as the quick launcher last had them (asked again in the background each time). */
    @Volatile var launcherGames: List<Game> = emptyList()
        private set
    @Volatile var launcherEntries: Pair<android.content.Intent?, android.content.Intent?> = null to null
        private set

    /** Asked again off the keyboard's thread: the quick launcher shows what was last found at once. */
    fun refreshLauncher(context: Context) {
        val app = context.applicationContext
        worker.execute {
            if (!installed(app)) return@execute
            launcherGames = games(app)
            launcherEntries = entries(app)
        }
    }
}
