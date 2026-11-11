package com.seedlessds.app.emu

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import org.json.JSONObject

internal class CartridgeIndex(private val context: Context) {
    private val preferences = context.getSharedPreferences("cartridge_index", Context.MODE_PRIVATE)

    suspend fun load(entries: List<RomLibrary.RomEntry>): Map<Uri, CartridgeId> {
        val remaining = preferences.all.toMutableMap()
        val changes = preferences.edit()
        val result = HashMap<Uri, CartridgeId>()
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            val key = entry.uri.toString()
            val stored = remaining.remove(key)
            val cacheable = entry.size != null && entry.size >= 0 && (entry.modified ?: 0L) > 0
            val cached = if (cacheable && stored is String) decode(stored, entry) else null
            val id = cached ?: CartridgeId.read(context, entry)
            if (id != null) result[entry.uri] = id
            if (cached != null) continue
            if (cacheable && id != null) {
                val record = JSONObject()
                    .put("name", entry.name)
                    .put("size", entry.size)
                    .put("modified", entry.modified)
                    .put("code", id.gameCode)
                    .put("revision", id.revision)
                    .put("unit", id.unitCode)
                changes.putString(key, record.toString())
            } else if (stored != null) {
                changes.remove(key)
            }
        }
        remaining.keys.forEach(changes::remove)
        currentCoroutineContext().ensureActive()
        changes.apply()
        return result
    }

    fun invalidate(uri: Uri) {
        preferences.edit().remove(uri.toString()).apply()
    }

    private fun decode(stored: String, entry: RomLibrary.RomEntry): CartridgeId? {
        return try {
            val record = JSONObject(stored)
            if (record.getString("name") == entry.name && record.getLong("size") == entry.size &&
                record.getLong("modified") == entry.modified) {
                CartridgeId(record.getString("code"), record.getInt("revision"), record.getInt("unit"))
            } else null
        } catch (_: JSONException) {
            null
        }
    }
}
