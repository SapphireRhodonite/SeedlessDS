package com.seedlessds.app.emu

import android.content.Context
import android.content.res.Resources
import com.seedlessds.app.R
import com.seedlessds.core.SeedlessCore
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

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

    private val variantEditions = entries.keys.filter { it.count { character -> character == '.' } == 2 }
        .mapTo(HashSet()) { it.substringBeforeLast('.') }

    fun hasVariants(id: CartridgeId): Boolean = id.edition in variantEditions

    operator fun get(id: CartridgeId?): Entry? {
        if (id == null) return null
        return id.contentHash?.let { entries["${id.edition}.$it"] } ?: entries[id.edition] ?: entries[id.gameCode]
    }
}

data class CartridgeId(val gameCode: String, val revision: Int, val unitCode: Int, val contentHash: String? = null) {
    val edition: String get() = "$gameCode.$revision"

    companion object {
        fun read(context: Context, entry: RomLibrary.RomEntry): CartridgeId? = read(context, entry, null)

        internal fun read(context: Context, entry: RomLibrary.RomEntry, catalog: MultiplayerCatalog?): CartridgeId? {
            return try {
                when (entry.name.substringAfterLast('.').lowercase()) {
                    "nds", "srl" -> readStream(catalog) { context.contentResolver.openInputStream(entry.uri) }
                    "zip" -> zipId(context, entry, catalog)
                    "7z", "rar" -> {
                        val path = entry.uri.toString()
                        val header = SeedlessCore.romHeader(path, entry.name) ?: return null
                        identify(header, catalog) { offset, size -> SeedlessCore.romSection(path, entry.name, offset, size) }
                    }
                    else -> null
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

        private fun identify(header: ByteArray, catalog: MultiplayerCatalog?, section: (Int, Int) -> ByteArray?): CartridgeId {
            val id = fromHeader(header)
            if (catalog?.hasVariants(id) != true) return id
            val hash = try {
                contentHash(header, section)
            } catch (_: IOException) {
                null
            }
            return id.copy(contentHash = hash)
        }

        internal fun fromHeader(header: ByteArray): CartridgeId =
            CartridgeId(String(header, 12, 4, Charsets.US_ASCII).trim { it <= ' ' },
                header[0x1e].toInt() and 0xff, header[0x12].toInt() and 0xff)

        private fun contentHash(prefix: ByteArray, section: (Int, Int) -> ByteArray?): String? {
            val tail = section(prefix.size, 0x160 - prefix.size) ?: return null
            if (prefix.size + tail.size != 0x160) return null
            val header = prefix + tail
            val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val digest = MessageDigest.getInstance("MD5")
            digest.update(header)
            var remainingCodeBytes = 16 * 1024 * 1024
            for (base in listOf(0x20, 0x30)) {
                val offset = fields.getInt(base)
                val size = fields.getInt(base + 12)
                if (offset < 0 || size !in 0..remainingCodeBytes) return null
                if (size == 0) continue
                val code = section(offset, size) ?: return null
                if (code.size != size) return null
                digest.update(code)
                remainingCodeBytes -= size
            }
            val bannerOffset = fields.getInt(0x68)
            if (bannerOffset < 0) return null
            val banner = section(bannerOffset, 0xa00) ?: return null
            digest.update(banner.copyOf(0xa00))
            return BigInteger(1, digest.digest()).toString(16).padStart(32, '0')
        }

        private fun readStream(catalog: MultiplayerCatalog?, open: () -> InputStream?): CartridgeId? {
            var input = open() ?: return null
            var position = 0L
            fun section(offset: Int, size: Int): ByteArray? {
                if (offset < position) {
                    input.close()
                    input = open() ?: return null
                    position = 0L
                }
                while (position < offset) {
                    val skipped = input.skip(offset - position)
                    if (skipped > 0) position += skipped
                    else if (input.read() < 0) return null
                    else position++
                }
                val data = ByteArray(size)
                var filled = 0
                while (filled < size) {
                    val count = input.read(data, filled, size - filled)
                    if (count <= 0) break
                    filled += count
                    position += count
                }
                return if (filled == size) data else data.copyOf(filled)
            }
            try {
                val header = section(0, 32) ?: return null
                return if (header.size == 32) identify(header, catalog, ::section) else null
            } finally {
                input.close()
            }
        }

        private fun zipId(context: Context, entry: RomLibrary.RomEntry, catalog: MultiplayerCatalog?): CartridgeId? =
            withZipRom(context, entry) { open -> readStream(catalog, open) }

        internal fun <T> withZipRom(context: Context, entry: RomLibrary.RomEntry, read: (() -> InputStream) -> T?): T? =
            context.contentResolver.openFileDescriptor(entry.uri, "r")?.use { descriptor ->
                if (descriptor.statSize >= 0) {
                    try {
                        return@use readZip(File("/proc/self/fd/" + descriptor.fd), read)
                    } catch (_: FileNotFoundException) {
                    }
                }
                val copy = File.createTempFile("cartridge-", ".zip", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(entry.uri)?.use { input ->
                        copy.outputStream().use { input.copyTo(it) }
                    } ?: return@use null
                    readZip(copy, read)
                } finally {
                    copy.delete()
                }
            }

        private fun <T> readZip(file: File, read: (() -> InputStream) -> T?): T? = ZipFile(file).use { archive ->
            val rom = archive.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".nds", true) }
            rom?.let { read { archive.getInputStream(it) } }
        }
    }
}
