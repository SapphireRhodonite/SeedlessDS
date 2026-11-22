package com.seedlessds.app.emu

import android.content.Context
import android.content.ContentProviderClient
import android.content.SharedPreferences
import android.os.SystemClock
import com.seedlessds.app.AppLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.IOException
import android.os.RemoteException

internal object RomPlatforms {
    private const val PREFS = "rom_platforms"
    private const val BATCH = 32
    private const val READERS = 4
    private const val UPDATE_MS = 1_200L

    fun cached(context: Context, entry: RomLibrary.RomEntry): RomLibrary.Platform? =
        cachedUnit(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), entry)?.platform()

    fun get(context: Context, entry: RomLibrary.RomEntry): RomLibrary.Platform {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cachedUnit(prefs, entry)?.let { return it.platform() }
        val unit = CartridgeId.read(context, entry)?.unitCode ?: return RomLibrary.Platform.DS
        val edit = prefs.edit()
        store(edit, entry, unit)
        edit.apply()
        return unit.platform()
    }

    suspend fun filter(
        context: Context,
        ordered: List<RomLibrary.RomEntry>,
        target: RomLibrary.Platform,
        partial: suspend (List<RomLibrary.RomEntry>) -> Unit,
    ): List<RomLibrary.RomEntry> {
        val started = SystemClock.elapsedRealtime()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val matched = BooleanArray(ordered.size)
        val pending = ArrayList<Int>()
        ordered.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            val cached = cachedUnit(prefs, entry)
            if (cached == null) pending.add(index) else matched[index] = cached.platform() == target
        }
        fun matches() = ordered.filterIndexed { index, _ -> matched[index] }

        if (pending.isNotEmpty()) {
            val edit = prefs.edit()
            var included = matched.count { it }
            var published = 0
            var lastUpdate = started
            if (included > 0) {
                partial(matches())
                published = included
                lastUpdate = SystemClock.elapsedRealtime()
            }
            coroutineScope {
                val batches = Channel<List<Pair<Int, Int?>>>(READERS)
                val partitionSize = (pending.size + READERS - 1) / READERS
                for ((reader, partition) in pending.chunked(partitionSize).withIndex()) {
                    launch {
                        val clients = HashMap<String, ContentProviderClient?>()
                        try {
                            val traversal = if (reader % 2 == 0) partition else partition.asReversed()
                            for (batch in traversal.chunked(BATCH)) {
                                batches.send(batch.map { index ->
                                    currentCoroutineContext().ensureActive()
                                    index to readUnit(context, ordered[index], clients)
                                })
                            }
                        } finally {
                            clients.values.forEach { client -> client?.release() }
                        }
                    }
                }
                var checked = 0
                while (checked < pending.size) {
                    val units = batches.receive()
                    checked += units.size
                    for ((index, unit) in units) {
                        if (unit != null) store(edit, ordered[index], unit)
                        if ((unit ?: 0).platform() == target) {
                            matched[index] = true
                            included++
                        }
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (checked < pending.size && included != published && (published == 0 || now - lastUpdate >= UPDATE_MS)) {
                        if (published == 0) AppLog.i("library", "platform filter $target: first match after ${now - started} ms")
                        partial(matches())
                        published = included
                        lastUpdate = now
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            edit.apply()
        }
        AppLog.i("library", "platform filter $target: ${ordered.size} candidates, ${pending.size} headers, ${SystemClock.elapsedRealtime() - started} ms")
        return matches()
    }

    private fun readUnit(
        context: Context,
        entry: RomLibrary.RomEntry,
        clients: MutableMap<String, ContentProviderClient?>,
    ): Int? {
        val authority = entry.uri.authority
        if (authority != null && (entry.name.endsWith(".nds", true) || entry.name.endsWith(".srl", true))) {
            try {
                val client = clients.getOrPut(authority) { context.contentResolver.acquireContentProviderClient(authority) }
                val unit = client?.openTypedAssetFileDescriptor(entry.uri, "*/*", null)?.use { descriptor ->
                    DataInputStream(descriptor.createInputStream()).use { stream ->
                        val header = ByteArray(32)
                        stream.readFully(header)
                        CartridgeId.fromHeader(header).unitCode
                    }
                }
                if (unit != null) return unit
            } catch (_: IOException) {
            } catch (_: RemoteException) {
            } catch (_: SecurityException) {
            }
        }
        return CartridgeId.read(context, entry)?.unitCode
    }

    private fun Int.platform(): RomLibrary.Platform =
        if (this == 3) RomLibrary.Platform.DSI else RomLibrary.Platform.DS

    private fun cachedUnit(prefs: SharedPreferences, entry: RomLibrary.RomEntry): Int? {
        val prefix = stamp(entry) ?: return null
        val record = prefs.getString(entry.uri.toString(), null) ?: return null
        return if (record.startsWith(prefix)) record.substring(prefix.length).toIntOrNull() else null
    }

    private fun store(edit: SharedPreferences.Editor, entry: RomLibrary.RomEntry, unit: Int) {
        val prefix = stamp(entry) ?: return
        edit.putString(entry.uri.toString(), prefix + unit)
    }

    private fun stamp(entry: RomLibrary.RomEntry): String? {
        val size = entry.size ?: return null
        val modified = entry.modified ?: return null
        if (size <= 0 || modified <= 0) return null
        return "$size:$modified:${entry.name}:"
    }
}
