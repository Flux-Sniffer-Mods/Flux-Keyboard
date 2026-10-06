package it.palsoftware.pastiera

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocaleManager {
    fun wrapContext(base: Context): Context {
        val tag = SettingsManager.getAppLanguageTag(base)
        if (tag.isNullOrBlank()) {
            return base
        }

        val locale = Locale.forLanguageTag(tag)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
