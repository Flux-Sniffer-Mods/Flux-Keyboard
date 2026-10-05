"""Crawls Google Play (US) for the apps behind the suggested app shortcuts.

Starts from each category's page and the seed packages, follows "similar apps", and records
each app's category, downloads and reviews in $PLAY_DB (play-apps.json). Social is crawled
deeper, since its top 100 are used. Then run generate.py.

    python3 tools/app-shortcuts/crawl.py && python3 tools/app-shortcuts/generate.py
"""
import re, json, sys, time, urllib.request, urllib.parse, concurrent.futures as cf, os
CATS = ["SOCIAL","COMMUNICATION","PRODUCTIVITY","ENTERTAINMENT","VIDEO_PLAYERS","MUSIC_AND_AUDIO","SHOPPING",
        "MAPS_AND_NAVIGATION","TRAVEL_AND_LOCAL","NEWS_AND_MAGAZINES","BOOKS_AND_REFERENCE","PHOTOGRAPHY","FINANCE",
        "EDUCATION","HEALTH_AND_FITNESS","FOOD_AND_DRINK","LIFESTYLE","BUSINESS","TOOLS","DATING","SPORTS","WEATHER"]
DB = os.environ.get("PLAY_DB", "play-apps.json")
db = json.load(open(DB)) if os.path.exists(DB) else {}
def get(url):
    for i in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent":"Mozilla/5.0","Accept-Language":"en-US"})
            return urllib.request.urlopen(req, timeout=20).read().decode("utf-8","ignore")
        except Exception as e:
            if "404" in str(e): return None
            time.sleep(2+i*3)
    return None
MULT={"K":1e3,"M":1e6,"B":1e9}
def num(s):
    m=re.match(r"([\d.]+)\s*([KMB]?)",s or "")
    return float(m.group(1))*MULT.get(m.group(2),1) if m else 0
def fetch(pkg):
    h=get(f"https://play.google.com/store/apps/details?id={pkg}&hl=en&gl=US")
    if not h: return pkg, {"missing":True}
    cat=re.search(r'"applicationCategory":"([A-Z_]+)"',h)
    dl=re.search(r'<div class="ClM7O">([^<]*)</div><div class="g1rdde">Downloads',h)
    rv=re.search(r'<div class="g1rdde">([\d.]+[KMB]?) reviews',h)
    title=re.search(r'<title id="main-title">(.*?) - Apps on Google Play',h)
    sim=list(dict.fromkeys(re.findall(r'details\?id=([a-zA-Z0-9._]+)',h)))
    return pkg, {"cat":cat.group(1) if cat else None,"dl":dl.group(1) if dl else "","dln":num((dl.group(1) if dl else "").replace("+","")),
                 "rv":num(rv.group(1)) if rv else 0,"title":title.group(1) if title else pkg,"sim":sim}
seeds=set(json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "seeds.json"))))
for c in CATS:
    for suffix in ["", "&hl=en&gl=GB"]:
        h=get(f"https://play.google.com/store/apps/category/{c}?hl=en&gl=US"+suffix) or ""
        seeds.update(re.findall(r'details\?id=([a-zA-Z0-9._]+)',h))
frontier=[p for p in seeds if p not in db]
rounds=3
for r in range(rounds):
    print("round",r,"fetch",len(frontier),flush=True)
    with cf.ThreadPoolExecutor(12) as ex:
        for pkg,info in ex.map(fetch, frontier): db[pkg]=info
    json.dump(db,open(DB,"w"))
    # expand only from apps in our categories
    nxt=set()
    for p,i in db.items():
        if i.get("cat") in CATS:
            nxt.update(i.get("sim",[]))
    frontier=[p for p in nxt if p not in db][:1500]
    if not frontier: break
# Social goes deeper: its top 100 are used
for r in range(4):
    frontier=set()
    for p,i in db.items():
        if i.get("cat")=="SOCIAL": frontier.update(i.get("sim",[]))
    frontier=[p for p in frontier if p not in db]
    print("social round",r,"fetch",len(frontier),flush=True)
    if not frontier: break
    with cf.ThreadPoolExecutor(12) as ex:
        for pkg,info in ex.map(fetch, frontier): db[pkg]=info
    json.dump(db,open(DB,"w"))
from collections import Counter
print(Counter(i.get("cat") for i in db.values()).most_common(40))
