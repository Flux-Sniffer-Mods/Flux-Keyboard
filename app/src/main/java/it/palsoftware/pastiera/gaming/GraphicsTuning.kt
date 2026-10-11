package it.palsoftware.pastiera.gaming

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Graphics settings that suit this phone's GPU, for emulators whose settings files can be
 * written (Dolphin, PPSSPP), and the same advice in words for apps whose can't. Performance
 * first: Vulkan where the driver is good for it, a light internal resolution on Mali (the Titan 2
 * Elite's GPU), shaders built in the background rather than stuttering.
 */
object GraphicsTuning {
    enum class Gpu { MALI, ADRENO, POWERVR, XCLIPSE, OTHER }

    data class Phone(val gpu: Gpu, val vulkan: Boolean) {
        /** Vulkan, unless the driver is too old or known to do worse with it. */
        val useVulkan get() = vulkan && gpu != Gpu.POWERVR
        /** A GPU with headroom for more than native resolution. */
        val strong get() = gpu == Gpu.ADRENO || gpu == Gpu.XCLIPSE
    }

    @Volatile private var known: Phone? = null

    /** This phone's GPU, read once from its OpenGL driver. */
    fun phone(context: Context): Phone = known ?: Phone(
        gpuOf(renderer() ?: socGuess()),
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x401000)
    ).also { known = it }

    internal fun gpuOf(renderer: String): Gpu {
        val name = renderer.lowercase()
        return when {
            "mali" in name || "immortalis" in name -> Gpu.MALI
            "adreno" in name -> Gpu.ADRENO
            "powervr" in name || "img " in name -> Gpu.POWERVR
            "xclipse" in name -> Gpu.XCLIPSE
            else -> Gpu.OTHER
        }
    }

    /** Without a driver to ask: MediaTek's chips use Mali, Qualcomm's Adreno. */
    private fun socGuess(): String = if (Build.VERSION.SDK_INT >= 31) when (Build.SOC_MANUFACTURER.lowercase()) {
        "mediatek" -> "mali"
        "qti", "qualcomm" -> "adreno"
        else -> ""
    } else ""

    private fun renderer(): String? = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
        ), 0, configs, 0, 1, count, 0)
        val config = configs[0] ?: return@runCatching null
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val name = GLES20.glGetString(GLES20.GL_RENDERER)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        name
    }.getOrNull()

    // ---- Settings written for the emulator ----

    /** Dolphin's Dolphin.ini and GFX.ini with this phone's graphics settings. */
    internal fun dolphin(core: String, gfx: String, phone: Phone): Pair<String, String> {
        var c = FluxLayout.withKey(core, "Core", "GFXBackend", if (phone.useVulkan) "Vulkan" else "OGL")
        c = FluxLayout.withKey(c, "Core", "CPUThread", "True")
        var g = FluxLayout.withKey(gfx, "Settings", "InternalResolution", if (phone.strong) "2" else "1")
        // Async ubershaders are heavy for Mali: skip drawing until a shader is ready instead
        g = FluxLayout.withKey(g, "Settings", "ShaderCompilationMode", if (phone.strong) "2" else "3")
        g = FluxLayout.withKey(g, "Settings", "WaitForShadersBeforeStarting", "True")
        g = FluxLayout.withKey(g, "Hardware", "VSync", "False")
        return c to g
    }

    /** PPSSPP's ppsspp.ini with this phone's graphics settings. */
    internal fun ppsspp(ini: String, phone: Phone): String {
        var out = FluxLayout.withKey(ini, "Graphics", "GraphicsBackend", if (phone.useVulkan) "3 (VULKAN)" else "0 (OPENGL)")
        out = FluxLayout.withKey(out, "Graphics", "InternalResolution", if (phone.strong) "3" else "2")
        out = FluxLayout.withKey(out, "Graphics", "FrameSkip", "0")
        out = FluxLayout.withKey(out, "Graphics", "AutoFrameSkip", "False")
        out = FluxLayout.withKey(out, "Graphics", "HardwareTransform", "True")
        return FluxLayout.withKey(out, "Graphics", "SoftwareSkinning", "True")
    }

    /** Whether [pkg]'s graphics settings can be written here. */
    fun writable(pkg: String): Boolean = GameApps.emulator(pkg) in setOf("Dolphin", "PPSSPP")

    /** Writes this phone's graphics settings into [pkg]'s (blocking: through the shell, the emulator stopped first). */
    fun tune(context: Context, pkg: String): FluxLayout.Result {
        if (!AdbShell.available()) return FluxLayout.Result.FAILED
        val phone = phone(context)
        return when (GameApps.emulator(pkg)) {
            "Dolphin" -> {
                val dir = FluxLayout.dolphinDir(pkg) ?: return FluxLayout.Result.NOT_FOUND
                AdbShell.run("am force-stop $pkg", 3_000)
                val (core, gfx) = dolphin(FluxLayout.read("$dir/Config/Dolphin.ini"), FluxLayout.read("$dir/Config/GFX.ini"), phone)
                if (FluxLayout.write("$dir/Config/Dolphin.ini", core) && FluxLayout.write("$dir/Config/GFX.ini", gfx)) FluxLayout.Result.DONE
                else FluxLayout.Result.FAILED
            }
            "PPSSPP" -> {
                val dir = FluxLayout.ppssppDir(pkg) ?: return FluxLayout.Result.NOT_FOUND
                AdbShell.run("am force-stop $pkg", 3_000)
                val file = "$dir/SYSTEM/ppsspp.ini"
                if (FluxLayout.write(file, ppsspp(FluxLayout.read(file), phone))) FluxLayout.Result.DONE else FluxLayout.Result.FAILED
            }
            else -> FluxLayout.Result.BY_HAND
        }
    }

    /** What was set, in words. */
    fun summary(context: Context, pkg: String): String {
        val phone = phone(context)
        val api = if (phone.useVulkan) "Vulkan" else "OpenGL"
        return when (GameApps.emulator(pkg)) {
            "Dolphin" -> context.getString(if (phone.strong) R.string.game_mode_tune_dolphin_strong else R.string.game_mode_tune_dolphin, api)
            else -> context.getString(if (phone.strong) R.string.game_mode_tune_ppsspp_strong else R.string.game_mode_tune_ppsspp, api)
        }
    }

    // ---- Advice for apps whose settings can't be written ----

    /** The best settings for this phone in [app] (GameApps' name, null for a game of its own), in words; null when it has none. */
    fun advice(context: Context, app: String?): String? {
        val phone = phone(context)
        val api = if (phone.useVulkan) "Vulkan" else "OpenGL ES"
        return when (app) {
            "Azahar" -> context.getString(R.string.game_mode_advice_azahar, api)
            "Eden" -> context.getString(
                if (phone.gpu == Gpu.MALI) R.string.game_mode_advice_eden_mali else R.string.game_mode_advice_eden
            )
            "GameNative", "GameHub" -> context.getString(
                R.string.game_mode_advice_windows, app,
                context.getString(if (phone.gpu == Gpu.ADRENO) R.string.game_mode_advice_driver_turnip else R.string.game_mode_advice_driver_system)
            )
            GameApps.XBOX_NAME -> null
            null -> context.getString(R.string.game_mode_advice_game)
            else -> null
        }
    }
}
