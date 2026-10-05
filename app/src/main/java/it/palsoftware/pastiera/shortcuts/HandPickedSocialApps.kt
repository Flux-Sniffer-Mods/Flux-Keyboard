package it.palsoftware.pastiera.shortcuts

/*
 * Social media apps, picked by hand, on top of Google Play's Social category. Play files some of
 * the biggest elsewhere (X and Quora under News, Pinterest under Lifestyle, LinkedIn under
 * Business, YouTube under Video players, Snapchat under Communication, Twitch under
 * Entertainment); they count as Social here, for the app list and for Enter. A few that aren't in
 * the Play ranking at all are added with the same kind of suggestions. See docs/app-shortcuts.md.
 */
internal object HandPickedSocialApps {

    /** Apps in the list that count as Social whatever their Play category. */
    val packages: Set<String> = setOf(
        "com.facebook.katana", "com.facebook.lite", "com.instagram.android", "com.instagram.barcelona",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.twitter.android", "com.reddit.frontpage",
        "com.snapchat.android", "com.pinterest", "com.linkedin.android", "com.tumblr",
        "com.google.android.youtube", "tv.twitch.android.app", "com.bereal.ft", "com.quora.android",
        "com.vkontakte.android", "xyz.blueskyweb.app", "org.joinmastodon.android", "com.nextdoor",
        "com.substack.app", "com.sina.weibo", "com.bd.nproject"
    )

    private fun app(packageName: String, appName: String, vararg searchLinks: String) = AppShortcutPreset(
        packageName, appName, AppCategory.Social, source = null, shortcuts = emptyMap(),
        suggested = mapOf(
            StandardShortcut.New to listOf(AppIntent.share()),
            StandardShortcut.Search to listOf(AppIntent.search()) + searchLinks.map { AppIntent.link(it) }
        )
    )

    /** Social media apps the Play ranking doesn't include. */
    val added: List<AppShortcutPreset> = listOf(
        app("xyz.blueskyweb.app", "Bluesky", "https://bsky.app/search"),
        app("org.joinmastodon.android", "Mastodon"),
        app("com.nextdoor", "Nextdoor"),
        app("com.substack.app", "Substack"),
        app("com.sina.weibo", "Weibo"),
        app("com.bd.nproject", "Lemon8")
    )
}
