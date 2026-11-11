package com.seedlessds.app.emu

import android.content.Context
import android.content.res.Resources
import com.seedlessds.core.SeedlessCore
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import com.seedlessds.app.R

internal class MultiplayerCatalog(resources: Resources) {
    data class Entry(
        val singleCard: Int,
        val multiCard: Int,
        val wfc: Boolean,
    ) {
        fun players(play: MultiplayerRoom.Play): Int = when (play) {
            MultiplayerRoom.Play.DOWNLOAD -> singleCard
            MultiplayerRoom.Play.ROM -> multiCard
            MultiplayerRoom.Play.CHAT -> 0
        }
    }

    private val entries = resources.openRawResource(R.raw.ds_multiplayer_catalog).bufferedReader().useLines { lines ->
        lines.associate { line ->
            val fields = line.split('\t')
            fields[0] to Entry(fields[1].toInt(), fields[2].toInt(), fields[3] == "1")
        }
    }

    operator fun get(id: CartridgeId?): Entry? = id?.let { entries["${it.gameCode}.${it.revision}"] ?: entries[it.gameCode] }
}

data class CartridgeId(val gameCode: String, val revision: Int, val unitCode: Int) {
    companion object {
        fun read(context: Context, entry: RomLibrary.RomEntry): CartridgeId? {
            val header = try {
                when (entry.name.substringAfterLast('.').lowercase()) {
                    "nds" -> context.contentResolver.openInputStream(entry.uri)?.use(::headerBytes)
                    "zip" -> zipHeader(context, entry)
                    "7z", "rar" -> SeedlessCore.romHeader(entry.uri.toString(), entry.name)
                    else -> null
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } ?: return null
            return CartridgeId(String(header, 12, 4, Charsets.US_ASCII).trim { it <= ' ' },
                header[0x1e].toInt() and 0xff, header[0x12].toInt() and 0xff)
        }

        private fun headerBytes(stream: InputStream): ByteArray =
            ByteArray(32).also { DataInputStream(stream).readFully(it) }

        private fun zipHeader(context: Context, entry: RomLibrary.RomEntry): ByteArray? =
            context.contentResolver.openFileDescriptor(entry.uri, "r")?.use { descriptor ->
                if (descriptor.statSize >= 0) {
                    try {
                        return@use readZip(File("/proc/self/fd/" + descriptor.fd))
                    } catch (_: FileNotFoundException) {
                    }
                }
                val copy = File.createTempFile("cartridge-", ".zip", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(entry.uri)?.use { input ->
                        copy.outputStream().use { input.copyTo(it) }
                    } ?: return@use null
                    readZip(copy)
                } finally {
                    copy.delete()
                }
            }

        private fun readZip(file: File): ByteArray? = ZipFile(file).use { archive ->
            val rom = archive.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".nds", true) }
            rom?.let { archive.getInputStream(it).use(::headerBytes) }
        }
    }
}
