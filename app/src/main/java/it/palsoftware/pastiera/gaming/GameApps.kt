package it.palsoftware.pastiera.gaming

/** Which launcher or emulator a profile's game is played in, by name. */
object GameApps {
    private val DOLPHIN = setOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr", "org.mm.jr")
    private val PPSSPP = setOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
    private val AZAHAR = setOf(
        "org.azahar_emu.azahar", "io.github.lime3ds.android", "io.github.azaharplus.android",
        "org.citra.citra_emu", "org.citra.citra_emu.canary", "org.citra.emu"
    )
    private val EDEN = setOf(
        "dev.eden.eden_emulator", "dev.legacy.eden_emulator", "dev.eden.eden_emulator.nightly", "dev.legacy.eden_emulator.nightly",
        "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.ea", "org.citron.citron_emu", "org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.ea"
    )

    /**
     * Other emulators, by their name: known so their games are kept the right way up (a DS or
     * 3DS's screens stacked, the rest landscape), with the keys bound in their own settings.
     */
    private val OTHERS = mapOf(
        "com.retroarch" to "RetroArch", "com.retroarch.aarch64" to "RetroArch", "com.retroarch.ra32" to "RetroArch",
        "com.swordfish.lemuroid" to "Lemuroid",
        "com.github.stenzek.duckstation" to "DuckStation", "com.epsxe.ePSXe" to "ePSXe",
        "xyz.aethersx2.android" to "NetherSX2", "xyz.aethersx2.tturnip" to "NetherSX2", "xyz.aethersx2.cturnip" to "NetherSX2",
        "me.magnum.melonds" to "melonDS", "me.magnum.melonds.nightly" to "melonDS", "me.magnum.melondualds" to "melonDS",
        "com.dsemu.drastic" to "DraStic", "com.hydra.noods" to "NooDS",
        "com.panda3ds.pandroid" to "Panda3DS", "io.github.mandarine3ds.mandarine" to "Mandarine",
        "org.mupen64plusae.v3.fzurita" to "Mupen64Plus", "org.mupen64plusae.v3.fzurita.pro" to "Mupen64Plus",
        "org.mupen64plusae.v3.fzurita.amazon" to "Mupen64Plus", "org.mupen64plusae.v3.alpha" to "Mupen64Plus",
        "com.flycast.emulator" to "Flycast", "io.recompiled.redream" to "Redream",
        "org.devmiyax.yabasanshioro2" to "Yaba Sanshiro", "org.devmiyax.yabasanshioro2.pro" to "Yaba Sanshiro",
        "com.fastemulator.gba" to "My Boy!", "com.fastemulator.gbc" to "My OldBoy!",
        "it.dbtecno.pizzaboygba" to "Pizza Boy", "it.dbtecno.pizzaboygbapro" to "Pizza Boy",
        "it.dbtecno.pizzaboy" to "Pizza Boy", "it.dbtecno.pizzaboypro" to "Pizza Boy",
        "it.dbtecno.pizzaboyscbasic" to "Pizza Boy", "it.dbtecno.pizzaboyscpro" to "Pizza Boy",
        "com.explusalpha.Snes9xPlus" to "Snes9x EX+", "com.explusalpha.GbaEmu" to "GBA.emu",
        "com.explusalpha.GbcEmu" to "GBC.emu", "com.explusalpha.NesEmu" to "NES.emu", "com.explusalpha.MdEmu" to "MD.emu",
        "org.vita3k.emulator" to "Vita3K", "org.vita3k.emulator.ikhoeyZX" to "Vita3K",
        "info.cemu.cemu" to "Cemu", "info.cemu.Cemu" to "Cemu",
        "org.kenjinx.android" to "Kenji-NX", "skyline.emu" to "Skyline",
        "com.seleuco.mame4d2024" to "MAME4droid", "com.seleuco.mame4droid" to "MAME4droid",
        "org.scummvm.scummvm" to "ScummVM"
    )

    /** Emulators of handhelds with two screens, one above the other: played upright. */
    private val TWO_SCREENS_3DS = setOf("Azahar", "Panda3DS", "Mandarine")
    private val TWO_SCREENS_DS = setOf("melonDS", "DraStic", "NooDS")

    /** Emulators of Nintendo's consoles: their A and B (X and Y) sit the other way round from Xbox's. */
    private val NINTENDO = setOf(
        "Dolphin", "Azahar", "Eden", "Panda3DS", "Mandarine", "melonDS", "DraStic", "NooDS", "Cemu", "Kenji-NX", "Skyline",
        "My Boy!", "My OldBoy!", "Pizza Boy", "Snes9x EX+", "GBA.emu", "GBC.emu", "NES.emu"
    )

    fun nintendo(packageName: String): Boolean = emulator(packageName) in NINTENDO

    /** A DS or 3DS emulator: "3DS", "DS", or null. */
    fun twoScreens(packageName: String): String? = when (emulator(packageName)) {
        in TWO_SCREENS_3DS -> "3DS"
        in TWO_SCREENS_DS -> "DS"
        else -> null
    }

    /** Xbox's apps that stream Game Pass games (cloud gaming). */
    private val XBOX = setOf("com.gamepass", "com.gamepass.beta", "com.microsoft.xboxone.smartglass", "com.microsoft.xboxone.smartglass.beta")

    /** GameNative, GameHub, Xbox or an emulator's name for [profile]'s app; null for other apps. */
    fun appFor(profile: GameProfile): String? = profile.packages.firstNotNullOfOrNull { appFor(it) }

    fun appFor(packageName: String): String? = when {
        packageName == GameNativeBridge.PACKAGE -> "GameNative"
        packageName.contains("gamehub", true) || packageName == "com.xiaoji.egggame" ||
            // GameHub Lite named as a benchmark app (not com.tencent.ig, the real PUBG Mobile's name too)
            packageName in GAMEHUB_BENCHMARK_NAMES -> "GameHub"
        packageName in XBOX || packageName == it.palsoftware.pastiera.BuildConfig.APPLICATION_ID -> XBOX_NAME
        else -> emulator(packageName)
    }

    const val XBOX_NAME = "Xbox Cloud Gaming"

    private val GAMEHUB_BENCHMARK_NAMES = setOf("com.antutu.ABenchMark", "com.antutu.benchmark.full", "com.ludashi.aibench")

    /** An emulator's name, for one Flux Keyboard knows. */
    fun emulator(packageName: String): String? = when (packageName) {
        in DOLPHIN -> "Dolphin"
        in PPSSPP -> "PPSSPP"
        in AZAHAR -> "Azahar"
        in EDEN -> "Eden"
        else -> OTHERS[packageName]
    }
}
