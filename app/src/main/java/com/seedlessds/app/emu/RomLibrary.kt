package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.ConcurrentHashMap

object RomLibrary {
    private const val PREFS = "rom_library"

    enum class Platform { DS, DSI }

    enum class Filter { ALL, FAVORITES, DS, DSIWARE, RETRO_ACHIEVEMENTS }

    enum class Sorting { ALPHABETICALLY, RECENTLY_PLAYED, MOST_PLAYED }

    enum class ViewMode { GRID, LIST }

    data class RomEntry(
        val uri: Uri,
        val name: String,
        val folder: Uri? = null,
    ) {
        val title: String get() = name.substringBeforeLast('.')
    }

    data class VirtualFolder(val uri: Uri, val name: String, val gameCount: Int)

    data class Stats(val playCount: Int = 0, val playTimeMs: Long = 0L, val lastPlayed: Long = 0L)

    private val iconCache = ConcurrentHashMap<String, NdsInfo>()

    fun folders(ctx: Context): List<Uri> =
        ctx.getSharedPreferences(PREFS, 0).getStringSet("folders", emptySet())!!.map { Uri.parse(it) }

    fun addFolder(ctx: Context, tree: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val set = prefs.getStringSet("folders", emptySet())!!.toMutableSet()
        set.add(tree.toString())
        prefs.edit().putStringSet("folders", set).apply()
        com.seedlessds.app.AppLog.i("library", "folder added: $tree")
        com.seedlessds.app.AppLog.start(ctx, SettingsRepo.current.appLogFile)
    }

    fun removeFolder(ctx: Context, tree: Uri) {
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val set = prefs.getStringSet("folders", emptySet())!!.toMutableSet()
        set.remove(tree.toString())
        prefs.edit().putStringSet("folders", set).apply()
        com.seedlessds.app.AppLog.i("library", "folder removed: $tree")
        com.seedlessds.app.AppLog.start(ctx, SettingsRepo.current.appLogFile)
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        val kept = recent(ctx).filter { e -> runCatching { DocumentsContract.getTreeDocumentId(e.uri) }.getOrNull() != treeId }
        prefs.edit().putString("recent", kept.joinToString("\n") { "${it.uri}\t${it.name}" }).apply()
    }

    fun virtualFolders(ctx: Context, games: List<RomEntry>): List<VirtualFolder> {
        val counts = games.groupingBy { it.folder?.toString() }.eachCount()
        return folders(ctx).map { uri ->
            val name = runCatching { DocumentFile.fromTreeUri(ctx, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: ctx.getString(R.string.library_folder)
            VirtualFolder(uri, name, counts[uri.toString()] ?: 0)
        }.sortedBy { it.name.lowercase() }
    }


    fun scan(ctx: Context): List<RomEntry> {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val out = ArrayList<RomEntry>()
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        for (t in folders(ctx)) {
            val children = runCatching {
                DocumentsContract.buildChildDocumentsUriUsingTree(t, DocumentsContract.getTreeDocumentId(t))
            }.getOrNull() ?: continue
            runCatching {
                ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val n = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                        if (n.endsWith("-crash.zip", true)) continue
                        if (n.endsWith(".nds", true) || n.endsWith(".zip", true) || n.endsWith(".7z", true) || n.endsWith(".rar", true))
                            out.add(RomEntry(DocumentsContract.buildDocumentUriUsingTree(t, id), n, t))
                    }
                }
            }
        }
        val result = out.distinctBy { it.uri }.sortedBy { it.name.lowercase() }
        com.seedlessds.app.AppLog.i("library", "scan: ${folders(ctx).size} folders, ${result.size} games, ${android.os.SystemClock.elapsedRealtime() - t0} ms")
        return result
    }

    fun browse(
        ctx: Context,
        games: List<RomEntry>,
        filter: Filter,
        folder: Uri?,
        sorting: Sorting,
        descending: Boolean,
    ): List<RomEntry> {
        val favorites = favorites(ctx)
        val raGames = raGames(ctx)
        var list = games.asSequence()
        if (folder != null && filter == Filter.ALL) {
            list = list.filter { it.folder == folder }
        }
        list = when (filter) {
            Filter.ALL -> list
            Filter.FAVORITES -> list.filter { favorites.contains(it.uri.toString()) }
            Filter.DS -> list.filter { platform(ctx, it) == Platform.DS }
            Filter.DSIWARE -> list.filter { platform(ctx, it) == Platform.DSI }
            Filter.RETRO_ACHIEVEMENTS -> list.filter { raGames.contains(it.uri.toString()) }
        }
        val stats = allStats(ctx)
        val sorted: List<RomEntry> = when (sorting) {
            Sorting.ALPHABETICALLY -> list.sortedBy { it.title.lowercase() }
            Sorting.RECENTLY_PLAYED -> list.sortedByDescending { stats[it.uri.toString()]?.lastPlayed ?: 0L }
            Sorting.MOST_PLAYED -> list.sortedByDescending { stats[it.uri.toString()]?.playTimeMs ?: 0L }
        }.toList()
        return if (descending) sorted.reversed() else sorted
    }

    fun favorites(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, 0).getStringSet("favorites", emptySet())!!

    fun isFavorite(ctx: Context, e: RomEntry) = favorites(ctx).contains(e.uri.toString())

    fun toggleFavorite(ctx: Context, e: RomEntry): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val set = prefs.getStringSet("favorites", emptySet())!!.toMutableSet()
        val added = set.add(e.uri.toString())
        if (!added) set.remove(e.uri.toString())
        prefs.edit().putStringSet("favorites", set).apply()
        return added
    }

    fun raGames(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, 0).getStringSet("ra_games", emptySet())!!

    fun markHasAchievements(ctx: Context, uri: Uri, iconUrl: String? = null) {
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val set = prefs.getStringSet("ra_games", emptySet())!!.toMutableSet()
        if (set.add(uri.toString())) prefs.edit().putStringSet("ra_games", set).apply()
        if (!iconUrl.isNullOrBlank()) {
            prefs.edit().putString("ra_icon_" + uri.toString().hashCode(), iconUrl).apply()
        }
    }

    fun raIconUrl(ctx: Context, e: RomEntry): String? =
        ctx.getSharedPreferences(PREFS, 0).getString("ra_icon_" + e.uri.toString().hashCode(), null)

    fun hasAchievements(ctx: Context, e: RomEntry) = raGames(ctx).contains(e.uri.toString())

    private fun allStats(ctx: Context): Map<String, Stats> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("stats", "") ?: ""
        val out = HashMap<String, Stats>()
        raw.split('\n').forEach { line ->
            val p = line.split('\t')
            if (p.size == 4) {
                out[p[0]] = Stats(
                    playCount = p[1].toIntOrNull() ?: 0,
                    playTimeMs = p[2].toLongOrNull() ?: 0L,
                    lastPlayed = p[3].toLongOrNull() ?: 0L,
                )
            }
        }
        return out
    }

    fun stats(ctx: Context, uri: Uri): Stats = allStats(ctx)[uri.toString()] ?: Stats()

    private fun writeStats(ctx: Context, map: Map<String, Stats>) {
        val raw = map.entries.joinToString("\n") { (k, v) ->
            "$k\t${v.playCount}\t${v.playTimeMs}\t${v.lastPlayed}"
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("stats", raw).apply()
    }

    fun addPlayTime(ctx: Context, uri: Uri, millis: Long) {
        if (millis < 1000L) return
        val map = allStats(ctx).toMutableMap()
        val key = uri.toString()
        val cur = map[key] ?: Stats()
        map[key] = cur.copy(playTimeMs = cur.playTimeMs + millis)
        writeStats(ctx, map)
    }

    fun formatPlayTime(uiContext: android.content.Context, millis: Long): String {
        val minutes = millis / 60_000L
        return when {
            minutes <= 0L -> uiContext.getString(R.string.empty_value)
            minutes < 60L -> uiContext.getString(R.string.library_min, minutes)
            else -> uiContext.getString(R.string.library_h, minutes / 60L)
        }
    }

    fun recent(ctx: Context): List<RomEntry> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("recent", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("\t"); if (p.size == 2) RomEntry(Uri.parse(p[0]), p[1]) else null
        }
    }

    fun markPlayed(ctx: Context, e: RomEntry) {
        val cur = recent(ctx).filter { it.uri != e.uri }.toMutableList()
        cur.add(0, e)
        val raw = cur.take(10).joinToString("\n") { "${it.uri}\t${it.name}" }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("recent", raw).apply()

        val map = allStats(ctx).toMutableMap()
        val key = e.uri.toString()
        val stat = map[key] ?: Stats()
        map[key] = stat.copy(playCount = stat.playCount + 1, lastPlayed = System.currentTimeMillis())
        writeStats(ctx, map)
    }

    fun viewMode(ctx: Context): ViewMode =
        if (ctx.getSharedPreferences(PREFS, 0).getString("view_mode", "GRID") == "LIST") ViewMode.LIST else ViewMode.GRID

    fun setViewMode(ctx: Context, mode: ViewMode) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("view_mode", mode.name).apply()
    }

    fun filter(ctx: Context): Filter = runCatching {
        Filter.valueOf(ctx.getSharedPreferences(PREFS, 0).getString("filter", null) ?: "ALL")
    }.getOrDefault(Filter.ALL)

    fun setFilter(ctx: Context, f: Filter) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("filter", f.name).apply()
    }

    fun sorting(ctx: Context): Sorting = runCatching {
        Sorting.valueOf(ctx.getSharedPreferences(PREFS, 0).getString("sorting", null) ?: "ALPHABETICALLY")
    }.getOrDefault(Sorting.ALPHABETICALLY)

    fun sortingDescending(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean("sorting_desc", false)

    fun setSorting(ctx: Context, s: Sorting, descending: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("sorting", s.name).putBoolean("sorting_desc", descending).apply()
    }

    fun platform(ctx: Context, e: RomEntry): Platform {
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val key = "unit_" + e.uri.toString().hashCode()
        val cached = prefs.getInt(key, -1)
        if (cached >= 0) return if (cached == 3) Platform.DSI else Platform.DS
        val unit = info(ctx, e).unitCode
        prefs.edit().putInt(key, unit).apply()
        return if (unit == 3) Platform.DSI else Platform.DS
    }

    fun platformLabel(uiContext: android.content.Context, p: Platform) = if (p == Platform.DSI) uiContext.getString(R.string.common_dsiware) else uiContext.getString(R.string.common_ds)

    fun cachedPlatform(ctx: Context, e: RomEntry): Platform? {
        val cached = ctx.getSharedPreferences(PREFS, 0).getInt("unit_" + e.uri.toString().hashCode(), -1)
        if (cached < 0) return null
        return if (cached == 3) Platform.DSI else Platform.DS
    }

    fun info(ctx: Context, e: RomEntry): NdsInfo {
        iconCache[e.uri.toString()]?.let { return it }
        val info = if (e.name.endsWith(".nds", true)) {
            NdsRom.read(ctx, e.uri).takeUnless { it.title.isBlank() && it.icon == null }
                ?: runCatching { ctx.contentResolver.openInputStream(e.uri)?.let { NdsRom.read(it) } }.getOrNull()
                ?: NdsInfo(e.title, null)
        } else NdsInfo(e.title, null)
        val resolved = if (info.title.isBlank()) info.copy(title = e.title) else info
        iconCache[e.uri.toString()] = resolved
        return resolved
    }

    fun cachedIcon(uri: Uri): Bitmap? = iconCache[uri.toString()]?.icon

    fun createShortcut(ctx: Context, e: RomEntry) {
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return
        val intent = Intent(ctx, Class.forName("com.seedlessds.app.emu.EmulatorActivity")).apply {
            action = Intent.ACTION_VIEW
            putExtra("rom_uri", e.uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val icon = info(ctx, e).icon
        val builder = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "seedless-" + e.uri.hashCode())
            .setShortLabel(e.title.take(20))
            .setIntent(intent)
        if (icon != null) builder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(
            Bitmap.createScaledBitmap(icon, 96, 96, true)))
        else builder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, android.R.drawable.ic_menu_send))
        runCatching { androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, builder.build(), null) }
    }
}
