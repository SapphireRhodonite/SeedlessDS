package com.seedlessds.app.emu

import android.content.Context
import android.util.LruCache
import coil.request.ImageRequest
import com.seedlessds.app.AppLog
import com.seedlessds.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

object BoxArt {
    private const val TAG = "BoxArt"
    private const val MAX_ROM_BYTES = 512L * 1024 * 1024
    private val indexLock = Mutex()
    private val fileReaders = Semaphore(2)
    private val matches = LruCache<RomLibrary.RomEntry, BoxArtCatalog.Cover>(512)

    @Volatile private var index: BoxArtCatalogs? = null
    @Volatile private var indexAttempted = false
    @Volatile private var legacyCache: LegacyBoxArtCache? = null

    suspend fun ensureIndex(ctx: Context) {
        if (indexAttempted || !isEnabled(ctx)) return
        indexLock.withLock {
            if (indexAttempted) return
            withContext(Dispatchers.IO) {
                index = try {
                    val json = ctx.assets.open(BoxArtCatalogs.ASSET).bufferedReader().use { JSONObject(it.readText()) }
                    BoxArtCatalogs.parse(json).also {
                        AppLog.i(TAG, "Using box art index DS ${it.nds.revision}: ${it.nds.size}, DSiWare ${it.dsiWare.revision}: ${it.dsiWare.size}")
                    }
                } catch (error: IOException) {
                    AppLog.i(TAG, "Box art index unavailable: ${error.message}")
                    null
                } catch (error: JSONException) {
                    AppLog.i(TAG, "Invalid box art index: ${error.message}")
                    null
                } catch (error: IllegalArgumentException) {
                    AppLog.i(TAG, "Invalid box art index: ${error.message}")
                    null
                }
                if (index != null) legacyCache = LegacyBoxArtCache(cacheDir(ctx))
                indexAttempted = true
            }
        }
    }

    suspend fun resolveUrl(ctx: Context, entry: RomLibrary.RomEntry): String? {
        if (!isEnabled(ctx)) return null
        ensureIndex(ctx)
        val catalog = index ?: return null
        val cacheable = (entry.size ?: 0L) > 0 && (entry.modified ?: 0L) > 0
        if (cacheable) matches.get(entry)?.let { return it.url }

        return withContext(Dispatchers.IO) {
            fileReaders.withPermit {
                if (cacheable) matches.get(entry)?.let { return@withPermit it.url }
                val cover = try {
                    when (entry.name.substringAfterLast('.').lowercase()) {
                        "nds", "srl" -> ctx.contentResolver.openInputStream(entry.uri)?.use {
                            resolveCover(it, catalog, coroutineContext)
                        }
                        "zip" -> CartridgeId.withZipRom(ctx, entry) { open ->
                            open().use { resolveCover(it, catalog, coroutineContext) }
                        }
                        "7z", "rar" -> CartridgeId.read(ctx, entry)?.let {
                            catalog.forUnit(it.unitCode)?.fromHeader(it)
                        }
                        else -> null
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
                if (cover != null) {
                    if (legacyCache?.reuse(ctx, entry.uri.toString(), cover) == true) {
                        AppLog.i(TAG, "Reused verified legacy artwork for ${cover.variant}")
                    }
                    if (cacheable) matches.put(entry, cover)
                    AppLog.i(TAG, "Selected ${cover.variant}: ${cover.url}")
                }
                cover?.url
            }
        }
    }

    private fun resolveCover(
        input: InputStream,
        catalogs: BoxArtCatalogs,
        context: CoroutineContext,
    ): BoxArtCatalog.Cover? {
        val header = ByteArray(32)
        DataInputStream(input).readFully(header)
        val id = CartridgeId.fromHeader(header)
        val catalog = catalogs.forUnit(id.unitCode) ?: return null
        catalog.fromHeader(id)?.let { return it }
        if (!catalog.hasCode(id)) return null

        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(header)
        val buffer = ByteArray(128 * 1024)
        var bytes = header.size.toLong()
        while (true) {
            context.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            bytes += count
            if (bytes > MAX_ROM_BYTES) return null
            digest.update(buffer, 0, count)
        }
        return catalog.fromSha1(id, BigInteger(1, digest.digest()).toString(16).padStart(40, '0'))
    }

    fun imageRequest(ctx: Context, url: String): ImageRequest.Builder =
        ImageRequest.Builder(ctx).data(url).apply {
            if (url.startsWith("https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_")) {
                setHeader("Cache-Control", "max-stale=${Int.MAX_VALUE}")
            }
        }

    fun raIconUrl(ctx: Context, entry: RomLibrary.RomEntry): String? = RomLibrary.raIconUrl(ctx, entry)

    private fun cacheDir(ctx: Context): File = File(ctx.filesDir, "boxart")

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
        legacyCache = null
        matches.evictAll()
    }

    fun formatSize(uiContext: android.content.Context, bytes: Long): String = when {
        bytes <= 0L -> uiContext.getString(R.string.common_empty).lowercase()
        bytes < 1024L * 1024L -> uiContext.getString(R.string.state_kb, bytes / 1024L)
        else -> uiContext.getString(R.string.common_mb, bytes / (1024L * 1024L))
    }
}
