package it.palsoftware.pastiera.gaming

/** Which launcher or emulator a profile's game is played in, by name. */
object GameApps {
    private val DOLPHIN = setOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr", "org.mm.jr")
    private val PPSSPP = setOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
    private val AZAHAR = setOf(
        "org.azahar_emu.azahar", "io.github.lime3ds.android", "org.citra.citra_emu", "org.citra.citra_emu.canary"
    )
    private val EDEN = setOf(
        "dev.eden.eden_emulator", "dev.legacy.eden_emulator", "org.yuzu.yuzu_emu", "org.citron.citron_emu", "org.sudachi.sudachi_emu"
    )

    /** GameNative, GameHub or an emulator's name for [profile]'s app; null for other apps. */
    fun appFor(profile: GameProfile): String? = profile.packages.firstNotNullOfOrNull { appFor(it) }

    fun appFor(packageName: String): String? = when {
        packageName == GameNativeBridge.PACKAGE -> "GameNative"
        packageName.contains("gamehub", true) || packageName == "com.xiaoji.egggame" -> "GameHub"
        else -> emulator(packageName)
    }

    /** An emulator's name, for one Flux Keyboard knows. */
    fun emulator(packageName: String): String? = when (packageName) {
        in DOLPHIN -> "Dolphin"
        in PPSSPP -> "PPSSPP"
        in AZAHAR -> "Azahar"
        in EDEN -> "Eden"
        else -> null
    }
}
