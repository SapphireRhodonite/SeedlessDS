package com.seedlessds.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.provider.DocumentsContract
import com.seedlessds.core.SeedlessCore
import com.seedlessds.app.emu.DeviceProfiles
import com.seedlessds.app.emu.RomLibrary
import com.seedlessds.app.emu.SettingsRepo
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    const val FILE = "SeedlessDS.log"
    private const val LIMIT = 4L * 1024 * 1024
    private const val PENDING = 400

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var out: FileOutputStream? = null
    private var pfd: ParcelFileDescriptor? = null
    private var readPfd: ParcelFileDescriptor? = null
    private var folder = ""
    private val pending = ArrayList<String>()
    @Volatile private var starting = false

    fun start(ctx: Context, enabled: Boolean) {
        val app = ctx.applicationContext
        if (!enabled) { close(); return }
        synchronized(this) { if (starting) return; starting = true }
        Thread({
            try { open(app) } catch (t: Throwable) { Log.w("AppLog", "log file not opened", t) }
            finally { synchronized(this) { starting = false } }
        }, "seedless-log").start()
    }

    private fun open(ctx: Context) {
        val root = RomLibrary.folders(ctx).firstOrNull()
        val key = root?.toString() ?: "app"
        val same = synchronized(this) { key == folder && out != null }
        if (same) return
        var note = ""
        var p: ParcelFileDescriptor? = null
        var r: ParcelFileDescriptor? = null
        if (root != null) {
            try {
                val t0 = android.os.SystemClock.elapsedRealtime()
                val treeDoc = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
                var doc = findChild(ctx, root)
                if (doc != null && doc.second > LIMIT) { DocumentsContract.deleteDocument(ctx.contentResolver, doc.first); doc = null }
                val uri = doc?.first ?: DocumentsContract.createDocument(ctx.contentResolver, treeDoc, "application/octet-stream", FILE)
                p = uri?.let { ctx.contentResolver.openFileDescriptor(it, "wa") }
                if (p != null) r = runCatching { uri?.let { ctx.contentResolver.openFileDescriptor(it, "r") } }.getOrNull()
                note = if (p == null) "ROM folder log not available (document=${uri != null})" else "log file opened in ${android.os.SystemClock.elapsedRealtime() - t0} ms"
            } catch (t: Throwable) { note = "ROM folder log failed: $t" }
        } else note = "no ROM folder registered"
        if (p == null) {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = java.io.File(dir, FILE)
            if (f.length() > LIMIT) f.delete()
            p = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND)
            r = runCatching { ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            note += "; writing to ${f.absolutePath}"
        }
        val header = header(ctx, key) + (if (note.isEmpty()) "" else "note: $note" + System.lineSeparator())
        synchronized(this) {
            close()
            pfd = p
            readPfd = r
            out = FileOutputStream(p.fileDescriptor)
            folder = key
            raw(header)
            for (l in pending) raw(l)
            pending.clear()
            SeedlessCore.logFd(p.fd)
            SeedlessCore.crashLogFd(r?.fd ?: -1)
        }
    }

    private fun findChild(ctx: Context, root: android.net.Uri): Pair<android.net.Uri, Long>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == FILE) return Pair(DocumentsContract.buildDocumentUriUsingTree(root, c.getString(0)), c.getLong(2))
            }
        }
        return null
    }

    @Synchronized fun close() {
        if (out != null) { SeedlessCore.logFd(-1); SeedlessCore.crashLogFd(-1) }
        runCatching { out?.close() }
        runCatching { pfd?.close() }
        runCatching { readPfd?.close() }
        out = null; pfd = null; readPfd = null; folder = ""
    }

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            e("crash", "uncaught exception on thread ${t.name}", e)
            close()
            previous?.uncaughtException(t, e)
        }
    }

    private fun header(ctx: Context, root: String): String {
        val sb = StringBuilder()
        sb.append("\n== SeedlessDS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) pid=${Process.myPid()} started=${stamp.format(Date())}\n")
        sb.append("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) Android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT} ${Build.FINGERPRINT}\n")
        runCatching {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            for (d in dm.displays) {
                val m = d.mode
                sb.append("display ${d.displayId} \"${d.name}\" ${m.physicalWidth}x${m.physicalHeight} ${m.refreshRate}Hz\n")
            }
        }
        sb.append("profile: ${DeviceProfiles.current(ctx)?.name ?: "generic"}\n")
        sb.append("log folder: $root\n")
        return sb.toString()
    }

    private fun raw(text: String) {
        val o = out ?: return
        try { o.write(text.toByteArray()) } catch (t: Throwable) { close() }
    }

    private fun put(level: String, tag: String, msg: String) {
        val line = "${stamp.format(Date())} $level $tag: $msg\n"
        synchronized(this) {
            if (out != null) raw(line)
            else { if (pending.size >= PENDING) pending.removeAt(0); pending.add(line) }
        }
    }

    fun i(tag: String, msg: String) { Log.i(tag, msg); put("I", tag, msg) }
    fun d(tag: String, msg: String) { Log.d(tag, msg); put("D", tag, msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg); put("W", tag, msg) }
    fun w(tag: String, msg: String, tr: Throwable) { Log.w(tag, msg, tr); put("W", tag, msg + "\n" + Log.getStackTraceString(tr)) }
    fun e(tag: String, msg: String) { Log.e(tag, msg); put("E", tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable) { Log.e(tag, msg, tr); put("E", tag, msg + "\n" + Log.getStackTraceString(tr)) }
}
