package it.palsoftware.pastiera.update

import it.palsoftware.pastiera.BuildConfig

/** Compare numeric versions and SemVer prereleases; unknown formats never suggest an update. */
internal fun compareReleaseVersions(first: String, second: String): Int? {
    val pattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
    val a = pattern.matchEntire(normalizeReleaseVersion(first)) ?: return null
    val b = pattern.matchEntire(normalizeReleaseVersion(second)) ?: return null
    val av = a.groupValues[1].split('.').map(String::toBigInteger)
    val bv = b.groupValues[1].split('.').map(String::toBigInteger)
    for (i in 0 until maxOf(av.size, bv.size)) {
        val order = (av.getOrNull(i) ?: java.math.BigInteger.ZERO).compareTo(bv.getOrNull(i) ?: java.math.BigInteger.ZERO)
        if (order != 0) return order
    }
    val ap = a.groupValues[2]
    val bp = b.groupValues[2]
    if (ap == bp) return 0
    if (ap.isEmpty()) return 1
    if (bp.isEmpty()) return -1
    val ai = ap.split('.')
    val bi = bp.split('.')
    for (i in 0 until minOf(ai.size, bi.size)) {
        val an = ai[i].toBigIntegerOrNull()
        val bn = bi[i].toBigIntegerOrNull()
        val order = when {
            an != null && bn != null -> an.compareTo(bn)
            an != null -> -1
            bn != null -> 1
            else -> ai[i].compareTo(bi[i])
        }
        if (order != 0) return order
    }
    return ai.size.compareTo(bi.size)
}

/** Whether a Flux Keyboard release tag ("flux/v…") is newer than the [current] version name. */
internal fun forkReleaseIsNewer(tag: String, current: String): Boolean =
    tag.startsWith("flux/") && (compareReleaseVersions(tag.removePrefix("flux/"), current) ?: -1) > 0

/** A dev build's release (flux/v0.92-flux.<time>), as opposed to a full release (flux/v0.91). */
internal fun isForkDevRelease(tagName: String): Boolean = tagName.contains("-flux.")

/**
 * What the update check reads: the fork's release tags, all of them. A list of releases comes
 * ordered by commit date and a page at a time, so a full release whose folded commits keep
 * their old dates could sit below a page of dev builds; the tags don't depend on either.
 */
internal fun forkReleasesApiUrl(): String =
    "https://api.github.com/repos/${BuildConfig.FORK_GITHUB_REPOSITORY}/git/matching-refs/tags/flux/v"

/**
 * The releases among the fork's tags ("refs/tags/flux/v0.92"), dev builds' too: the build
 * publishes each one's APK as flux-keyboard-<version>.apk, so its page and download follow
 * from the tag.
 */
internal fun forkReleasesFromTags(refs: List<String>): List<GitHubRelease> =
    refs.map { it.removePrefix("refs/tags/") }
        .filter { it.startsWith("flux/v") }
        .map { tag ->
            val version = tag.removePrefix("flux/v")
            val base = "https://github.com/${BuildConfig.FORK_GITHUB_REPOSITORY}/releases"
            GitHubRelease(
                tagName = tag,
                // "Flux Keyboard 0.93 dev · 26 Sep 2026, 20:47", as the release is titled
                name = "Flux Keyboard " + if (isForkDevRelease(tag)) friendlyVersion(version).replaceFirst(" · ", " dev · ") else version,
                prerelease = false,
                draft = false,
                htmlUrl = "$base/tag/$tag",
                downloadUrl = "$base/download/$tag/flux-keyboard-$version.apk"
            )
        }

/** The newest release newer than [current]; dev builds only when [includeDev] (the Dev channel). */
internal fun findNewerForkRelease(releases: List<GitHubRelease>, current: String, includeDev: Boolean = true): ReleaseInfo? =
    releases.filter { !it.draft && (includeDev || !isForkDevRelease(it.tagName)) && forkReleaseIsNewer(it.tagName, current) }
        .maxWithOrNull { a, b -> compareReleaseVersions(a.tagName.removePrefix("flux/"), b.tagName.removePrefix("flux/")) ?: 0 }
        ?.let { ReleaseInfo(it.tagName, it.name ?: it.tagName, it.htmlUrl, it.downloadUrl) }

internal fun findNewerNightlyRelease(releases: List<GitHubRelease>, current: String): ReleaseInfo? =
    releases.filter { !it.draft && it.prerelease && it.tagName.startsWith("nightly/") &&
        (compareReleaseVersions(it.tagName, current) ?: -1) > 0 }
        .maxWithOrNull { a, b -> compareReleaseVersions(a.tagName, b.tagName) ?: 0 }
        ?.let { ReleaseInfo(it.tagName, it.name ?: it.tagName, it.htmlUrl, it.downloadUrl) }
