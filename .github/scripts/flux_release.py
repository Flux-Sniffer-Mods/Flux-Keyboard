#!/usr/bin/env python3
"""Versions and release notes for Flux Keyboard builds.

  flux_release.py version <branch> [version_name]
      Prints kind=, name= and code= lines for $GITHUB_OUTPUT. The flux-release branch builds
      full releases: the newest version in whats_new.json's "releases". Every other branch
      builds dev builds: the next version after that, stamped with the build time
      (0.93-flux.yyyyMMddHHmm). A given version_name must be of the branch's kind.

  flux_release.py notes <version> <previous tag or ""> <commit>
      Prints release notes listing only what changed since the previous build: for a full
      release the previous full release, for a dev build the previous build of either kind.

Entries in whats_new.json are either text or {"text", "after": "yyyyMMddHHmm"}: new since the
build made at "after". Full releases carry no time in their name, so "releases" maps each one
to the time it was built.
"""
import datetime
import json
import re
import sys

WHATS_NEW = "app/src/main/assets/fork/whats_new.json"
RELEASE_BRANCH = "flux-release"
SECTIONS = [("highlights", "Highlights"), ("improvements", "Everything else"), ("bugFixes", "Fixed"),
            ("upstream", "From Pastiera")]


def is_release_branch(branch):
    return branch.lower() == RELEASE_BRANCH.lower()


def load():
    with open(WHATS_NEW, encoding="utf-8") as f:
        return json.load(f)


def parse(version):
    """0.92 -> (0, 92, 0); 0.94.1 -> (0, 94, 1); 0.93-flux.202610011200 -> (0, 93, 0)."""
    m = re.fullmatch(r"(\d+)\.(\d+)(?:\.(\d+))?(?:-flux\.(\d{12}))?", version)
    if not m:
        sys.exit(f"Not a Flux Keyboard version: {version}")
    return int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)


def is_dev(version):
    return "-flux." in version


def code(version):
    # 0.94 -> 94 (as before), 0.94.1 -> 9401, 0.95 -> 9500: every build is an upgrade of the last
    major, minor, patch = parse(version)
    if (major, minor) <= (0, 94) and patch == 0:
        return major * 100 + minor
    return major * 10000 + minor * 100 + patch


def newest_release(data, below=None):
    versions = [v for v in data.get("releases", {}) if below is None or parse(v) < parse(below)]
    return max(versions, key=parse, default=None)


def version_cmd(branch, given):
    data = load()
    release = newest_release(data)
    if given:
        name = given
        parse(name)
        if is_release_branch(branch) and is_dev(name):
            sys.exit(f"{RELEASE_BRANCH} builds full releases, not the dev build {name}")
        if not is_release_branch(branch) and not is_dev(name):
            sys.exit(f"{branch} builds dev builds (x.yy-flux.<time>); full releases come from {RELEASE_BRANCH}")
        if is_dev(name) and release and parse(name) <= parse(release):
            # 0.92-flux.<time> counts as older than 0.92, so it would never be offered as an update
            sys.exit(f"Dev builds after {release} need a newer version than it, like {parse(release)[0]}.{parse(release)[1]}.{parse(release)[2] + 1}-flux.<time>")
    elif is_release_branch(branch):
        if release is None:
            sys.exit(f"No releases in {WHATS_NEW}")
        name = release
    else:
        # Named after the next patch (after 0.94.1: 0.94.2-flux.<time>), so whichever full release
        # comes next (0.94.2 or 0.95) is newer than every dev build before it, and installs over it
        major, minor, patch = parse(release) if release else (0, 90, 0)
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M")
        name = f"{major}.{minor}.{patch + 1}-flux.{stamp}"
    if not is_dev(name) and name not in data.get("releases", {}):
        sys.exit(f'Add "{name}": "<yyyyMMddHHmm>" to "releases" in {WHATS_NEW} before releasing it')
    print(f"kind={'dev' if is_dev(name) else 'release'}")
    print(f"name={name}")
    print(f"code={code(name)}")


def stamp_of(data, version):
    if is_dev(version):
        return int(version.rsplit(".", 1)[1])
    stamp = data.get("releases", {}).get(version)
    return int(stamp) if stamp else None


def notes_cmd(version, previous_tag, commit):
    data = load()
    previous = previous_tag.removeprefix("flux/v") if previous_tag else None
    if not is_dev(version):
        # A full release lists everything since the full release before it
        previous = newest_release(data, below=version)
    since = stamp_of(data, previous) if previous else None

    out = []
    kind = "Dev build" if is_dev(version) else "Release"
    out.append(f"{kind} `{version}` · built from {commit[:7]} · the APK is under **Assets** below.")
    out.append("")
    if since is None:
        out.append("## What's new")
    else:
        what = "dev build" if is_dev(previous) else "release"
        out.append(f"## Changes since {previous} ({what})")
    any_entry = False
    for key, title in SECTIONS:
        entries = []
        for entry in data.get(key, []):
            if isinstance(entry, dict):
                text, after = entry.get("text", ""), int(entry.get("after", "0") or 0)
            else:
                text, after = entry, 0
            if text and (since is None or after >= since):
                entries.append(text)
        if entries:
            any_entry = True
            out.append("")
            out.append(f"### {title}")
            out.extend(f"- {text}" for text in entries)
    if not any_entry:
        out.append("")
        out.append("Behind-the-scenes changes only.")
    # A patch release (0.94.1): its own changes above, then what its release (0.94) brought
    major, minor, patch = parse(version)
    base = f"{major}.{minor}"
    if not is_dev(version) and patch > 0 and base in data.get("releases", {}):
        before = newest_release(data, below=base)
        start = stamp_of(data, before) if before else None
        end = stamp_of(data, base)
        out.append("")
        out.append(f"## What's new in {base}")
        for key, title in SECTIONS:
            entries = []
            for entry in data.get(key, []):
                if not isinstance(entry, dict):
                    continue
                after = int(entry.get("after", "0") or 0)
                if entry.get("text") and (start is None or after >= start) and after < end:
                    entries.append(entry["text"])
            if entries:
                out.append("")
                out.append(f"### {title}")
                out.extend(f"- {text}" for text in entries)
    print("\n".join(out))


if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["version"] and len(args) in (2, 3):
        version_cmd(args[1], args[2] if len(args) == 3 else "")
    elif args[:1] == ["notes"] and len(args) == 4:
        notes_cmd(args[1], args[2], args[3])
    else:
        sys.exit(__doc__)
