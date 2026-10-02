package com.seedlessds.app.emu

import android.content.Context
import java.io.File

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
            if (!dest.exists()) ctx.assets.open(assetPath).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        for (e in entries) copyAssetDir(ctx, "$assetPath/$e", File(dest, e))
    }
}
