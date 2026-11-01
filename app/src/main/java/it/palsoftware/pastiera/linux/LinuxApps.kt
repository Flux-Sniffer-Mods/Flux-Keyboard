package it.palsoftware.pastiera.linux

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import it.palsoftware.pastiera.FluxLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * The programs installed in Linux (their .desktop files, as its own menu lists them), each a
 * shortcut on the phone: opened in the desktop from the Desktop page, or pinned to the home
 * screen. Read through the shell when asked, kept for when Linux isn't reachable.
 */
object LinuxApps {
    private const val TAG = "FluxLinux"

    data class App(val id: String, val name: String, val exec: String, val terminal: Boolean, val iconName: String?)

    private val mutableApps = MutableStateFlow<List<App>>(emptyList())
    val apps: StateFlow<List<App>> = mutableApps

    private fun dir(context: Context) = File(context.filesDir, "linux-apps").apply { mkdirs() }
    private fun listFile(context: Context) = File(dir(context), "apps.json")
    fun iconFile(context: Context, app: App) = File(dir(context), app.id.removeSuffix(".desktop") + ".png")

    fun icon(context: Context, app: App): Bitmap? =
        iconFile(context, app).takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /** The list kept from last time, at once. */
    fun cached(context: Context): List<App> {
        if (mutableApps.value.isEmpty()) runCatching {
            val array = JSONArray(listFile(context).readText())
            mutableApps.value = List(array.length()) { i ->
                val o = array.getJSONObject(i)
                App(o.getString("id"), o.getString("name"), o.getString("exec"), o.optBoolean("terminal"),
                    o.optString("icon").ifBlank { null })
            }
        }
        return mutableApps.value
    }

    fun find(context: Context, id: String): App? = cached(context).firstOrNull { it.id == id }

    /** Reads the programs (and their icons) from Linux again: blocking, through the shell. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val mode = LinuxDesktop.installedMode(app) ?: return
        if (!LinuxDesktop.canRun(mode)) return
        val listing = LinuxDesktop.runForOutput(app, mode, "apps") ?: return
        val found = parse(listing, Locale.getDefault(), LinuxDesktop.desktop(app).xdg)
        val names = found.mapNotNull { it.iconName }.filter(::safeIcon).distinct()
        if (names.isNotEmpty()) {
            val icons = LinuxDesktop.runForOutput(app, mode, "icons " + names.joinToString(" ") { "'$it'" }).orEmpty()
            for ((name, png) in parseIcons(icons)) {
                found.filter { it.iconName == name }.forEach { entry ->
                    runCatching { iconFile(app, entry).writeBytes(png) }
                }
            }
        }
        runCatching {
            listFile(app).writeText(JSONArray(found.map {
                JSONObject().put("id", it.id).put("name", it.name).put("exec", it.exec)
                    .put("terminal", it.terminal).put("icon", it.iconName ?: "")
            }).toString())
        }.onFailure { FluxLog.w(TAG, "apps: $it") }
        mutableApps.value = found
    }

    /** An icon name the shell may be given: a theme name or an absolute path, nothing that climbs out. */
    internal fun safeIcon(name: String) = name.isNotBlank() && ".." !in name && name.all { it.isLetterOrDigit() || it in "._-+/" }

    /**
     * The programs in `apps`'s output: each file's [Desktop Entry], its name in [locale] where
     * it has one; hidden ones, other desktops' own ([desktop] is this one's name) and anything
     * without a command left out, a
     * later file of the same name (the user's own) replacing an earlier one.
     */
    internal fun parse(output: String, locale: Locale, desktop: String = "XFCE"): List<App> {
        val byId = LinkedHashMap<String, App>()
        val language = locale.language
        val full = locale.toString()
        for (chunk in output.split("@@app ").drop(1)) {
            val id = chunk.substringBefore('\n').trim()
            var section = ""
            val fields = HashMap<String, String>()
            for (line in chunk.lineSequence().drop(1)) {
                val text = line.trim()
                if (text.startsWith("[")) { section = text; continue }
                if (section != "[Desktop Entry]" || text.startsWith("#")) continue
                val eq = text.indexOf('=')
                if (eq > 0) fields.putIfAbsent(text.substring(0, eq).trim(), text.substring(eq + 1).trim())
            }
            if (fields["Type"] != "Application") continue
            if (fields["NoDisplay"] == "true" || fields["Hidden"] == "true") continue
            val only = fields["OnlyShowIn"]
            if (only != null && only.split(';').none { it.equals(desktop, true) }) continue
            val not = fields["NotShowIn"]
            if (not != null && not.split(';').any { it.equals(desktop, true) }) continue
            val exec = cleanExec(fields["Exec"] ?: continue)
            if (exec.isBlank()) continue
            val name = fields["Name[$full]"] ?: fields["Name[$language]"] ?: fields["Name"] ?: id.removeSuffix(".desktop")
            byId[id] = App(id, name, exec, fields["Terminal"] == "true", fields["Icon"]?.takeIf { it.isNotBlank() })
        }
        return byId.values.sortedBy { it.name.lowercase() }
    }

    /** A .desktop Exec line as a command: its field codes (%f, %U…) dropped, %% as %. */
    internal fun cleanExec(exec: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < exec.length) {
            val c = exec[i]
            if (c == '%' && i + 1 < exec.length) {
                if (exec[i + 1] == '%') out.append('%')
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** `icons`'s output: each icon's name with its PNG. */
    internal fun parseIcons(output: String): List<Pair<String, ByteArray>> =
        output.split("@@icon ").drop(1).mapNotNull { chunk ->
            val name = chunk.substringBefore('\n').trim()
            val data = chunk.substringAfter('\n').filterNot { it.isWhitespace() }
            runCatching { name to java.util.Base64.getDecoder().decode(data) }.getOrNull()
                ?.takeIf { it.second.size > 8 }
        }

    /**
     * Opens [app] in the desktop, and the desktop's picture at once (on [displayId], a TV
     * screen, when given; it waits there while the desktop starts, if it has to). [context]
     * should be on screen: Android only lets a visible app open its picture.
     */
    fun open(context: Context, app: App, width: Int, height: Int, displayId: Int? = null) {
        val ctx = context.applicationContext
        val launch = { Thread { LinuxDesktop.launch(ctx, app.exec, app.terminal) }.start() }
        when (LinuxDesktop.state.value) {
            LinuxDesktop.State.Running -> launch()
            LinuxDesktop.State.Starting -> Unit
            // Started first: the program once the desktop has had a moment to come up
            else -> LinuxDesktop.start(ctx, width, height) {
                Thread.sleep(4_000)
                launch()
            }
        }
        LinuxDesktop.show(context, displayId)
    }

    /** [app] pinned to the home screen: a tap opens it in the desktop. */
    fun pin(context: Context, app: App): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val icon = icon(context, app)?.let { Icon.createWithBitmap(it) }
            ?: Icon.createWithResource(context, it.palsoftware.pastiera.R.mipmap.ic_launcher)
        val intent = Intent(context, LinuxLaunchActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(LinuxLaunchActivity.EXTRA_APP, app.id)
        val shortcut = ShortcutInfo.Builder(context, "linux_" + app.id.removeSuffix(".desktop"))
            .setShortLabel(app.name.take(24))
            .setLongLabel(app.name)
            .setIcon(icon)
            .setIntent(intent)
            .build()
        return runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
    }
}
