"""Writes SuggestedAppShortcuts.kt and play-ranking.json from play-apps.json (see crawl.py).

Top 100 Social apps and top 10 of every other listed category, by downloads then reviews;
games, keyboards and system components left out. Run from the repository root.
"""
import json, re, html
import os
db=json.load(open(os.environ.get("PLAY_DB","play-apps.json")))
CAT={"SOCIAL":"Social","COMMUNICATION":"Communication","PRODUCTIVITY":"Productivity","ENTERTAINMENT":"Entertainment",
 "VIDEO_PLAYERS":"VideoPlayers","MUSIC_AND_AUDIO":"Music","SHOPPING":"Shopping","MAPS_AND_NAVIGATION":"Maps",
 "TRAVEL_AND_LOCAL":"Travel","NEWS_AND_MAGAZINES":"News","BOOKS_AND_REFERENCE":"Books","PHOTOGRAPHY":"Photography",
 "FINANCE":"Finance","EDUCATION":"Education","HEALTH_AND_FITNESS":"Health","FOOD_AND_DRINK":"Food","LIFESTYLE":"Lifestyle",
 "BUSINESS":"Business","TOOLS":"Tools","DATING":"Dating","SPORTS":"Sports","WEATHER":"Weather"}
KEYBOARDS={"com.google.android.inputmethod.latin","com.touchtype.swiftkey","com.samsung.android.honeyboard",
 "com.google.android.apps.inputmethod.hindi","com.syntellia.fleksy.keyboard","com.grammarly.android.keyboard",
 "com.microsoft.swiftkey","com.baidu.input","com.sohu.inputmethod.sogou","com.iflytek.inputmethod"}
SHARE_NEW={"Social","Communication","Productivity","Business"}
# System components and services: nothing to open with a shortcut
SYSTEM={"com.google.android.ims","com.google.android.marvin.talkback","com.google.android.apps.wellbeing",
 "com.samsung.android.lool","com.google.android.play.games","com.google.android.youtube.tv",
 "com.hp.android.printservice","com.google.android.webview","com.google.android.gms"}
SYSTEM_TITLE=re.compile(r"Carrier Services|Accessibility Suite|Digital Wellbeing|Device Care|Print Service|for Android TV|System WebView|Remote \(Official\)|MYTECNO",re.I)
def is_system(p,i): return p in SYSTEM or SYSTEM_TITLE.search(i.get("title",""))
def is_keyboard(p,i): return p in KEYBOARDS or re.search(r"keyboard|キーボード|clavier|tastiera|teclado",i.get("title",""),re.I)
S=lambda u:("link",u)
OVR={
 "com.facebook.katana":{"Search":[S("https://www.facebook.com/search/top/")]},
 "com.facebook.lite":{"Search":[S("https://m.facebook.com/search/")]},
 "com.instagram.android":{"Search":[S("https://www.instagram.com/explore/")]},
 "com.zhiliaoapp.musically":{"Search":[S("https://www.tiktok.com/search")]},
 "com.twitter.android":{"New":[("share",None)],"Search":[S("https://x.com/search"),S("https://twitter.com/search")]},
 "com.instagram.barcelona":{"Search":[S("https://www.threads.com/search"),S("https://www.threads.net/search")]},
 "com.reddit.frontpage":{"Search":[S("https://www.reddit.com/search/")]},
 "com.pinterest":{"Search":[S("https://www.pinterest.com/search/pins/")]},
 "com.linkedin.android":{"Search":[S("https://www.linkedin.com/search/results/all/")]},
 "com.tumblr":{"Search":[S("https://www.tumblr.com/search")]},
 "xyz.blueskyweb.app":{"Search":[S("https://bsky.app/search")]},
 "com.quora.android":{"Search":[S("https://www.quora.com/search")]},
 "com.vkontakte.android":{"Search":[S("https://vk.com/search")]},
 "com.google.android.youtube":{"Search":[("search",None),S("https://www.youtube.com/results")]},
 "com.netflix.mediaclient":{"Search":[S("https://www.netflix.com/search")]},
 "tv.twitch.android.app":{"Search":[S("https://www.twitch.tv/search")]},
 "com.spotify.music":{"Search":[S("spotify:search"),S("https://open.spotify.com/search")]},
 "com.google.android.apps.youtube.music":{"Search":[("search",None),S("https://music.youtube.com/search")]},
 "com.soundcloud.android":{"Search":[S("https://soundcloud.com/search")]},
 "com.amazon.mShop.android.shopping":{"Search":[("search",None),S("https://www.amazon.com/s")]},
 "com.ebay.mobile":{"Search":[S("https://www.ebay.com/sch/i.html")]},
 "com.etsy.android":{"Search":[S("https://www.etsy.com/search")]},
 "com.walmart.android":{"Search":[S("https://www.walmart.com/search")]},
 "com.google.android.apps.maps":{"Search":[S("geo:0,0?q=")]},
 "com.waze":{"Search":[S("https://waze.com/ul")]},
 "com.airbnb.android":{"Search":[S("https://www.airbnb.com/s/homes")]},
 "com.booking":{"Search":[S("https://www.booking.com/searchresults.html")]},
 "com.google.android.apps.magazines":{"Search":[S("https://news.google.com/search")]},
 "com.medium.reader":{"Search":[S("https://medium.com/search")]},
 "com.google.android.apps.messaging":{"New":[("sendto","smsto:")]},
 "com.microsoft.office.outlook":{"New":[("sendto","mailto:")]},
}
chosen={}
for p,i in db.items():
    c=i.get("cat")
    if c not in CAT or is_keyboard(p,i) or is_system(p,i) or p.startswith("it.palsoftware"): continue
    chosen.setdefault(c,[]).append((p,i))
out=[]
for c,apps in chosen.items():
    apps.sort(key=lambda x:(-x[1]["dln"],-x[1]["rv"]))
    n=100 if c=="SOCIAL" else 10
    for p,i in apps[:n]: out.append((c,p,i))
def kt_intent(kind,val):
    if kind=="link": return f'AppIntent.link("{val}")'
    if kind=="search": return "AppIntent.search()"
    if kind=="sendto": return f'AppIntent(AppIntent.ACTION_SENDTO, data = "{val}")'
    return "AppIntent.share()"
def esc(t): return t.replace("\\","\\\\").replace('"','\\"').replace("$","\\$")
lines=[]
for c,p,i in out:
    cat=CAT[c]; o=OVR.get(p,{})
    new=o.get("New") or ([("share",None)] if cat in SHARE_NEW else [])
    search=o.get("Search",[])
    if not any(k=="search" for k,_ in search): search=[("search",None)]+search
    parts=[]
    if new: parts.append("StandardShortcut.New to listOf("+", ".join(kt_intent(*x) for x in new)+")")
    parts.append("StandardShortcut.Search to listOf("+", ".join(kt_intent(*x) for x in search)+")")
    full=html.unescape(i["title"])
    title=re.sub(r"\s*(?:[-–—:|•]|\s\()\s*.*$","",full).strip() or full
    lines.append(f'        app("{p}", "{esc(title)}", AppCategory.{cat}, "{esc(i["dl"] or "?")}", ' + ", ".join(parts) + "),")
src='''package it.palsoftware.pastiera.shortcuts

/*
 * Suggested shortcuts for the top apps in each Google Play category (top 100 in Social, top 10
 * in the others; games and keyboards left out), ranked by downloads and then reviews on Google
 * Play (US), collected September 2026. Generated by tools/app-shortcuts/generate.py; see
 * docs/app-shortcuts.md.
 *
 * None of these apps documents Android keyboard shortcuts for these actions, so the suggestions
 * are intents into the apps' own screens: New opens the share screen (a new post, message or
 * note) in social, communication, productivity and business apps; Search opens the app's own
 * search screen, or one of its search links. Each is used only when the app on the phone
 * accepts it, otherwise the key goes through unchanged.
 */
internal object SuggestedAppShortcuts {

    @Suppress("UNUSED_PARAMETER")
    private fun app(
        packageName: String,
        appName: String,
        category: AppCategory,
        downloads: String,
        vararg suggested: Pair<StandardShortcut, List<AppIntent>>
    ) = AppShortcutPreset(packageName, appName, category, source = null, shortcuts = emptyMap(), suggested = mapOf(*suggested))

    val all: List<AppShortcutPreset> = listOf(
'''+"\n".join(lines)+'''
    )
}
'''
import os
OUT=os.environ.get("OUT","app/src/main/java/it/palsoftware/pastiera/shortcuts/SuggestedAppShortcuts.kt")
open(OUT,"w").write(src)
# The ranking the list was built from, kept next to the scripts
snap=[{"package":p,"title":html.unescape(i["title"]),"category":c,"downloads":i["dl"],"reviews":int(i["rv"])} for c,p,i in out]
open(os.environ.get("SNAPSHOT","tools/app-shortcuts/play-ranking.json"),"w").write(json.dumps(snap,indent=1,ensure_ascii=False)+"\n")
from collections import Counter
print(len(out), Counter(CAT[c] for c,_,_ in out))
