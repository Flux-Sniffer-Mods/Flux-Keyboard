package it.palsoftware.pastiera.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's last times not responding, with where each thread was (its main thread first), kept
 * where the shell can read them; null when there are none.
 */
private fun anrTraces(context: android.content.Context): java.io.File? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(android.app.ActivityManager::class.java) ?: return null
    val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        .filter { it.reason == android.app.ApplicationExitInfo.REASON_ANR }.take(2)
    if (exits.isEmpty()) return null
    val dir = context.getExternalFilesDir(null) ?: return null
    val file = java.io.File(dir, "anr-traces.txt")
    file.bufferedWriter().use { out ->
        for (exit in exits) {
            out.write("--- ${java.util.Date(exit.timestamp)}: ${exit.description}\n")
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // The main thread, then the rest up to a size worth sending
            val main = Regex("(?s)\"main\".*?\n\n").find(trace)?.value.orEmpty()
            out.write(main)
            out.write(trace.take(60_000))
            out.write("\n")
        }
    }
    file
}.getOrNull()

/** Android's record of the last crashes (Flux Keyboard's and its web pages'), saved to share. */
@Composable
internal fun CrashLogRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = "system.crash_log",
        icon = "\u26A0",
        title = stringResource(R.string.crash_log),
        description = stringResource(R.string.crash_log_description)
    ) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val file = "/storage/emulated/0/Download/Flux Keyboard/crash-log.txt"
                val app = it.palsoftware.pastiera.BuildConfig.APPLICATION_ID
                // When the app stopped answering: where its threads were, which only the app can read
                val traces = anrTraces(context)
                // The Linux desktop: how it's set up, its last start and Linux's own logs
                val desktop = context.getExternalFilesDir(null)?.let { dir ->
                    runCatching {
                        java.io.File(dir, "desktop.txt").apply { writeText(it.palsoftware.pastiera.linux.LinuxDesktop.report(context)) }
                    }.getOrNull()
                }
                it.palsoftware.pastiera.adb.AdbShell.run(
                    "f='$file'; mkdir -p \"\${f%/*}\"; { " +
                        // Why Android last ended the app: a crash, its web page's renderer dying, memory running low
                        "echo '== why it stopped =='; dumpsys activity exit-info $app | head -n 120; " +
                        "echo '== crashes =='; logcat -d -b crash -t 400; " +
                        "echo '== warnings and errors =='; logcat -d -b main,system '*:W' 'ActivityTaskManager:I' | " +
                        "grep -i -E '$app|gamehub|egggame|Permission Denial|quickstep|launcher3|Recents|OverviewProxy|FluxGameMode|FluxGameLaunch|FluxExtraScreen|Stream(Wake|Host|Session|Input|Audio|Control)|AndroidRuntime|chromium|cr_|webview|lowmemorykiller|has died|Killing' | grep -v 'Changing focus' | tail -n 400; " +
                        // The screen and window settings gaming mode and the desktop change
                        // (through a pipe: Android's services can't write into a file in Download)
                        // The input devices (the keyboard, the side buttons): which one each key comes from
                        "echo '== input devices =='; dumpsys input | grep -E '^ +Device -?[0-9]+:' | head -30; " +
                        "echo '== screen and window settings =='; wm size | cat; wm density | cat; " +
                        "for k in force_resizable_activities enable_non_resizable_multi_window enable_freeform_support " +
                        "force_desktop_mode_on_external_displays override_desktop_mode_features override_desktop_experience_features; do " +
                        "echo \"\$k=\$(settings get global \$k | cat)\"; done; " +
                        "echo \"rotation=\$(settings get system user_rotation | cat) auto=\$(settings get system accelerometer_rotation | cat)\"; " +
                        // Recents: whether apps' turning is ignored, and what Recents and its screen are doing
                        "echo \"ignore orientation=\$(cmd window get-ignore-orientation-request | cat)\"; " +
                        "echo '== recents =='; dumpsys activity recents | head -n 12; " +
                        "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|RecentsActivity|quickstep' | head -n 10; " +
                        // The official GameHub's ways in (its activities, actions and links), to start its games directly
                        "for p in com.xiaoji.egggame gamehub.lite; do pm path \$p >/dev/null 2>&1 && { echo \"== \$p ==\"; " +
                        "dumpsys package \$p | grep -E 'Activity|Action:|Scheme:|Authority:|Path' | grep -v -i 'permission' | head -n 150; }; done; " +
                        // Where games are found: the game folders, every Steam library on the phone's storage
                        // and the launchers' own folders, and the launchers' home screen shortcuts
                        "echo '== game folders =='; find '${it.palsoftware.pastiera.gaming.GameFolders.ROOT}' -maxdepth 4 ! -name '.nomedia' 2>/dev/null | head -n 200; " +
                        "echo '== steam libraries =='; find /storage/emulated/0 -maxdepth 7 -name 'appmanifest_*.acf' 2>/dev/null | head -n 60; " +
                        "for p in gamehub.lite emuready.gamehub.lite com.antutu.ABenchMark com.antutu.benchmark.full com.ludashi.aibench com.xiaoji.egggame app.gamenative; do " +
                        "[ -d \"/storage/emulated/0/Android/data/\$p\" ] && { echo \"== \$p files ==\"; find \"/storage/emulated/0/Android/data/\$p\" -maxdepth 4 -type d 2>/dev/null | head -n 60; }; done; " +
                        // Only the game launchers' own shortcuts: other apps' hold contacts and chats
                        "echo '== launcher shortcuts =='; dumpsys shortcut | grep -E '^ *Package: |shortLabel=' | while IFS= read -r l; do case \"\$l\" in " +
                        "*'Package: '*) case \"\$l\" in *'Package: gamehub'*|*'Package: emuready'*|*'Package: com.antutu'*|*'Package: com.ludashi'*|*'Package: com.xiaoji'*|*'Package: app.gamenative'*) k=1; echo \"\$l\";; *) k=;; esac;; " +
                        "*) [ -n \"\$k\" ] && echo \"\$l\";; esac; done | head -n 80; " +
                        // Gaming mode reads keys through Flux Keyboard's accessibility service: whether it's on
                        "echo '== accessibility services =='; settings get secure enabled_accessibility_services | tr ':' '\\n'; " +
                        // Flux Keyboard's own record of gaming mode, streaming and game launches (the phone's turns over in seconds)
                        "echo '== gaming and streaming =='; tail -c 120000 '${context.getExternalFilesDir(null)?.path}/${it.palsoftware.pastiera.FluxLog.FILE_NAME}'; " +
                        (if (traces != null) "echo '== not responding: threads =='; cat '${traces.path}'; " else "") +
                        (if (desktop != null) "echo '== desktop =='; cat '${desktop.path}'; " else "") +
                        // Everything through a pipe: Android's services can't write into a file in Download
                        "} 2>&1 | cat > \"\$f\"; echo ok",
                    15_000
                )?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (saved) R.string.crash_log_saved else R.string.adb_not_running, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    // A fresh start: Android's logs and Flux Keyboard's own record emptied, so the next crash
    // log holds only what happens from now (and saves quickly)
    FluxActionRow(
        linkId = "system.clear_logs",
        icon = "\u2715",
        title = stringResource(R.string.crash_log_clear),
        description = stringResource(R.string.crash_log_clear_description)
    ) {
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(context.getExternalFilesDir(null), it.palsoftware.pastiera.FluxLog.FILE_NAME).delete()
                }
                it.palsoftware.pastiera.adb.AdbShell.run("logcat -c -b main -b system -b crash; echo ok", 10_000)?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (cleared) R.string.crash_log_cleared else R.string.adb_not_running, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

