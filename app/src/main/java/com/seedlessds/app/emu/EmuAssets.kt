package com.seedlessds.app.emu

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

object EmuAssets {
    const val DATA_DIR = "SeedlessDS"

    private data class Item(val asset: String, val dest: String)

    private val ITEMS = listOf(
        Item("game_database.xml", "game_database.xml"),
        Item("LC_default.dat", "config/LC_default.dat"),
        Item("nds_bios_arm7_replacement.bin", "system/nds_bios_arm7_replacement.bin"),
        Item("nds_bios_arm9_replacement.bin", "system/nds_bios_arm9_replacement.bin"),
    )

    fun systemDir(ctx: Context): File = File(ctx.filesDir, DATA_DIR).apply { mkdirs() }

    fun ensureInstalled(ctx: Context, sys: File = systemDir(ctx)) {
        for (it in ITEMS) {
            val out = File(sys, it.dest)
            val assetSize = try { ctx.assets.openFd(it.asset).length } catch (e: Exception) { -1L }
            if (out.exists() && (assetSize < 0 || out.length() == assetSize)) continue
            out.parentFile?.mkdirs()
            ctx.assets.open(it.asset).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        copyAssetDir(ctx, "shaders", File(sys, "shaders"))
        listOf("savestates", "backup", "cheats", "user").forEach { File(sys, it).mkdirs() }
    }

    private fun copyAssetDir(ctx: Context, assetPath: String, dest: File) {
        val entries = try { ctx.assets.list(assetPath) } catch (e: Exception) { null } ?: return
        if (entries.isEmpty()) {
            dest.parentFile?.mkdirs()
            if (!dest.exists()) {
                ctx.assets.open(assetPath).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            } else {
                val stock = when (assetPath) {
                    "shaders/quilez.dsd" -> 736L to "36d855f89b969822d2de863c7c2c763f07b765ba991525eef153a29dcd0ecde6"
                    "shaders/fxaa.dsd" -> 3138L to "94c00e20ab3d32f059ec1c38a565fd2aa17839434a2b625df922e5183876667f"
                    "shaders/fxaa/fxaa.dsd" -> 6194L to "c1b01e635df33dbd836f75a88808bf43aa469d0426b20b40be998d92e5aec13d"
                    else -> null
                }
                if (stock == null || dest.length() != stock.first) return
                val digest = MessageDigest.getInstance("SHA-256").digest(dest.readBytes())
                    .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                if (digest == stock.second) {
                    val replacement = ctx.assets.open(assetPath).use { it.readBytes() }
                    val file = AtomicFile(dest)
                    val output = file.startWrite()
                    try {
                        output.write(replacement)
                        file.finishWrite(output)
                    } catch (error: Exception) {
                        file.failWrite(output)
                        throw error
                    }
                }
            }
            return
        }
        dest.mkdirs()
        for (e in entries) copyAssetDir(ctx, "$assetPath/$e", File(dest, e))
    }
}
