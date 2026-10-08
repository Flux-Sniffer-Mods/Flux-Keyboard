package it.palsoftware.pastiera.data.gif

import it.palsoftware.pastiera.core.writeBytesAtomically
import android.content.ClipDescription
import android.content.Context
import it.palsoftware.pastiera.OfflineMode
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One GIF: a small preview for the grid and the full GIF that gets sent. */
data class GifResult(
    val id: String,
    val description: String,
    val previewUrl: String,
    val gifUrl: String,
    val previewWidth: Int,
    val previewHeight: Int
)

/** A search that failed; [message] says how (shown to the user). */
class GifSearchException(message: String) : IOException(message)

/**
 * GIF search through KLIPY (https://klipy.com). Google shut the Tenor API down on 30 June 2026.
 * KLIPY's own API ([NATIVE_URL]) comes first: it offers WebP previews about a quarter the size
 * of GIFs. Its Tenor v2-compatible endpoints ([BASE_URL]) are the fallback. Requests need an
 * API key (free at [SIGNUP_URL]) and are only made while GIF search is open.
 *
 * Results (with their titles) are cached on the phone: featured for [FEATURED_MAX_AGE_MS],
 * searches for [SEARCH_MAX_AGE_MS], and used past that when the network fails. Previews are
 * cached on disk too (up to [PREVIEW_CACHE_MAX_BYTES], least recently used removed first).
 * GIF search shows anything cached at once ([cachedResults]) and refreshes it behind; featured
 * GIFs and their first previews are fetched ahead when the emoji layer or picker opens
 * ([prefetchFeatured]), at most once per [FEATURED_MAX_AGE_MS].
 */
object KlipyGifs {
    const val BASE_URL = "https://api.klipy.com/v2"
    const val NATIVE_URL = "https://api.klipy.com/api/v1"
    const val SIGNUP_URL = "https://partner.klipy.com"
    const val CACHE_FOLDER = "gifs"
    private const val PAGE_SIZE = 30
    private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    const val FEATURED_MAX_AGE_MS = 3L * 60 * 60 * 1000
    const val SEARCH_MAX_AGE_MS = 24L * 60 * 60 * 1000
    const val PREVIEW_CACHE_MAX_BYTES = 40L * 1024 * 1024
    private const val RESULTS_FOLDER = "gif-results"
    private const val RESULTS_KEEP_MS = 7L * 24 * 60 * 60 * 1000
    private const val RESULTS_MAX_FILES = 200
    private const val PREVIEWS_FOLDER = "gif-previews"

    private val client by lazy {
        OkHttpClient.Builder()
            // Previews all come from one host: let a grid's worth load side by side (default 5)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Preview bytes by URL; each grid cell decodes its own animation from them
    private val previewCache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** Words that search for something else. */
    private val ALIASES = mapOf("cowsad" to "diddy kong pointing at an unknown object with his hat on backwards")

    /** Words that answer with one GIF only (its slug), over and over. */
    private val PINNED = mapOf("cowsad" to "diddy-kong-pointing-at-an-unknown-object-with-his-hat-on-backwards")
    private const val PINNED_REPEATS = 300

    private fun pinned(query: String?, results: List<GifResult>): List<GifResult> {
        val slug = PINNED[query?.trim()?.lowercase()] ?: return results
        val words = slug.split('-').filter { it.length > 3 }
        val one = results.firstOrNull { it.id == slug } ?: results.maxByOrNull { result ->
            words.count { it in result.description.lowercase() }
        } ?: return results
        return List(PINNED_REPEATS) { one.copy(id = "${one.id}#$it") }
    }

    internal fun searchTerm(query: String): String =
        query.trim().let { ALIASES[it.lowercase()] ?: it }

    /** Featured GIFs for a blank [query], search results otherwise. */
    fun requestUrl(apiKey: String, query: String?, locale: Locale = Locale.getDefault(), limit: Int = PAGE_SIZE): HttpUrl {
        val search = !query.isNullOrBlank()
        return BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment(if (search) "search" else "featured")
            .apply { if (search) addQueryParameter("q", searchTerm(query!!)) }
            .addQueryParameter("key", apiKey.trim())
            .addQueryParameter("client_key", "pastiera")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("media_filter", "tinygif,gif")
            .addQueryParameter("contentfilter", "medium")
            .addQueryParameter("locale", locale.toString())
            .apply { locale.country.takeIf { it.isNotBlank() }?.let { addQueryParameter("country", it) } }
            .build()
    }

    /** Parses a v2 response; entries without an https GIF are skipped. */
    fun parse(json: String): List<GifResult> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val formats = item.optJSONObject("media_formats") ?: return@mapNotNull null
            val gif = formats.optJSONObject("gif") ?: formats.optJSONObject("mediumgif") ?: return@mapNotNull null
            val gifUrl = gif.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val preview = formats.optJSONObject("tinygif") ?: formats.optJSONObject("nanogif") ?: gif
            val previewUrl = preview.optString("url").takeIf { it.startsWith("https://") } ?: gifUrl
            val dims = preview.optJSONArray("dims")
            GifResult(
                id = item.optString("id"),
                description = item.optString("content_description"),
                previewUrl = previewUrl,
                gifUrl = gifUrl,
                previewWidth = dims?.optInt(0) ?: 0,
                previewHeight = dims?.optInt(1) ?: 0
            )
        }
    }

    /** KLIPY's own API: trending for a blank [query], search otherwise. */
    fun nativeUrl(apiKey: String, query: String?, customerId: String, locale: Locale = Locale.getDefault()): HttpUrl {
        val search = !query.isNullOrBlank()
        return NATIVE_URL.toHttpUrl().newBuilder()
            .addPathSegment(apiKey.trim())
            .addPathSegment("gifs")
            .addPathSegment(if (search) "search" else "trending")
            .apply { if (search) addQueryParameter("q", searchTerm(query!!)) }
            .addQueryParameter("customer_id", customerId)
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("rating", "pg-13")
            .addQueryParameter("locale", locale.country.ifBlank { locale.language })
            .build()
    }

    /**
     * KLIPY's own response: {"result": true, "data": {"data": [...]}}, each item with "file"
     * (or "files") holding sizes hd/md/sm/xs, each with formats gif/webp/mp4. Ads are skipped.
     */
    fun parseNative(json: String): List<GifResult> {
        val root = JSONObject(json)
        val items = root.optJSONObject("data")?.optJSONArray("data") ?: root.optJSONArray("data") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") == "ad") return@mapNotNull null
            val files = item.optJSONObject("file") ?: item.optJSONObject("files") ?: return@mapNotNull null
            fun format(size: String, type: String): JSONObject? =
                files.optJSONObject(size)?.optJSONObject(type)?.takeIf { it.optString("url").startsWith("https://") }
            val gif = format("md", "gif") ?: format("sm", "gif") ?: format("hd", "gif") ?: return@mapNotNull null
            // Previews: animated WebP (about a quarter of a GIF's size), then GIF
            val preview = format("sm", "webp") ?: format("xs", "webp") ?: format("sm", "gif") ?: format("xs", "gif") ?: gif
            GifResult(
                id = item.optString("id").ifBlank { item.optString("slug") },
                description = item.optString("title"),
                previewUrl = preview.optString("url"),
                gifUrl = gif.optString("url"),
                previewWidth = preview.optInt("width"),
                previewHeight = preview.optInt("height")
            )
        }
    }

    /**
     * GIFs for [query] (featured when blank): from the phone's cache while fresh, else from
     * KLIPY (its own API, then the Tenor-compatible one), else a stale cached answer when the
     * network fails. Throws [GifSearchException] saying what each attempt returned.
     */
    suspend fun find(context: Context, apiKey: String, query: String?): List<GifResult> = withContext(Dispatchers.IO) {
        if (OfflineMode.enabled) throw GifSearchException("offline mode")
        val file = resultsFile(context, query)
        readResults(file, maxAge(query))?.let { return@withContext pinned(query, it) }
        try {
            pinned(query, fetchResults(context, apiKey, query).also { writeResults(file, it) })
        } catch (e: GifSearchException) {
            readResults(file, Long.MAX_VALUE)?.takeIf { it.isNotEmpty() }?.let { pinned(query, it) } ?: throw e
        }
    }

    /** Where results for [query] are cached. */
    fun resultsFile(context: Context, query: String?): File {
        val folder = File(context.cacheDir, RESULTS_FOLDER).apply { mkdirs() }
        return File(folder, resultsCacheKey(query, Locale.getDefault()) + ".json")
    }

    private fun maxAge(query: String?): Long = if (query.isNullOrBlank()) FEATURED_MAX_AGE_MS else SEARCH_MAX_AGE_MS

    /**
     * Whatever is cached for [query], however old, and whether it's still fresh (then there's no
     * need to ask KLIPY again); null if nothing is cached. Reads a small file.
     */
    fun cachedResults(context: Context, query: String?): Pair<List<GifResult>, Boolean>? {
        val file = resultsFile(context, query)
        val results = readResults(file, Long.MAX_VALUE) ?: return null
        return pinned(query, results) to (System.currentTimeMillis() - file.lastModified() <= maxAge(query))
    }

    /** Stores [results] for [query] as a fresh answer. */
    fun cacheResults(context: Context, query: String?, results: List<GifResult>) {
        writeResults(resultsFile(context, query), results)
    }

    const val PREFETCH_PREVIEWS = 12
    private const val PREFETCH_RETRY_MS = 10L * 60 * 1000
    // Let the page that asked for it open first
    private const val PREFETCH_DELAY_MS = 1500L
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetching = AtomicBoolean(false)
    @Volatile
    private var lastPrefetchAttempt = 0L

    /**
     * Fetches featured GIFs and their first [PREFETCH_PREVIEWS] previews in the background, so
     * GIF search opens with them at once. Asks KLIPY only when the cached featured list is older
     * than [FEATURED_MAX_AGE_MS]; tries at most once per 10 minutes. Cheap to call often.
     */
    fun prefetchFeatured(context: Context, apiKey: String) {
        if (apiKey.isBlank() || OfflineMode.enabled) return
        val now = System.currentTimeMillis()
        if (now - lastPrefetchAttempt < PREFETCH_RETRY_MS) return
        if (!prefetching.compareAndSet(false, true)) return
        lastPrefetchAttempt = now
        val appContext = context.applicationContext
        prefetchScope.launch {
            try {
                delay(PREFETCH_DELAY_MS)
                val started = SystemClock.elapsedRealtime()
                val cached = cachedResults(appContext, null)?.takeIf { it.second }?.first
                val results = cached
                    ?: runCatching { find(appContext, apiKey, null) }.getOrNull()
                    ?: return@launch
                results.take(PREFETCH_PREVIEWS).forEach { gif ->
                    runCatching { previewBytes(appContext, gif.previewUrl) }
                }
                Log.i(
                    "FluxSearch",
                    "gif prefetch: ${results.size} featured (${if (cached != null) "cached" else "fetched"}), " +
                        "${minOf(PREFETCH_PREVIEWS, results.size)} previews, ${SystemClock.elapsedRealtime() - started} ms"
                )
            } finally {
                prefetching.set(false)
            }
        }
    }

    private suspend fun fetchResults(context: Context, apiKey: String, query: String?): List<GifResult> {
        val attempts = mutableListOf<String>()
        val native = fetch(context, nativeUrl(apiKey, query, customerId(context)))
        if (native.first in 200..299) {
            val results = runCatching { parseNative(native.second) }.getOrNull()
            if (results != null) return results
            attempts += "api: unexpected response"
        } else {
            attempts += "api: HTTP ${native.first}"
        }
        val compat = fetch(context, requestUrl(apiKey, query))
        if (compat.first in 200..299) {
            val body = compat.second
            val results = runCatching { parse(body) }.getOrDefault(emptyList())
            if (results.isNotEmpty() || runCatching { JSONObject(body).has("results") }.getOrDefault(false)) {
                return results
            }
            attempts += "v2: unexpected response"
        } else {
            attempts += "v2: HTTP ${compat.first}"
        }
        throw GifSearchException(attempts.joinToString(", "))
    }

    /** One cache file per query and locale (case and outer spaces don't matter). */
    fun resultsCacheKey(query: String?, locale: Locale): String =
        sha1("${locale}|${query.orEmpty().trim().lowercase(Locale.ROOT)}")

    /** Cached results no older than [maxAgeMs], or null. */
    fun readResults(file: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()): List<GifResult>? {
        if (!file.isFile || now - file.lastModified() > maxAgeMs) return null
        return runCatching { resultsFromJson(file.readText()) }.getOrNull()
    }

    private fun writeResults(file: File, results: List<GifResult>) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(resultsToJson(results))
            temp.renameTo(file)
            file.parentFile?.let { trimResults(it) }
        }
    }

    /**
     * Every search keeps its answer in a file of its own: those unused for [maxAgeMs] go, and
     * beyond [maxFiles] the least recently used, so the folder doesn't grow with every search.
     */
    fun trimResults(
        folder: File,
        maxAgeMs: Long = RESULTS_KEEP_MS,
        maxFiles: Int = RESULTS_MAX_FILES,
        now: Long = System.currentTimeMillis()
    ) {
        val files = folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        files.forEachIndexed { index, file ->
            if (index >= maxFiles || now - file.lastModified() > maxAgeMs) file.delete()
        }
    }

    fun resultsToJson(results: List<GifResult>): String = JSONArray().apply {
        results.forEach { gif ->
            put(
                JSONObject()
                    .put("id", gif.id)
                    .put("description", gif.description)
                    .put("preview", gif.previewUrl)
                    .put("gif", gif.gifUrl)
                    .put("w", gif.previewWidth)
                    .put("h", gif.previewHeight)
            )
        }
    }.toString()

    fun resultsFromJson(json: String): List<GifResult> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            GifResult(
                id = item.optString("id"),
                description = item.optString("description"),
                previewUrl = item.optString("preview").takeIf { it.startsWith("https://") } ?: return@mapNotNull null,
                gifUrl = item.optString("gif").takeIf { it.startsWith("https://") } ?: return@mapNotNull null,
                previewWidth = item.optInt("w"),
                previewHeight = item.optInt("h")
            )
        }
    }

    /** A preview's bytes: from memory, the disk cache, or downloaded (then kept on disk). */
    suspend fun previewBytes(context: Context, url: String): ByteArray = withContext(Dispatchers.IO) {
        previewCache.get(url)?.let { return@withContext it }
        val folder = File(context.cacheDir, PREVIEWS_FOLDER).apply { mkdirs() }
        val file = File(folder, sha1(url))
        val bytes = if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis()) // recently used
            file.readBytes()
        } else {
            download(context, url).also { downloaded ->
                runCatching {
                    file.writeBytesAtomically(downloaded)
                    trimPreviews(folder, PREVIEW_CACHE_MAX_BYTES)
                }
            }
        }
        previewCache.put(url, bytes)
        bytes
    }

    /** Removes the least recently used previews until the folder fits in [maxBytes]. */
    fun trimPreviews(folder: File, maxBytes: Long) {
        val files = folder.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** (HTTP code, body); code 0 when the network request itself failed. */
    private suspend fun fetch(context: Context, url: HttpUrl): Pair<Int, String> = try {
        call(context, url.toString()) { response -> response.code to response.body?.string().orEmpty() }
    } catch (e: IOException) {
        0 to (e.message ?: "network error")
    }

    /**
     * Runs a request on OkHttp's threads and reads the response with [read]. Cancelling the
     * coroutine (the next keystroke, a preview scrolled away) cancels the request, rather than
     * letting it finish unseen and hold up the ones that matter.
     */
    private suspend fun <T> call(context: Context, url: String, read: (Response) -> T): T {
        // Offline mode: every request stops here (searches, previews, sending)
        if (OfflineMode.enabled) throw IOException("offline mode")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request(context, url))
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use(read) })
                }
            })
        }
    }

    // KLIPY's network requirements ask for a browser-like User-Agent; keys can be tied to an app
    private fun request(context: Context, url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) ${it.palsoftware.pastiera.BuildConfig.APP_NAME.replace(" ", "")}")
        .header("X-Android-Package", context.packageName)
        .build()

    /** A random ID for this install (KLIPY's per-user parameter); nothing personal. */
    private fun customerId(context: Context): String {
        val prefs = context.getSharedPreferences("gif_search", Context.MODE_PRIVATE)
        return prefs.getString("customer_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("customer_id", it).apply()
        }
    }

    /** The full GIF in the cache folder the FileProvider shares; older files are removed. */
    suspend fun downloadToCache(context: Context, gif: GifResult): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, CACHE_FOLDER).apply { mkdirs() }
        val now = System.currentTimeMillis()
        folder.listFiles()?.filter { now - it.lastModified() > CACHE_MAX_AGE_MS }?.forEach { it.delete() }
        val name = gif.id.filter { it.isLetterOrDigit() }.ifEmpty { gif.gifUrl.hashCode().toUInt().toString() }
        File(folder, "gif-$name.gif").also { file ->
            if (!file.exists() || file.length() == 0L) file.writeBytesAtomically(download(context, gif.gifUrl))
        }
    }

    /** Whether a field accepting these MIME types takes GIFs as content (not just text). */
    fun editorAcceptsGif(mimeTypes: Array<String>): Boolean =
        mimeTypes.any { ClipDescription.compareMimeTypes("image/gif", it) }

    private suspend fun download(context: Context, url: String): ByteArray =
        call(context, url) { response ->
            if (!response.isSuccessful) throw IOException("GIF HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty GIF")
        }
}
