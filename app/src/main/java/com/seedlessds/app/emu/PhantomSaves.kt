package com.seedlessds.app.emu

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.seedlessds.app.R
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

internal class PhantomSaves(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "phantom")

    fun pending(rom: Uri): File? {
        val profile = Profiles.userSub(context)
        val matches = root.listFiles().orEmpty().filter { dir ->
            val key = File(dir, "rom")
            key.isFile && key.readText() == rom.toString() && File(dir, "profile").readText() == profile
        }
        check(matches.size <= 1) { context.getString(R.string.phantom_busy) }
        return matches.singleOrNull()
    }

    fun prepare(rom: Uri, name: String): File {
        require(name.isNotBlank() && File(name).name == name && name !in setOf(".", ".."))
        val entry = RomLibrary.RomEntry(rom, name)
        require(RomLibrary.platform(context, entry) == RomLibrary.Platform.DS) { context.getString(R.string.phantom_ds_only) }
        val settings = SettingsRepo.perGame(context, entry.title)
        val filename = entry.title + if (settings.rawSavFormat) ".sav" else ".dsv"
        val profile = Profiles.userSub(context)
        val system = EmuAssets.systemDir(context)
        val user = File(File(system, "user"), profile)
        val path = "backup/" + filename
        val source = SaveLocation.stat(context, rom, null, path, profile, File(user, path))
            ?.takeIf { !it.isDir }?.uri ?: error(context.getString(R.string.phantom_missing_save))
        val dir = File(root, UUID.randomUUID().toString())
        check(dir.mkdirs())
        try {
            val original = File(dir, "original")
            input(source).use { input -> original.outputStream().use { input.copyTo(it); it.fd.sync() } }
            require(original.length() > 0 && matches(source, original)) { context.getString(R.string.phantom_conflict) }
            for (side in listOf("primary", "peer")) {
                val player = File(dir, side)
                EmuAssets.ensureInstalled(context, player)
                for (folder in listOf("system", "shaders", "config")) {
                    val shared = File(system, folder)
                    if (shared.isDirectory) shared.copyRecursively(File(player, folder), overwrite = true)
                }
                val firmware = File(user, "system")
                if (firmware.isDirectory) firmware.copyRecursively(File(player, "user/system"), overwrite = true)
                val privateSave = original.copyTo(File(player, "user/" + path))
                check(privateSave.inputStream().use(::digest).contentEquals(original.inputStream().use(::digest)))
            }
            File(dir, "filename").writeText(filename)
            File(dir, "source").writeText(source.toString())
            File(dir, "profile").writeText(profile)
            File(dir, "rom").writeText(rom.toString())
            return dir
        } catch (error: Throwable) {
            dir.deleteRecursively()
            throw error
        }
    }

    fun finish(dir: File, keep: Boolean) {
        val files = listOf(File(dir, "primary/owner"), File(dir, "peer/owner"))
        RandomAccessFile(files[0], "rw").use { a ->
            a.channel.tryLock().use { first ->
                check(first != null) { context.getString(R.string.phantom_busy) }
                RandomAccessFile(files[1], "rw").use { b ->
                    b.channel.tryLock().use { second ->
                        check(second != null) { context.getString(R.string.phantom_busy) }
                        val publishing = File(dir, "publishing")
                        if (keep || publishing.exists()) {
                            val uri = Uri.parse(File(dir, "source").readText())
                            val original = File(dir, "original")
                            val main = File(dir, "primary/user/backup/" + File(dir, "filename").readText())
                            val selected = if (keep) main else original
                            require(selected.length() > 0)
                            if (!matches(uri, selected)) {
                                check(matches(uri, original) || (publishing.exists() &&
                                    (prefixOf(uri, main) || prefixOf(uri, original)))) { context.getString(R.string.phantom_conflict) }
                                publishing.outputStream().use { it.fd.sync() }
                                write(uri, selected)
                                check(matches(uri, selected)) { context.getString(R.string.phantom_save_failed) }
                            }
                        }
                        check(dir.deleteRecursively())
                    }
                }
            }
        }
    }

    private fun input(uri: Uri): InputStream = checkNotNull(context.contentResolver.openInputStream(uri))

    private fun write(uri: Uri, source: File) {
        if (uri.scheme == "file") {
            val target = AtomicFile(File(requireNotNull(uri.path)))
            val out = target.startWrite()
            try {
                source.inputStream().use { it.copyTo(out) }
                target.finishWrite(out)
            } catch (error: Throwable) {
                target.failWrite(out)
                throw error
            }
        } else {
            checkNotNull(context.contentResolver.openOutputStream(uri, "rwt")).use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
        }
    }

    private fun matches(uri: Uri, file: File) = input(uri).use(::digest).contentEquals(file.inputStream().use(::digest))

    private fun digest(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(bytes)
            if (count < 0) return hash.digest()
            hash.update(bytes, 0, count)
        }
    }

    private fun prefixOf(uri: Uri, file: File): Boolean = input(uri).buffered().use { current ->
        file.inputStream().buffered().use { previous ->
            var x = current.read()
            while (x >= 0) {
                if (x != previous.read()) return false
                x = current.read()
            }
            true
        }
    }
}
