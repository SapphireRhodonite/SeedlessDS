package com.seedlessds.app.emu

import com.seedlessds.core.SeedlessCore

import android.opengl.EGL14
import android.opengl.EGL15
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLSync
import android.opengl.GLES20
import android.util.Log
import com.seedlessds.app.filesystem.PathCache
import java.nio.ByteBuffer
import java.nio.ByteOrder






internal class ExternalThread(
    private val dpy: EGLDisplay, private val shared: EGLContext,
) : Thread("DsExternal") {
    private lateinit var cfg: EGLConfig

    private val lock = Object()
    private var pending = false
    private var number = 0L
    private var fence: EGLSync? = null
    private var topTex = 0; private var bottomTex = 0; private var srcW = 256; private var srcH = 192
    private var quit = false
    @Volatile var externalFence: EGLSync? = null
        private set
    private var lastDrawn = -1L
    private var busyTop = 0; private var busyBottom = 0
    var drawn = 0; var skipped = 0; var repeated = 0
    private var extPrev = 0L; private var ext1 = 0; private var ext2 = 0; private var ext3 = 0; private var extMax = 0L

    private var ctx: EGLContext? = null
    private var surface: EGLSurface? = null
    private var surfaceGen = -1; private var surfaceW = 0; private var surfaceH = 0
    private var extVbo = 0
    private var chainFilter = ""; private var chainSw = 0; private var chainSh = 0; private var chainW = 0; private var chainH = 0
    private var chainRect = floatArrayOf(0f, 0f, 0f, 0f); private var chainReady = false

    private val UV_EXT = floatArrayOf(
        0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
        0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f,
    )
    private val QUAD_FULL = floatArrayOf(-1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)

    fun publish(n: Long, v: EGLSync, top: Int, bottom: Int, sw: Int, sh: Int) = synchronized(lock) {
        fence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
        if (pending) skipped++
        pending = true; number = n; fence = v; topTex = top; bottomTex = bottom; srcW = sw; srcH = sh
        lock.notifyAll()
    }

    fun terminate() { synchronized(lock) { quit = true; lock.notifyAll() }; runCatching { join(1000) } }

    override fun run() {
        val out = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
        if (!EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RED_SIZE, 5, EGL14.EGL_GREEN_SIZE, 6, EGL14.EGL_BLUE_SIZE, 5,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE), 0, out, 0, 1, num, 0) || num[0] == 0) {
            com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: no config"); return
        }
        cfg = out[0]!!
        val c = EGL14.eglCreateContext(dpy, cfg, shared, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (c == null || c == EGL14.EGL_NO_CONTEXT) { com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: no shared context 0x${Integer.toHexString(EGL14.eglGetError())}"); return }
        ctx = c
        while (true) {
            var n: Long; var v: EGLSync?; var top: Int; var bottom: Int; var sw: Int; var sh: Int
            synchronized(lock) {
                while (!pending && !quit) runCatching { lock.wait() }
                if (quit) { release(); return }
                pending = false; n = number; v = fence; fence = null
                top = topTex; bottom = bottomTex; sw = srcW; sh = srcH
                busyTop = top; busyBottom = bottom
            }
            ExternalSurface.withSurface { snap ->
                if (snap == null) { releaseSurface(); v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }; return@withSurface }
                drawIt(snap, n, v, top, bottom, sw, sh)
            }
            synchronized(lock) { busyTop = 0; busyBottom = 0; lock.notifyAll() }
        }
    }

    private fun ensureSurface(snap: ExternalSurface.Snap): Boolean {
        if (surface != null && snap.generation == surfaceGen && snap.width == surfaceW && snap.height == surfaceH) return true
        releaseSurface()
        val s = EGL14.eglCreateWindowSurface(dpy, cfg, snap.surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE) { com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: no surface 0x${Integer.toHexString(EGL14.eglGetError())}"); return false }
        if (!EGL14.eglMakeCurrent(dpy, s, s, ctx)) { com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: makeCurrent 0x${Integer.toHexString(EGL14.eglGetError())}"); EGL14.eglDestroySurface(dpy, s); return false }
        surface = s; surfaceGen = snap.generation; surfaceW = snap.width; surfaceH = snap.height
        chainReady = false; chainFilter = ""
        return true
    }

    private fun releaseSurface() {
        surface?.let { EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT); runCatching { EGL14.eglDestroySurface(dpy, it) } }
        surface = null; surfaceGen = -1; chainReady = false
    }

    private fun release() {
        releaseSurface()
        ctx?.let { runCatching { EGL14.eglDestroyContext(dpy, it) } }; ctx = null
    }

    private fun writeVbo(offsetBytes: Int, data: FloatArray) {
        val b = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        b.asFloatBuffer().put(data).flip()
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, offsetBytes, data.size * 4, b)
    }

    private fun quadNdc(w: Int, h: Int, r: FloatArray): FloatArray {
        fun ndx(px: Float) = px / w * 2f - 1f
        fun ndy(px: Float) = 1f - px / h * 2f
        val x0 = ndx(r[0]); val x1 = ndx(r[0] + r[2]); val yt = ndy(r[1]); val yb = ndy(r[1] + r[3])
        return floatArrayOf(x0, yt, x0, yb, x1, yb, x0, yt, x1, yb, x1, yt)
    }

    private fun prepareChain(snap: ExternalSurface.Snap, r: FloatArray, sw: Int, sh: Int) {
        val cfgApp = SettingsRepo.current
        if (extVbo == 0) {
            val buf = IntArray(1); GLES20.glGenBuffers(1, buf, 0); extVbo = buf[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 192, null, GLES20.GL_DYNAMIC_DRAW)
            writeVbo(48, QUAD_FULL); writeVbo(96, UV_EXT)
        } else GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
        if (chainReady && cfgApp.filter == chainFilter && sw == chainSw && sh == chainSh &&
            snap.width == chainW && snap.height == chainH && r.contentEquals(chainRect)) return
        writeVbo(0, quadNdc(snap.width, snap.height, r))
        if (!chainReady || cfgApp.filter != chainFilter) {
            val fxPath = PathCache.SYS_PREFIX + "shaders/${cfgApp.filter}.dfx"
            val fxResult = SeedlessCore.extfxLoad(fxPath, 0, 96)
            if (fxResult != 0) com.seedlessds.app.AppLog.w("reconDS", "extfxLoad failed ($fxResult): $fxPath, using the default shader")
            chainFilter = cfgApp.filter
        }
        SeedlessCore.extfxSetup(sw, sh, 0, 0, snap.width, snap.height)
        chainSw = sw; chainSh = sh; chainW = snap.width; chainH = snap.height; chainRect = r.copyOf(); chainReady = true
        com.seedlessds.app.AppLog.i("reconDS", "EXTERNAL-THREAD chain ${cfgApp.filter} source ${sw}x${sh} surface ${snap.width}x${snap.height}")
    }

    private fun drawIt(snap: ExternalSurface.Snap, n: Long, v: EGLSync?, top: Int, bottom: Int, sw: Int, sh: Int) {
        if (!ensureSurface(snap)) { v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }; return }
        if (n == lastDrawn) { repeated++; return }
        v?.let { EGL15.eglWaitSync(dpy, it, 0); runCatching { EGL15.eglDestroySync(dpy, it) } }
        val r = ExternalLayout.rect(snap.width, snap.height)
        prepareChain(snap, r, sw, sh)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, snap.width, snap.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val cfgApp = SettingsRepo.current
        val screen = (cfgApp.extDisplayScreen xor (if (cfgApp.swapScreens) 1 else 0)) and 1
        val tex = if (screen == 1) bottom else top
        if (tex != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            SeedlessCore.extfxRender(tex, screen, 0, 6, r[2].toInt().coerceAtLeast(1), r[3].toInt().coerceAtLeast(1))
            GLES20.glFinish()
        }
        val own = EGL15.eglCreateSync(dpy, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        GLES20.glFlush()
        externalFence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
        externalFence = own
        if (!EGL14.eglSwapBuffers(dpy, surface)) { com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: swap 0x${Integer.toHexString(EGL14.eglGetError())}"); releaseSurface() }
        lastDrawn = n; drawn++
        val now = android.os.SystemClock.elapsedRealtimeNanos()
        if (extPrev != 0L) {
            val period = now - extPrev
            if (period > extMax) extMax = period
            when {
                period < 25_000_000L -> ext1++
                period < 41_700_000L -> ext2++
                else -> ext3++
            }
        }
        extPrev = now
        if (drawn % 600 == 0) {
            com.seedlessds.app.AppLog.i("pacing", "external drawn=600 skipped=$skipped repeated=$repeated 1v=$ext1 2v=$ext2 3v+=$ext3 max=%.1fms".format(extMax / 1e6))
            skipped = 0; repeated = 0; ext1 = 0; ext2 = 0; ext3 = 0; extMax = 0L
        }
    }



    fun acquire(top: Int, bottom: Int): EGLSync? = synchronized(lock) {
        while (!quit && (busyTop == top || busyBottom == bottom)) runCatching { lock.wait(20) }
        val v = externalFence; externalFence = null; v
    }
}
