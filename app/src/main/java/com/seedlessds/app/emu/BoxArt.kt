package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BoxArt {

    private const val TAG = "BoxArt"
    private const val BASE = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/"
    private const val NO_MATCH = "-"
    private val INDEX_TTL_MS = TimeUnit.DAYS.toMillis(30)

    private const val BUNDLED_INDEX = "boxart_index.txt"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val indexLock = Mutex()

    suspend fun ensureIndex(ctx: Context) {
        if (index != null || indexFailed || !isEnabled(ctx)) return
        indexLock.withLock {
            if (index != null || indexFailed) return
            withContext(Dispatchers.IO) { loadIndex(ctx) }
        }
    }

    suspend fun refreshIndexIfStale(ctx: Context) {
        if (!isEnabled(ctx)) return
        val file = File(cacheDir(ctx), "index.txt")
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < INDEX_TTL_MS) return
        indexLock.withLock {
            withContext(Dispatchers.IO) {
                val fetched = fetchIndex() ?: return@withContext
                if (fetched.isEmpty()) return@withContext
                runCatching { file.writeText(fetched.joinToString("\n")) }
                index = fetched.groupBy(::normalise)
                indexFailed = false
                com.seedlessds.app.AppLog.i(TAG, "box art index refreshed: ${fetched.size} covers")
                lock.withLock {
                    val store = loadMatches(ctx)
                    if (store.values.any { it == NO_MATCH }) {
                        store.entries.removeAll { it.value == NO_MATCH }
                        persistMatches(ctx, store)
                    }
                }
            }
        }
    }

    private val lock = Mutex()

    @Volatile private var index: Map<String, List<String>>? = null
    @Volatile private var indexFailed = false

    private var matches: MutableMap<String, String>? = null

    fun cacheDir(ctx: Context): File = File(ctx.filesDir, "boxart").apply { mkdirs() }

    suspend fun resolveUrl(ctx: Context, entry: RomLibrary.RomEntry): String? {
        if (!isEnabled(ctx)) return null
        val key = entry.uri.toString()

        withContext(Dispatchers.IO) { lock.withLock { loadMatches(ctx) } }[key]?.let {
            return if (it == NO_MATCH) null else BASE + encode(it)
        }

        ensureIndex(ctx)
        return withContext(Dispatchers.IO) {
            lock.withLock {
                val store = loadMatches(ctx)
                store[key]?.let { return@withLock if (it == NO_MATCH) null else BASE + encode(it) }

                val name = resolveName(ctx, entry.title)
                if (name == null && index == null) return@withLock null
                store[key] = name ?: NO_MATCH
                appendMatch(ctx, key, name ?: NO_MATCH)
                name?.let { BASE + encode(it) }
            }
        }
    }

    fun raIconUrl(ctx: Context, entry: RomLibrary.RomEntry): String? = RomLibrary.raIconUrl(ctx, entry)

    private fun encode(name: String): String =
        URLEncoder.encode(name, "UTF-8").replace("+", "%20") + ".png"

    private fun loadMatches(ctx: Context): MutableMap<String, String> {
        matches?.let { return it }
        val file = File(cacheDir(ctx), "matches.txt")
        val loaded = HashMap<String, String>()
        var lines = 0
        runCatching {
            if (file.isFile) file.forEachLine { line ->
                val i = line.indexOf('\t')
                if (i > 0) {
                    loaded[line.substring(0, i)] = line.substring(i + 1)
                    lines++
                }
            }
        }
        matches = loaded
        if (loaded.isNotEmpty() && lines > loaded.size * 2) persistMatches(ctx, loaded)
        return loaded
    }

    private fun appendMatch(ctx: Context, key: String, value: String) {
        runCatching {
            File(cacheDir(ctx), "matches.txt").appendText(key + "\t" + value + "\n")
        }
    }

    private fun persistMatches(ctx: Context, store: Map<String, String>) {
        runCatching {
            File(cacheDir(ctx), "matches.txt")
                .writeText(store.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        }
    }

    private fun resolveName(ctx: Context, title: String): String? {
        val idx = loadIndex(ctx) ?: return null
        val q = normalise(title)
        if (q.length < 3) return null

        idx[q]?.let { return it.minByOrNull(::rank) }
        if (q.length >= 8) {
            val prefixed = idx.keys.filter { it.startsWith(q) }
            if (prefixed.isNotEmpty()) return prefixed.flatMap { idx.getValue(it) }.minByOrNull(::rank)
        }
        if (q.length >= 10) {
            val contained = idx.keys.filter { it.length >= 10 && (it in q || q in it) }
            if (contained.isNotEmpty()) return contained.flatMap { idx.getValue(it) }.minByOrNull(::rank)
        }
        return null
    }

    private fun rank(name: String): Int {
        val low = name.lowercase()
        var score = 0
        if (BAD_TAGS.any { it in low }) score += 1000
        val region = REGION_ORDER.indexOfFirst { "($it" in low }
        score += (if (region < 0) REGION_ORDER.size else region) * 20
        score += name.length
        return score
    }

    private val BAD_TAGS = listOf("(demo", "(kiosk", "(beta", "(proto", "(sample", "(aftermarket", "(pirate")
    private val REGION_ORDER = listOf("usa", "world", "europe", "australia", "japan")

    private fun normalise(title: String): String =
        title.lowercase()
            .replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
            .replace(Regex("""[^a-z0-9]+"""), "")

    private fun loadIndex(ctx: Context): Map<String, List<String>>? {
        index?.let { return it }
        if (indexFailed) return null

        val file = File(cacheDir(ctx), "index.txt")
        var names = runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        if (names.isEmpty()) {
            names = runCatching {
                ctx.assets.open(BUNDLED_INDEX).bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.toList()
                }
            }.getOrDefault(emptyList())
            if (names.isNotEmpty()) com.seedlessds.app.AppLog.i(TAG, "using the box art index bundled with the app")
        }

        if (names.isEmpty()) {
            indexFailed = true
            return null
        }
        val built = names.groupBy(::normalise)
        index = built
        com.seedlessds.app.AppLog.i(TAG, "box art index: ${names.size} covers, ${built.size} distinct titles")
        return built
    }

    private fun fetchIndex(): List<String>? {
        com.seedlessds.app.AppLog.i(TAG, "fetching the box art index")
        return runCatching {
            client.newCall(Request.Builder().url(BASE).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    com.seedlessds.app.AppLog.i(TAG, "index request returned HTTP ${response.code}")
                    return@use null
                }
                val html = response.body?.string() ?: return@use null
                HREF.findAll(html)
                    .map { URLDecoder.decode(it.groupValues[1], "UTF-8").removeSuffix(".png") }
                    .filter { it.isNotBlank() }
                    .toList()
            }
        }.getOrElse {
            com.seedlessds.app.AppLog.i(TAG, "could not fetch the box art index: $it")
            null
        }
    }

    private val HREF = Regex("""<a href="([^"?/][^"]*\.png)"""")

    private const val PREFS = "rom_library"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean("boxart_enabled", true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("boxart_enabled", enabled).apply()
    }

    fun cacheSizeBytes(ctx: Context): Long =
        (cacheDir(ctx).listFiles()?.sumOf { it.length() } ?: 0L) + imageCacheSizeBytes(ctx)

    private fun imageCacheSizeBytes(ctx: Context): Long =
        File(ctx.cacheDir, "image_cache").walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clearCache(ctx: Context) {
        cacheDir(ctx).listFiles()?.forEach { it.delete() }
        runCatching { File(ctx.cacheDir, "image_cache").deleteRecursively() }
        index = null
        indexFailed = false
        matches = null
    }

    fun formatSize(uiContext: android.content.Context, bytes: Long): String = when {
        bytes <= 0L -> uiContext.getString(R.string.common_empty).lowercase()
        bytes < 1024L * 1024L -> uiContext.getString(R.string.state_kb, bytes / 1024L)
        else -> uiContext.getString(R.string.common_mb, bytes / (1024L * 1024L))
    }
}
