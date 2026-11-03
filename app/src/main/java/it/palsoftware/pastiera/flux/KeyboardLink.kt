package it.palsoftware.pastiera.flux

import android.os.Bundle
import io.github.fluxsniffermods.core.link.FluxLinkProvider
import io.github.fluxsniffermods.core.link.FluxMigration
import io.github.fluxsniffermods.core.link.LinkMethods
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.getSymMappingsPage2
import it.palsoftware.pastiera.personaliseSymbolsDefaults

/**
 * What the other Flux apps ask of the keyboard: Flux Desktop, the built-in keyboard's Alt and Sym
 * layers as the keyboard has them; and the apps that were Flux Titan Hub's modules, the settings
 * and files the hub kept for them, once, to take over.
 */
class KeyboardLink : FluxLinkProvider() {
    override fun answer(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        return when (method) {
            LinkMethods.KEY_LAYERS -> {
                val custom = runCatching { SettingsManager.getSymMappingsPage2(context) }.getOrDefault(emptyMap())
                val alt = runCatching { AltModifierMappingResolver.resolve(context.assets, context) }.getOrDefault(emptyMap())
                val sym = custom.ifEmpty {
                    runCatching { SettingsManager.personaliseSymbolsDefaults(context, KeyMappingLoader.loadSymKeyMappingsPage2(context.assets)) }.getOrDefault(emptyMap())
                }
                val symShifted = if (custom.isEmpty()) runCatching { KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(context.assets) }.getOrDefault(emptyMap()) else emptyMap()
                Bundle().apply {
                    fun layer(name: String, map: Map<Int, String>) {
                        val entries = map.entries.toList()
                        putIntArray(LinkMethods.layerKeys(name), entries.map { it.key }.toIntArray())
                        putStringArray(LinkMethods.layerChars(name), entries.map { it.value }.toTypedArray())
                    }
                    layer(LinkMethods.LAYER_ALT, alt)
                    layer(LinkMethods.LAYER_SYM, sym)
                    layer(LinkMethods.LAYER_SYM_SHIFTED, symShifted)
                }
            }
            LinkMethods.LEGACY_PREFS -> {
                val prefixes = extras?.getStringArray("prefixes")?.toList().orEmpty().filter { it.length >= 3 }
                FluxMigration.answer(SettingsManager.getPreferences(context).all, prefixes)
            }
            // Only what streaming kept: the phone's identity to Moonlight and the devices paired with it
            LinkMethods.LEGACY_FILES -> FluxMigration.answerFiles(
                context.filesDir, extras?.getStringArray("paths")?.toList().orEmpty(), allowed = listOf("streaming/")
            )
            else -> null
        }
    }
}
