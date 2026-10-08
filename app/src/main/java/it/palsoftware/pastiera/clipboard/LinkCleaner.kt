package it.palsoftware.pastiera.clipboard

/**
 * Cleans links the keyboard pastes (clipboard history and the paste chip): tracking parameters go
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
        "spm", "scm", "trk", "trkCampaign", "sc_campaign", "ncid", "cmpid", "wt_mc",
        // Affiliate networks, email tools, app deep-link and shopping-ad tags
        "irclickid", "irgwc", "_kx", "ck_subscriber_id", "mkt_tok", "_branch_match_id",
        "_branch_referrer", "srsltid", "epik", "sc_cid", "icid", "ocid", "cvid", "obOrigUrl",
        "ga_source", "ga_medium", "ga_campaign", "gad_source", "gad_campaignid", "dicbo"
    )

    private val AMAZON_TRACKING = setOf(
        "ref", "ref_", "psc", "pd_rd_w", "pd_rd_r", "pd_rd_wg", "pd_rd_i", "pf_rd_p", "pf_rd_r", "pf_rd_s",
        "pf_rd_t", "pf_rd_i", "pf_rd_m", "content-id", "tag", "linkCode", "linkId", "creative", "creativeASIN",
        "camp", "dib", "dib_tag", "qid", "sr", "sprefix", "crid", "smid", "spLa", "sp_csd", "_encoding", "social_share",
        "starsLeft", "skipTwisterOG", "ascsubtag", "asc_campaign", "asc_refurl", "asc_source"
    )
    private val AMAZON_DOMAINS = listOf(
        "amazon.de", "amazon.fr", "amazon.it", "amazon.es", "amazon.nl", "amazon.se", "amazon.pl",
        "amazon.com.be", "amazon.ca", "amazon.com.mx", "amazon.com.br", "amazon.com.au", "amazon.in",
        "amazon.co.jp", "amazon.sg", "amazon.ae", "amazon.sa", "amazon.com.tr", "amazon.eg"
    )
    private val EBAY_TRACKING = setOf(
        "_trkparms", "_trksid", "hash", "amdata", "mkcid", "mkrid", "campid", "toolid", "mkevt", "customid",
        "_from", "itmmeta", "ssspo", "sssrc", "ssuid", "widget_ver", "norover", "siteid", "mkpid", "emsid",
        "ul_noapp", "_ul", "srcrot", "rt", "epid_ref"
    )
    private val EBAY_DOMAINS = listOf(
        "ebay.com", "ebay.co.uk", "ebay.de", "ebay.fr", "ebay.it", "ebay.es", "ebay.nl", "ebay.ie",
        "ebay.at", "ebay.ch", "ebay.be", "ebay.pl", "ebay.ca", "ebay.com.au"
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
        "amazon.com" to AMAZON_TRACKING,
        "amazon.co.uk" to AMAZON_TRACKING,
        "linkedin.com" to setOf("trackingId", "refId", "lipi", "rcm"),
        "google.com" to setOf("ved", "ei", "sca_esv", "sxsrf", "gs_lcp", "gs_lp", "aqs", "sourceid", "oq", "uact", "sclient", "iflsig", "biw", "bih", "dpr"),
        "etsy.com" to setOf(
            "click_key", "click_sum", "ref", "pro", "sts", "frs", "ga_order", "ga_search_type",
            "ga_view_type", "ga_search_query", "sr_prefetch", "pf_from", "pla_spm", "plkey",
            "organic_search_click", "content_source", "logging_key", "external", "dd_referrer", "share_time"
        ),
        "aliexpress.com" to setOf(
            "aff_fcid", "aff_fsk", "aff_platform", "aff_trace_key", "sk", "terminal_id",
            "afSmartRedirect", "gatewayAdapt", "pdp_npi", "pdp_ext_f", "algo_pvid", "algo_exp_id",
            "gps-id", "scm_id", "scm-url", "pvid", "utparam", "_t", "sourceType", "srcSns", "bizType",
            "social_params", "businessType", "tt", "shareId", "platform", "templateId", "spreadType", "curPageLogUid"
        ),
        "temu.com" to setOf("_x_ads_channel", "_x_ads_sub_channel", "_x_vst_scene", "_x_sessn_id", "refer_page_name", "refer_page_id", "refer_page_sn", "_x_share_id", "share_uin", "_bg_fs"),
        "walmart.com" to setOf("athbdg", "athcpid", "athpgid", "athznid", "athieid", "athena", "athancid", "athrsid", "wl13", "adsRedirect", "classType", "from"),
        "target.com" to setOf("lnk", "afid", "ref", "AFID", "CPNG", "adgroup"),
        "bestbuy.com" to setOf("ref", "loc", "acampID", "irclickid"),
        "facebook.com" to setOf("mibextid", "rdid", "share_url", "sfnsn", "__tn__", "__cft__[0]", "ref", "fs", "sh", "paipv", "eav", "extid", "comment_tracking", "notif_id", "notif_t"),
        "threads.net" to setOf("xmt", "slof"),
        "pinterest.com" to setOf("invite_code", "sender", "sfo", "mweb_unauth_id"),
        "nytimes.com" to setOf("smid", "smtyp", "partner", "campaign_id", "emc", "instance_id", "nl", "regi_id", "segment_id", "te", "user_id"),
        "medium.com" to setOf("source", "sk"),
        "substack.com" to setOf("r", "triedRedirect", "showWelcomeOnShare", "publication_id", "post_id", "isFreemail"),
        "twitch.tv" to setOf("sr", "tt_medium", "tt_content"),
        "music.apple.com" to setOf("ls", "app", "at", "ct", "itscg", "itsct"),
        "apps.apple.com" to setOf("ls", "at", "ct", "itscg", "itsct", "mt"),
        "play.google.com" to setOf("pcampaignid", "referrer"),
        "booking.com" to setOf("aid", "label", "sid", "srpvid", "ucfs", "arphpl", "dest_type", "dist", "type"),
        "airbnb.com" to setOf("source_impression_id", "previous_page_section_name", "federated_search_id", "s", "unique_share_id", "viralityEntryPoint"),
        "vinted.co.uk" to setOf("referrer", "homepage_session_id"),
        "vinted.com" to setOf("referrer", "homepage_session_id"),
        "depop.com" to setOf("utm_campaign", "_branch_match_id"),
        "shein.com" to setOf("url_from", "share_time", "share_from", "onelink", "scene", "sharetype", "currency", "lang", "ici", "src_identifier", "src_module", "src_tab_page_id")
    ) + AMAZON_DOMAINS.associateWith { AMAZON_TRACKING } + EBAY_DOMAINS.associateWith { EBAY_TRACKING }


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
     * The clipboard's link, cleaned in place (so any paste is clean: the keyboard's, the app's own and
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
