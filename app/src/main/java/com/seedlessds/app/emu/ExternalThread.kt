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
    private var outputToken = 0L
    private var activeOutputToken = 0L
    private var inputReleased = false
    private val releaseInputFence: (EGLSync?) -> Unit = ::releaseInput
    private val outputConsumed: (Boolean) -> Unit = ::completeOutput
    private var outputTimingActive = false
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
    private var surfacePrepared = false
    private var extVbo = 0
    private var outputFbo = 0; private var outputTex = 0
    private var outputW = 0; private var outputH = 0
    private var outputUnavailable = false
    private val outputBlitter = DirectBlit()
    private var outputPipeline: ExternalOutputPipeline? = null
    private var queuedOutput: ExternalOutput? = null
    private var queuedOutputUnavailable = -1
    private val onOutputSwap: () -> Unit = ::recordSwap
    private var chainTarget = false
    private var chainFilter = ""; private var chainSw = 0; private var chainSh = 0; private var chainW = 0; private var chainH = 0
    private var chainRect = floatArrayOf(0f, 0f, 0f, 0f); private var chainReady = false

    private val UV_EXT = floatArrayOf(
        0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
        0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f,
    )
    private val QUAD_FULL = floatArrayOf(-1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)

    fun publish(n: Long, v: EGLSync, top: Int, bottom: Int, sw: Int, sh: Int, token: Long) = synchronized(lock) {
        fence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
        if (pending) skipped++
        pending = true; number = n; outputToken = token; fence = v; topTex = top; bottomTex = bottom; srcW = sw; srcH = sh
        lock.notifyAll()
    }

    fun terminate() { synchronized(lock) { quit = true; lock.notifyAll() }; join() }

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
            var n: Long; var token: Long; var v: EGLSync?; var top: Int; var bottom: Int; var sw: Int; var sh: Int
            synchronized(lock) {
                while (!pending && !quit) runCatching { lock.wait() }
                if (quit) { release(); return }
                pending = false; n = number; token = outputToken; v = fence; fence = null
                top = topTex; bottom = bottomTex; sw = srcW; sh = srcH
                busyTop = top; busyBottom = bottom
            }
            activeOutputToken = token
            inputReleased = false
            if (token == 0L && outputTimingActive) releaseOutputTiming()
            var prepareNext = false
            try {
                if (token == 0L || !drawQueued(n, v, top, bottom, sw, sh)) {
                    closeQueuedOutput()
                    ExternalSurface.withSurface { snap ->
                        if (snap == null) { releaseSurface(); v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }; return@withSurface }
                        prepareNext = drawIt(snap, n, v, top, bottom, sw, sh)
                    }
                }
            } finally {
                completeOutput(false)
            }
            synchronized(lock) { busyTop = 0; busyBottom = 0; lock.notifyAll() }
            if (prepareNext) ExternalSurface.withSurface { snap ->
                if (snap != null) prepareNextSurface(snap)
            }
        }
    }

    private fun closeQueuedOutput() {
        val output = queuedOutput ?: return
        queuedOutput = null
        try { output.close() } finally {
            synchronized(lock) {
                externalFence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
                externalFence = null
            }
            chainReady = false
        }
    }

    private fun drawQueued(n: Long, v: EGLSync?, top: Int, bottom: Int, sw: Int, sh: Int): Boolean {
        val geometry = ExternalSurface.geometry ?: return false
        if (queuedOutput?.matches(geometry) == false) closeQueuedOutput()
        if (queuedOutputUnavailable == geometry.generation) return false
        if (queuedOutput == null) {
            releaseSurface()
            queuedOutput = ExternalOutput.open(dpy, ctx!!, cfg, geometry, onOutputSwap)
            if (queuedOutput == null) {
                queuedOutputUnavailable = geometry.generation
                return false
            }
            chainReady = false
        }
        if (n == lastDrawn) {
            repeated++
            v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
            return true
        }
        val output = queuedOutput!!
        var ready = v
        try {
            output.acquire()
            ready = null
            if (v != null && v != EGL15.EGL_NO_SYNC) try {
                if (!EGL15.eglWaitSync(dpy, v, 0)) {
                    check(EGL15.eglClientWaitSync(dpy, v, 0, EGL15.EGL_FOREVER) == EGL15.EGL_CONDITION_SATISFIED) {
                        "External source fence wait failed"
                    }
                }
            } finally { EGL15.eglDestroySync(dpy, v) }
            val r = ExternalLayout.rect(geometry.width, geometry.height)
            prepareChain(geometry.width, geometry.height, r, sw, sh, true)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, output.framebuffer)
            com.seedlessds.app.SeedlessBridge.fboDest(output.framebuffer)
            GLES20.glViewport(0, 0, geometry.width, geometry.height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val settings = SettingsRepo.current
            val screen = (settings.extDisplayScreen xor (if (settings.swapScreens) 1 else 0)) and 1
            val texture = if (screen == 1) bottom else top
            try {
                if (texture != 0) {
                    GLES20.glDisable(GLES20.GL_BLEND)
                    SeedlessCore.extfxRender(texture, screen, 0, 6, r[2].toInt().coerceAtLeast(1), r[3].toInt().coerceAtLeast(1))
                }
            } finally { com.seedlessds.app.SeedlessBridge.fboDest(0) }
            output.submit(activeOutputToken, texture != 0, releaseInputFence)
            activeOutputToken = 0L
            lastDrawn = n
        } catch (error: Throwable) {
            queuedOutputUnavailable = geometry.generation
            closeQueuedOutput()
            releaseInput(null)
            com.seedlessds.app.AppLog.w("reconDS", "External queued output: ${error.message}")
        } finally {
            ready?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
        }
        return true
    }

    private fun releaseInput(consumed: EGLSync?) = synchronized(lock) {
        externalFence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
        externalFence = consumed
        busyTop = 0; busyBottom = 0
        inputReleased = true
        lock.notifyAll()
    }

    private fun completeOutput(consumed: Boolean) {
        val token = activeOutputToken
        activeOutputToken = 0L
        if (token != 0L) {
            if (consumed) outputTimingActive = true
            SeedlessCore.completeOutputFrame(token, consumed)
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

    private fun releaseOutputTiming() {
        if (!outputTimingActive) return
        SeedlessCore.releaseOutputTiming()
        outputTimingActive = false
    }

    private fun releaseSurface() {
        releaseOutputTiming()
        val restored = outputPipeline?.release() ?: true
        outputPipeline = null
        if (restored) {
            releaseOutputTarget()
            outputBlitter.release()
        } else {
            outputFbo = 0; outputTex = 0; outputW = 0; outputH = 0
            outputBlitter.forget()
        }
        outputUnavailable = false
        surface?.let { EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT); runCatching { EGL14.eglDestroySurface(dpy, it) } }
        surface = null; surfaceGen = -1; chainReady = false; surfacePrepared = false
    }

    private fun release() {
        closeQueuedOutput()
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

    private fun prepareChain(width: Int, height: Int, r: FloatArray, sw: Int, sh: Int, target: Boolean) {
        val cfgApp = SettingsRepo.current
        if (extVbo == 0) {
            val buf = IntArray(1); GLES20.glGenBuffers(1, buf, 0); extVbo = buf[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 192, null, GLES20.GL_DYNAMIC_DRAW)
            writeVbo(48, QUAD_FULL); writeVbo(96, UV_EXT)
        } else GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
        if (chainReady && cfgApp.filter == chainFilter && sw == chainSw && sh == chainSh &&
            width == chainW && height == chainH && r.contentEquals(chainRect) && target == chainTarget) return
        val quad = quadNdc(width, height, r)
        if (target) for (i in 1 until quad.size step 2) quad[i] = -quad[i]
        writeVbo(0, quad)
        if (!chainReady || cfgApp.filter != chainFilter) {
            val fxPath = PathCache.SYS_PREFIX + "shaders/${cfgApp.filter}.dfx"
            val fxResult = SeedlessCore.extfxLoad(fxPath, 0, 96)
            if (fxResult != 0) com.seedlessds.app.AppLog.w("reconDS", "extfxLoad failed ($fxResult): $fxPath, using the default shader")
            chainFilter = cfgApp.filter
        }
        SeedlessCore.extfxSetup(sw, sh, 0, 0, width, height)
        chainSw = sw; chainSh = sh; chainW = width; chainH = height; chainRect = r.copyOf(); chainTarget = target; chainReady = true
        com.seedlessds.app.AppLog.i("reconDS", "EXTERNAL-THREAD chain ${cfgApp.filter} source ${sw}x${sh} surface ${width}x${height}")
    }

    private fun releaseOutputTarget() {
        if (outputFbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(outputFbo), 0)
        if (outputTex != 0) GLES20.glDeleteTextures(1, intArrayOf(outputTex), 0)
        outputFbo = 0; outputTex = 0; outputW = 0; outputH = 0
    }

    private fun prepareOutputTarget(width: Int, height: Int): Boolean {
        if (outputUnavailable) return false
        if (outputFbo != 0 && outputW == width && outputH == height) return true
        releaseOutputTarget()
        val previous = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, previous, 0)
        val unit = previous[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previous, 0)
        val texture = previous[0]
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); outputTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenFramebuffers(1, ids, 0); outputFbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, outputTex, 0)
        val complete = outputTex != 0 && outputFbo != 0 &&
            GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glActiveTexture(unit)
        if (!complete) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            releaseOutputTarget()
            outputUnavailable = true
            com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL-THREAD: output target unavailable")
            return false
        }
        outputW = width; outputH = height
        return true
    }

    private fun copyOutputTarget(snap: ExternalSurface.Snap, hasInput: Boolean): EGLSync? {
        val consumerFence = EGL15.eglCreateSync(dpy, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        var copied = false
        try {
            if (hasInput && (consumerFence == null || consumerFence == EGL15.EGL_NO_SYNC)) GLES20.glFinish()
            GLES20.glFlush()
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            outputBlitter.draw(outputTex, floatArrayOf(0f, 0f, snap.width.toFloat(), snap.height.toFloat()), snap.height, flipY = true)
            copied = true
            return consumerFence
        } finally {
            if (!copied && consumerFence != null && consumerFence != EGL15.EGL_NO_SYNC)
                runCatching { EGL15.eglDestroySync(dpy, consumerFence) }
        }
    }

    private fun drawIt(snap: ExternalSurface.Snap, n: Long, v: EGLSync?, top: Int, bottom: Int, sw: Int, sh: Int): Boolean {
        if (!ensureSurface(snap)) { v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }; return false }
        if (n == lastDrawn) { repeated++; return false }
        val submittedOutputToken = activeOutputToken
        val useOutputFence = submittedOutputToken != 0L
        if (!useOutputFence && outputPipeline != null) {
            val restored = outputPipeline!!.release()
            outputPipeline = null
            if (!restored) {
                v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
                releaseSurface()
                return false
            }
        }
        val pipeline = if (useOutputFence) outputPipeline ?: ExternalOutputPipeline(dpy, ctx!!, cfg, surface!!).also { outputPipeline = it } else null
        val separate = pipeline?.prepare(snap.width, snap.height) == true
        if (pipeline != null && !separate && !pipeline.restoreWindow()) {
            v?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
            releaseSurface()
            return false
        }
        v?.let { EGL15.eglWaitSync(dpy, it, 0); runCatching { EGL15.eglDestroySync(dpy, it) } }
        val r = ExternalLayout.rect(snap.width, snap.height)
        val target = separate || useOutputFence && prepareOutputTarget(snap.width, snap.height)
        prepareChain(snap.width, snap.height, r, sw, sh, target)
        val framebuffer = if (separate) pipeline!!.framebuffer else if (target) outputFbo else 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        if (target) com.seedlessds.app.SeedlessBridge.fboDest(framebuffer)
        GLES20.glViewport(0, 0, snap.width, snap.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        if (target || !useOutputFence || !surfacePrepared) GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        surfacePrepared = false
        val cfgApp = SettingsRepo.current
        val screen = (cfgApp.extDisplayScreen xor (if (cfgApp.swapScreens) 1 else 0)) and 1
        val tex = if (screen == 1) bottom else top
        try {
            if (tex != 0) {
                GLES20.glDisable(GLES20.GL_BLEND)
                SeedlessCore.extfxRender(tex, screen, 0, 6, r[2].toInt().coerceAtLeast(1), r[3].toInt().coerceAtLeast(1))
                if (!useOutputFence) GLES20.glFinish()
            }
        } finally {
            if (target) com.seedlessds.app.SeedlessBridge.fboDest(0)
        }
        if (!useOutputFence) completeOutput(tex != 0)
        val own = if (separate) {
            pipeline!!.copy(releaseInputFence, outputConsumed, tex != 0)
            null
        }
            else if (target) copyOutputTarget(snap, tex != 0)
            else EGL15.eglCreateSync(dpy, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        if (separate && pipeline!!.copyFailed) {
            releaseSurface()
            return false
        }
        if (useOutputFence) {
            if (!target && tex != 0 && (own == null || own == EGL15.EGL_NO_SYNC)) GLES20.glFinish()
            completeOutput(tex != 0)
            if (tex != 0) SeedlessCore.recordOutputBufferAge()
        }
        GLES20.glFlush()
        if (!inputReleased) {
            externalFence?.let { runCatching { EGL15.eglDestroySync(dpy, it) } }
            externalFence = own
        }
        if (useOutputFence && tex != 0) SeedlessCore.refreshOutputGpu(submittedOutputToken)
        val swapped = EGL14.eglSwapBuffers(dpy, surface)
        if (!swapped) { com.seedlessds.app.AppLog.e("reconDS", "EXTERNAL-THREAD: swap 0x${Integer.toHexString(EGL14.eglGetError())}"); releaseSurface() }
        lastDrawn = n
        recordSwap()
        return useOutputFence && tex != 0 && swapped
    }

    private fun recordSwap() {
        drawn++
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

    private fun prepareNextSurface(snap: ExternalSurface.Snap) {
        if (surface == null || snap.generation != surfaceGen || snap.width != surfaceW || snap.height != surfaceH) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, snap.width, snap.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glFlush()
        surfacePrepared = true
    }



    fun acquire(top: Int, bottom: Int, waitForPending: Boolean = false): EGLSync? = synchronized(lock) {
        while (!quit && (busyTop == top || busyBottom == bottom
                || (waitForPending && pending && (topTex == top || bottomTex == bottom)))) runCatching { lock.wait(20) }
        val v = externalFence; externalFence = null; v
    }
}

private class ExternalOutputPipeline(
    private val display: EGLDisplay,
    private val filterContext: EGLContext,
    private val config: EGLConfig,
    private val window: EGLSurface,
) {
    private var copyContext: EGLContext? = null
    private var filterSurface: EGLSurface? = null
    private var framebuffers = IntArray(0)
    private var textures = IntArray(0)
    private val copyFences = arrayOfNulls<EGLSync>(2)
    private val blitter = DirectBlit()
    private var width = 0; private var height = 0; private var index = 0
    private var unavailable = false
    var copyFailed = false
        private set
    val framebuffer: Int get() = framebuffers[index]

    fun restoreWindow(): Boolean = EGL14.eglMakeCurrent(display, window, window, filterContext)

    private fun disable(): Boolean {
        release()
        unavailable = true
        com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL-THREAD: separate output context unavailable")
        return false
    }

    fun prepare(w: Int, h: Int): Boolean {
        if (unavailable) return false
        copyFailed = false
        if (copyContext != null && (width != w || height != h)) release()
        if (copyContext == null) {
            val kinds = IntArray(1)
            if (!EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_SURFACE_TYPE, kinds, 0)
                || kinds[0] and EGL14.EGL_PBUFFER_BIT == 0) return disable()
            val p = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (p == null || p == EGL14.EGL_NO_SURFACE) return disable()
            filterSurface = p
            val c = EGL14.eglCreateContext(display, config, filterContext, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (c == null || c == EGL14.EGL_NO_CONTEXT) return disable()
            copyContext = c
            if (!EGL14.eglMakeCurrent(display, p, p, filterContext)) return disable()
            val previous = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, previous, 0)
            val unit = previous[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previous, 0)
            val texture = previous[0]
            framebuffers = IntArray(2); textures = IntArray(2)
            GLES20.glGenFramebuffers(2, framebuffers, 0)
            GLES20.glGenTextures(2, textures, 0)
            var complete = true
            for (i in 0..1) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[i], 0)
                complete = complete && framebuffers[i] != 0 && textures[i] != 0
                    && GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glActiveTexture(unit)
            if (!complete) return disable()
            width = w; height = h
            com.seedlessds.app.AppLog.i("reconDS", "EXTERNAL-THREAD: separate output context ${w}x${h}")
        } else if (!EGL14.eglMakeCurrent(display, filterSurface, filterSurface, filterContext)) return disable()
        copyFences[index]?.let {
            EGL15.eglWaitSync(display, it, 0)
            runCatching { EGL15.eglDestroySync(display, it) }
            copyFences[index] = null
        }
        return true
    }

    fun copy(releaseInput: (EGLSync?) -> Unit, outputConsumed: (Boolean) -> Unit, hasTexture: Boolean) {
        var consumed = EGL15.eglCreateSync(display, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        try {
            if (consumed == null || consumed == EGL15.EGL_NO_SYNC) GLES20.glFinish()
            GLES20.glFlush()
            if (!EGL14.eglMakeCurrent(display, window, window, copyContext)) {
                copyFailed = true
                com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL-THREAD: copy context makeCurrent failed")
                return
            }
            if (consumed != null && consumed != EGL15.EGL_NO_SYNC && !EGL15.eglWaitSync(display, consumed, 0)) {
                copyFailed = true
                com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL-THREAD: copy fence wait failed")
                return
            }
            releaseInput(if (consumed == EGL15.EGL_NO_SYNC) null else consumed)
            consumed = null
            outputConsumed(hasTexture)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            blitter.draw(textures[index], floatArrayOf(0f, 0f, width.toFloat(), height.toFloat()), height, flipY = true)
            val used = EGL15.eglCreateSync(display, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
            if (used == null || used == EGL15.EGL_NO_SYNC) GLES20.glFinish()
            else copyFences[index] = used
            index = (index + 1) % 2
        } finally {
            if (consumed != null && consumed != EGL15.EGL_NO_SYNC)
                runCatching { EGL15.eglDestroySync(display, consumed) }
        }
    }

    fun release(): Boolean {
        val p = filterSurface
        val c = copyContext
        if (c != null && p != null && EGL14.eglMakeCurrent(display, p, p, c)) {
            GLES20.glFinish()
            blitter.release()
        } else blitter.forget()
        if (p != null && EGL14.eglMakeCurrent(display, p, p, filterContext)) {
            for (fence in copyFences) if (fence != null) EGL15.eglWaitSync(display, fence, 0)
            GLES20.glFinish()
            if (framebuffers.isNotEmpty()) GLES20.glDeleteFramebuffers(framebuffers.size, framebuffers, 0)
            if (textures.isNotEmpty()) GLES20.glDeleteTextures(textures.size, textures, 0)
        }
        for (i in copyFences.indices) {
            copyFences[i]?.let { runCatching { EGL15.eglDestroySync(display, it) } }
            copyFences[i] = null
        }
        framebuffers = IntArray(0); textures = IntArray(0)
        width = 0; height = 0; index = 0
        val restored = restoreWindow()
        if (!restored) EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (c != null) EGL14.eglDestroyContext(display, c)
        if (p != null) EGL14.eglDestroySurface(display, p)
        copyContext = null; filterSurface = null
        return restored
    }
}
