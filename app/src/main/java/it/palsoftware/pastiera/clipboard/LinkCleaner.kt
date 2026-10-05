package it.palsoftware.pastiera.clipboard

/**
 * Cleans links Pastiera pastes (clipboard history and the paste chip): tracking parameters go
 * (utm_*, fbclid, gclid, si and the like) and mobile hosts become the normal site
 * (m.youtube.com → youtube.com, en.m.wikipedia.org → en.wikipedia.org), and redirect links (a
 * Google result's google.com/url?q=…) become the link they lead to. Text around the links,
 * and every other part of a link, stays as it was (palsoftware/pastiera#278).
 */
object LinkCleaner {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    // Parameters that only track where a link was shared from
    private val TRACKING_PREFIXES = listOf("utm_", "mtm_", "pk_", "hsa_", "__hs", "_hs")
    private val TRACKING = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid",
        "ttclid", "li_fat_id", "mc_cid", "mc_eid", "igshid", "igsh", "ref_src", "ref_url",
        "_ga", "_gl", "vero_id", "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid",
        "spm", "scm", "trk", "trkCampaign", "sc_campaign", "ncid", "cmpid", "wt_mc"
    )

    // Share ids some sites add; only removed on those sites, where they never change the page
    private val SITE_TRACKING = mapOf(
        "youtube.com" to setOf(
            "si", "is", "feature", "pp", "ab_channel", "source_ve_path",
            "embeds_referring_euri", "embeds_referring_origin", "embeds_euri"
        ),
        "youtu.be" to setOf("si", "is", "feature", "pp"),
        "open.spotify.com" to setOf("si", "context", "nd"),
        "instagram.com" to setOf("igsh", "igshid", "img_index"),
        "twitter.com" to setOf("s", "t", "ref_src"),
        "x.com" to setOf("s", "t", "ref_src"),
        "reddit.com" to setOf("share_id", "utm_name", "rdt"),
        "tiktok.com" to setOf("is_from_webapp", "sender_device", "sender_web_id", "_r", "_t"),
        "amazon.com" to setOf("ref", "ref_", "psc", "pd_rd_w", "pd_rd_r", "pd_rd_wg", "pf_rd_p", "pf_rd_r", "content-id"),
        "amazon.co.uk" to setOf("ref", "ref_", "psc", "pd_rd_w", "pd_rd_r", "pd_rd_wg", "pf_rd_p", "pf_rd_r", "content-id"),
        "linkedin.com" to setOf("trackingId", "refId", "lipi", "rcm")
    )

    // Mobile hosts with a plain desktop twin
    private val MOBILE_HOSTS = mapOf(
        "m.youtube.com" to "youtube.com",
        "m.facebook.com" to "facebook.com",
        "mobile.twitter.com" to "twitter.com",
        "mobile.x.com" to "x.com",
        "m.twitter.com" to "twitter.com",
        "m.reddit.com" to "reddit.com",
        "i.reddit.com" to "reddit.com",
        "m.imdb.com" to "imdb.com",
        "m.aliexpress.com" to "aliexpress.com",
        "m.ebay.com" to "ebay.com",
        "m.ebay.co.uk" to "ebay.co.uk"
    )

    /** [text] with every link in it cleaned. */
    fun clean(text: String): String = URL.replace(text) { cleanUrl(it.value) }

    /** A link that only redirects to another (Google's results, AMP pages, Facebook's and Instagram's link wrappers): the link it leads to. */
    internal fun unwrapRedirect(link: String): String? {
        val match = Regex("""^https?://([^/?#]+)(/[^?#]*)?(\?[^#]*)?""", RegexOption.IGNORE_CASE).find(link) ?: return null
        val host = match.groupValues[1].lowercase()
        val path = match.groupValues[2]
        val query = match.groupValues[3].removePrefix("?")
        fun param(vararg names: String): String? = query.split('&').firstNotNullOfOrNull { part ->
            val name = part.substringBefore('=')
            if (name in names) runCatching { java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull() else null
        }?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        val google = Regex("""^(www\.)?google\.[a-z.]+$""").matches(host)
        return when {
            google && path == "/url" -> param("q", "url")
            google && path.startsWith("/amp/s/") -> "https://" + path.removePrefix("/amp/s/")
            host in setOf("l.facebook.com", "lm.facebook.com", "l.messenger.com", "l.instagram.com", "l.threads.net") -> param("u")
            host == "www.youtube.com" && path == "/redirect" || host == "youtube.com" && path == "/redirect" -> param("q")
            else -> null
        }
    }

    fun cleanUrl(url: String): String {
        // Trailing punctuation belongs to the sentence, not the link
        val trailing = url.takeLastWhile { it in ".,;:!?)]}" }
        val unwrapped = url.dropLast(trailing.length)
        unwrapRedirect(unwrapped)?.let { return cleanUrl(it) + trailing }
        val link = unwrapped
        val schemeEnd = link.indexOf("://") + 3
        val hostEnd = link.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd).let { if (it < 0) link.length else it }
        val scheme = link.substring(0, schemeEnd)
        var host = link.substring(schemeEnd, hostEnd)
        var rest = link.substring(hostEnd)

        val lowerHost = host.lowercase()
        MOBILE_HOSTS[lowerHost]?.let { host = it }
            ?: Regex("""^([a-z]{2,3})\.m\.(wikipedia|wiktionary|wikibooks|wikiquote|wikivoyage)\.org$""")
                .find(lowerHost)?.let { host = "${it.groupValues[1]}.${it.groupValues[2]}.org" }

        val siteParams = SITE_TRACKING.entries
            .firstOrNull { (site, _) -> host.lowercase() == site || host.lowercase().endsWith(".$site") }
            ?.value.orEmpty()

        val fragmentStart = rest.indexOf('#')
        val fragment = if (fragmentStart >= 0) rest.substring(fragmentStart) else ""
        if (fragmentStart >= 0) rest = rest.substring(0, fragmentStart)
        val queryStart = rest.indexOf('?')
        if (queryStart >= 0) {
            val path = rest.substring(0, queryStart)
            val kept = rest.substring(queryStart + 1).split('&').filter { part ->
                val name = part.substringBefore('=')
                part.isNotEmpty() && !isTracking(name, siteParams)
            }
            rest = if (kept.isEmpty()) path else path + "?" + kept.joinToString("&")
        }
        return scheme + host + rest + fragment + trailing
    }

    private fun isTracking(name: String, siteParams: Set<String>): Boolean {
        val lower = name.lowercase()
        return lower in TRACKING.map { it.lowercase() } ||
            TRACKING_PREFIXES.any { lower.startsWith(it) } ||
            name in siteParams
    }

    /**
     * The clipboard's link, cleaned in place (so any paste is clean: Pastiera's, the app's own and
     * Ctrl+V). Only plain text is replaced, or formatted text that's just a link; never a password
     * manager's copy or a file. Returns whether the clip was replaced.
     */
    fun cleanClipboard(context: android.content.Context, clipboard: android.content.ClipboardManager, maxLength: Int = 4_000): Boolean {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return false
        if (clip.itemCount != 1) return false
        val description = clip.description ?: return false
        val extras = description.extras
        if (extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return false
        val item = clip.getItemAt(0) ?: return false
        if (item.uri != null || item.intent != null) return false
        val text = item.text?.toString() ?: return false
        if (text.length > maxLength || !text.contains("://")) return false
        val justALink = URL.matchEntire(text.trim()) != null
        val plain = description.mimeTypeCount == 1 &&
            description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) && item.htmlText == null
        if (!plain && !justALink) return false
        val cleaned = clean(text)
        if (cleaned == text) return false
        return runCatching {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(description.label, cleaned))
        }.isSuccess
    }
}
