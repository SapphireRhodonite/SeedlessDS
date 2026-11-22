package com.seedlessds.app.emu

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest

internal class LegacyBoxArtCache(directory: File) {
    private val names = runCatching {
        File(directory, "matches.txt").useLines { lines ->
            lines.mapNotNull { line ->
                val fields = line.split('\t', limit = 2)
                fields.takeIf { it.size == 2 && it[1] != "-" }?.let { it[0] to it[1] }
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    @OptIn(ExperimentalCoilApi::class)
    fun reuse(context: Context, uri: String, cover: BoxArtCatalog.Cover): Boolean {
        val name = names[uri] ?: return false
        val legacyUrl = BASE + URLEncoder.encode(name, "UTF-8").replace("+", "%20") + ".png"
        val cache = context.imageLoader.diskCache ?: return false
        return try {
            cache.openSnapshot(cover.url)?.use { return false }
            val bytes = cache.openSnapshot(legacyUrl)?.use { snapshot ->
                val data = snapshot.data.toFile()
                if (data.length() !in 1..MAX_COVER_BYTES) return false
                data.readBytes()
            } ?: return false
            val hash = MessageDigest.getInstance("SHA-1")
            hash.update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII))
            if (BigInteger(1, hash.digest(bytes)).toString(16).padStart(40, '0') != cover.blobSha) return false

            val editor = cache.openEditor(cover.url) ?: return false
            try {
                editor.metadata.toFile().writeBytes(byteArrayOf())
                editor.data.toFile().writeBytes(bytes)
                editor.commit()
            } catch (error: IOException) {
                editor.abort()
                return false
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        private const val BASE = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/"
        private const val MAX_COVER_BYTES = 8L * 1024 * 1024
    }
}
