package it.palsoftware.pastiera

import it.palsoftware.pastiera.BuildConfig

/**
 * Fornisce informazioni sulla build dell'app.
 */
object BuildInfo {
    private val devVersion = Regex("""^(.+)-flux\.(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})$""")

    /** "Version 1.0.0", or for a dev build "Dev build 1.0.1 · 2026-10-05 15:49 UTC". */
    fun getBuildInfoString(): String = describe(BuildConfig.VERSION_NAME, BuildConfig.RELEASE_CHANNEL)

    internal fun describe(version: String, channel: String): String {
        devVersion.matchEntire(version)?.destructured?.let { (base, y, mo, d, h, mi) ->
            return "Dev build $base · $y-$mo-$d $h:$mi UTC"
        }
        return if (channel == "nightly") "Nightly $version" else "Version $version"
    }
}
