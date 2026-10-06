package it.palsoftware.pastiera.update

import org.json.JSONObject

/** Flux Keyboard's changelog: where release notes send you for more, never Pastiera's website */
const val FORK_CHANGELOG_URL = "https://github.com/Flux-Sniffer-Mods/Flux-Keyboard/blob/flux-release/FORK_CHANGES.md"

data class ReleaseNotesSummary(
    val version: String,
    val title: String,
    val highlights: List<String>,
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList(),
    val docsUrl: String = FORK_CHANGELOG_URL,
    // Flux Keyboard: what the notes cover, a heading for the fork's own changes, and the
    // Pastiera team's changes since their last official release in a section of their own
    val intro: String? = null,
    val sectionTitle: String? = null,
    val upstreamTitle: String? = null,
    val upstreamChanges: List<String> = emptyList(),
    val docsLabel: String? = null
) {
    companion object {
        fun fallback(version: String, languageTag: String = "en"): ReleaseNotesSummary {
            val language = normalizeReleaseNotesLanguage(languageTag)
            return ReleaseNotesSummary(
                version = version,
                title = when (language) {
                    else -> "${it.palsoftware.pastiera.BuildConfig.APP_NAME} $version"
                },
                highlights = when (language) {
                    "de" -> listOf(
                        "Die neu gestalteten Einstellungen sind durchsuchbar, direkt verlinkbar und zuverlässiger navigierbar.",
                        "Pastiera passt sich sauberer an das gerundete Display des Titan 2 Elite an. Clicks-Tastaturen erhalten eigene Steuerungen und zuverlässigere Eingabe.",
                        "Die Bildschirmtastatur bietet eigene Themes, Presets, Software-Modifier, Zahlenreihe und bessere Barrierefreiheit."
                    )
                    "it" -> listOf(
                        "Le impostazioni ridisegnate sono ricercabili, collegabili direttamente e più affidabili da navigare.",
                        "Pastiera si adatta meglio al display arrotondato del Titan 2 Elite. Le tastiere Clicks ricevono controlli dedicati e un input più affidabile.",
                        "La tastiera su schermo offre temi, preset, modificatori software, riga numerica e accessibilità migliorata."
                    )
                    else -> listOf(
                        "Redesigned Settings are searchable, directly linkable, and more reliable to navigate.",
                        "Pastiera fits the Titan 2 Elite’s rounded display more cleanly. Clicks keyboards gain dedicated controls and more reliable input.",
                        "The on-screen keyboard adds custom themes, presets, software modifiers, a number row, and better accessibility."
                    )
                },
                improvements = when (language) {
                    "de" -> listOf(
                        "Snippets, Emoji- und Symbol-Shortcodes sowie feinere Satzzeichenregeln beschleunigen wiederkehrende Eingaben.",
                        "Vorschläge können mehrere Wörterbücher und lokal gelernte nächste Wörter verwenden.",
                        "Neue Sprachressourcen, darunter Griechisch, ergänzen aktualisierte Unicode- und Emoji-Daten."
                    )
                    "it" -> listOf(
                        "Snippet, shortcode per emoji e simboli e regole di punteggiatura più precise velocizzano l'inserimento ricorrente.",
                        "I suggerimenti possono usare più dizionari e sequenze di parole successive apprese localmente.",
                        "Nuove risorse linguistiche, incluso il greco, accompagnano dati Unicode ed emoji aggiornati."
                    )
                    else -> listOf(
                        "Snippets, emoji and symbol shortcodes, and refined punctuation rules speed up recurring input.",
                        "Suggestions can use multiple dictionaries and locally learned next-word sequences.",
                        "New language resources, including Greek, accompany updated Unicode and emoji data."
                    )
                },
                bugFixes = when (language) {
                    "de" -> listOf("Candidate- und Emoji-Oberflächen reagieren zuverlässiger; Importe, Backup-Archive und eigene Tippgeräusche werden strenger geprüft.")
                    "it" -> listOf("Le superfici dei candidati e delle emoji sono più affidabili; importazioni, archivi di backup e suoni personalizzati vengono convalidati con maggiore rigore.")
                    else -> listOf("Candidate and emoji surfaces are more reliable; imports, backup archives, and custom typing sounds receive stricter validation.")
                },
                docsUrl = FORK_CHANGELOG_URL
            )
        }
    }
}

private val FORK_VERSION = Regex("""^(\d+(?:\.\d+)*)-flux\.(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})$""")

/** "0.86-flux.202609260416" → "0.86"; other versions as they are. */
fun shortVersion(version: String): String = FORK_VERSION.find(version.trim())?.groupValues?.get(1) ?: version.trim()

/**
 * A version as people read it: "0.86-flux.202609260416" → "0.86 · 26 Sep 2026, 04:16" (the build
 * time, UTC). Other versions come back as they are.
 */
fun friendlyVersion(version: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val m = FORK_VERSION.find(version.trim()) ?: return version.trim()
    val (base, y, mo, d, h, mi) = m.destructured
    val month = runCatching {
        java.time.Month.of(mo.toInt()).getDisplayName(java.time.format.TextStyle.SHORT, locale)
    }.getOrDefault(mo)
    return "$base · ${d.toInt()} $month $y, $h:$mi"
}

/** A fork version's build time as a number ("0.90-flux.202609261444" → 202609261444), or null for other versions. */
internal fun forkBuildStamp(version: String?): Long? {
    val m = FORK_VERSION.find(version?.trim() ?: return null) ?: return null
    return m.groupValues.drop(2).joinToString("").toLongOrNull()
}

/**
 * Release notes shipped inside the app (assets/fork/whats_new.json), used instead of the online
 * notes when present: a fork build describes its own changes, offline.
 *
 * An entry is either text, or {"text": …, "after": "yyyyMMddHHmm"}: new since the build made at
 * that time. With [sinceVersion] (the version the notes were last seen on) only entries newer
 * than it are listed; without it, or when nothing is newer, all of them are.
 */
fun bundledReleaseNotes(
    context: android.content.Context,
    version: String,
    sinceVersion: String? = null
): ReleaseNotesSummary? = runCatching {
    val body = context.assets.open("fork/whats_new.json").bufferedReader().use { it.readText() }
    parseBundledReleaseNotes(body, version, sinceStamp(body, sinceVersion))
        ?: if (sinceVersion != null) parseBundledReleaseNotes(body, version, null) else null
}.getOrNull()

/**
 * Whether the notes bundled with this build list anything new since [sinceVersion]; null when
 * the build has no notes of its own or that version's build time isn't known.
 */
fun bundledNotesHaveNewSince(context: android.content.Context, sinceVersion: String): Boolean? = runCatching {
    val body = context.assets.open("fork/whats_new.json").bufferedReader().use { it.readText() }
    bundledNotesHaveNewSince(body, sinceVersion)
}.getOrNull()

internal fun bundledNotesHaveNewSince(body: String, sinceVersion: String): Boolean? {
    val stamp = sinceStamp(body, sinceVersion) ?: return null
    return parseBundledReleaseNotes(body, sinceVersion, stamp) != null
}

/** A version's build time: in a dev build's name, or for releases (0.91) from the notes' list. */
private fun sinceStamp(body: String, sinceVersion: String?): Long? {
    if (sinceVersion == null) return null
    return forkBuildStamp(sinceVersion)
        ?: runCatching { JSONObject(body).optJSONObject("releases")?.optString(sinceVersion) }.getOrNull()?.toLongOrNull()
}

internal fun parseBundledReleaseNotes(body: String, version: String, sinceStamp: Long?): ReleaseNotesSummary? {
    val json = JSONObject(body)
    val highlights = parseEntries(json, "highlights", sinceStamp)
    val improvements = parseEntries(json, "improvements", sinceStamp)
    val bugFixes = parseEntries(json, "bugFixes", sinceStamp)
    val upstream = parseEntries(json, "upstream", sinceStamp)
    if (highlights.isEmpty() && improvements.isEmpty() && bugFixes.isEmpty()) return null
    return ReleaseNotesSummary(
        version = version,
        title = json.optString("title").takeIf(String::isNotBlank)?.let { "$it ${shortVersion(version)}" }
            ?: "${it.palsoftware.pastiera.BuildConfig.APP_NAME} ${shortVersion(version)}",
        highlights = highlights.ifEmpty { improvements },
        improvements = if (highlights.isEmpty()) emptyList() else improvements,
        bugFixes = bugFixes,
        docsUrl = json.optString("docsUrl").takeIf { it.startsWith("https://") } ?: FORK_CHANGELOG_URL,
        intro = (if (sinceStamp != null) json.optString("introSince") else "").takeIf(String::isNotBlank)
            ?: json.optString("intro").takeIf(String::isNotBlank),
        sectionTitle = json.optString("sectionTitle").takeIf(String::isNotBlank),
        upstreamTitle = json.optString("upstreamTitle").takeIf(String::isNotBlank),
        upstreamChanges = upstream,
        docsLabel = json.optString("docsLabel").takeIf(String::isNotBlank)
    )
}

/** Entries newer than [sinceStamp] (all of them without it); plain text entries are the oldest. */
private fun parseEntries(json: JSONObject, key: String, sinceStamp: Long?): List<String> {
    val array = json.optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val entry = array.opt(index)
            val text = ((entry as? JSONObject)?.optString("text") ?: entry as? String)?.trim().orEmpty()
            val after = (entry as? JSONObject)?.optString("after")?.toLongOrNull() ?: 0L
            if (text.isNotBlank() && (sinceStamp == null || after >= sinceStamp)) add(text)
        }
    }
}

private fun normalizeReleaseNotesLanguage(languageTag: String): String {
    val language = languageTag
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
        .filter { it in 'a'..'z' }
    return language.ifBlank { "en" }
}

