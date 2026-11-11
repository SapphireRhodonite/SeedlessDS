package com.seedlessds.app.emu

import android.opengl.EGL14
import android.opengl.EGL15
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLSync
import android.opengl.GLES20
import java.util.concurrent.CountDownLatch

internal class InternalOutput private constructor(
    private val display: EGLDisplay,
    private val producerContext: EGLContext,
    private val producerSurface: EGLSurface,
    private val window: EGLSurface,
    private val readSurface: EGLSurface,
    private val copyContext: EGLContext,
    private val onFrameSwapped: () -> Unit,
) {
    private data class Frame(
        val texture: Int,
        val width: Int,
        val height: Int,
        val overlay: SurfaceOverlayFrame?,
        val ready: EGLSync?,
        val emulated: Boolean,
    )

    data class SwapTime(val swap: Long, val after: Long)

    private val lock = Object()
    private val started = CountDownLatch(1)
    private var pending: Frame? = null
    private var busyTexture = 0
    private var inFlight = false
    private var closing = false
    private var closed = false
    private var failure: Throwable? = null
    private var lastSwap: SwapTime? = null
    private val consumed = HashMap<Int, EGLSync>()
    private val worker = Thread({ run() }, "DsInternalCopy")

    fun acquire(texture: Int) {
        val fence = synchronized(lock) {
            while (pending?.texture == texture || busyTexture == texture) {
                checkRunning()
                lock.wait()
            }
            checkRunning()
            consumed.remove(texture)
        }
        waitFence(fence, false)
    }

    fun submit(texture: Int, width: Int, height: Int, overlay: SurfaceOverlayFrame?, emulated: Boolean) {
        val ready = createFence()
        var accepted = false
        try {
            synchronized(lock) {
                while (pending != null) {
                    checkRunning()
                    lock.wait()
                }
                checkRunning()
                pending = Frame(texture, width, height, overlay, ready, emulated)
                accepted = true
                lock.notifyAll()
            }
        } finally {
            if (!accepted && ready != null) EGL15.eglDestroySync(display, ready)
        }
    }

    fun drain() {
        synchronized(lock) {
            while (pending != null || inFlight) {
                checkRunning()
                lock.wait()
            }
            checkRunning()
        }
        while (true) {
            val fence = synchronized(lock) { consumed.keys.firstOrNull()?.let { consumed.remove(it) } } ?: break
            waitFence(fence, true)
        }
    }

    fun takeSwapTime(): SwapTime? = synchronized(lock) {
        lastSwap.also { lastSwap = null }
    }

    fun close(restoreWindow: Boolean = true) {
        if (closed) return
        synchronized(lock) { closing = true; lock.notifyAll() }
        var interrupted = false
        while (worker.isAlive) {
            try { worker.join() } catch (_: InterruptedException) { interrupted = true }
        }
        GLES20.glFinish()
        consumed.values.forEach { EGL15.eglDestroySync(display, it) }
        consumed.clear()
        val restored = if (restoreWindow) EGL14.eglMakeCurrent(display, window, readSurface, producerContext)
            else EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (!restored) EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, producerSurface)
        closed = true
        if (interrupted) Thread.currentThread().interrupt()
        check(restored) { "Internal output could not restore the producer window" }
    }

    private fun checkRunning() {
        failure?.let { throw IllegalStateException("Internal output failed", it) }
        check(!closing && !closed) { "Internal output is closed" }
    }

    private fun createFence(): EGLSync? {
        val fence = EGL15.eglCreateSync(display, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        if (fence == null || fence == EGL15.EGL_NO_SYNC) {
            GLES20.glFinish()
            return null
        }
        GLES20.glFlush()
        return fence
    }

    private fun waitFence(fence: EGLSync?, client: Boolean) {
        if (fence == null) return
        try {
            if (client || !EGL15.eglWaitSync(display, fence, 0)) {
                check(EGL15.eglClientWaitSync(display, fence, 0, EGL15.EGL_FOREVER) == EGL15.EGL_CONDITION_SATISFIED) {
                    "Internal output fence wait failed"
                }
            }
        } finally {
            EGL15.eglDestroySync(display, fence)
        }
    }

    private fun run() {
        var blitter: DirectBlit? = null
        var overlayRenderer: SurfaceOverlayRenderer? = null
        var current = false
        try {
            check(EGL14.eglMakeCurrent(display, window, window, copyContext)) { "Internal output context failed" }
            current = true
            val copyBlitter = DirectBlit().also { blitter = it }
            val copyOverlay = SurfaceOverlayRenderer().also { overlayRenderer = it }
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            started.countDown()
            while (true) {
                val frame = synchronized(lock) {
                    while (pending == null && !closing) lock.wait()
                    val next = pending ?: return
                    pending = null
                    busyTexture = next.texture
                    inFlight = true
                    lock.notifyAll()
                    next
                }
                waitFence(frame.ready, false)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glViewport(0, 0, frame.width, frame.height)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                copyBlitter.draw(frame.texture, floatArrayOf(0f, 0f, frame.width.toFloat(), frame.height.toFloat()), frame.height, flipY = false)
                frame.overlay?.let { copyOverlay.draw(it, frame.height) }
                val fence = createFence()
                val previous = synchronized(lock) {
                    val old = consumed.remove(frame.texture)
                    if (fence != null) consumed[frame.texture] = fence
                    busyTexture = 0
                    lock.notifyAll()
                    old
                }
                if (previous != null) EGL15.eglDestroySync(display, previous)
                val before = android.os.SystemClock.elapsedRealtimeNanos()
                check(EGL14.eglSwapBuffers(display, window)) { "Internal output swap failed" }
                val after = android.os.SystemClock.elapsedRealtimeNanos()
                if (frame.emulated) {
                    synchronized(lock) { lastSwap = SwapTime(after - before, android.os.SystemClock.elapsedRealtimeNanos() - after) }
                    onFrameSwapped()
                }
                synchronized(lock) { inFlight = false; lock.notifyAll() }
            }
        } catch (error: Throwable) {
            synchronized(lock) { failure = error; lock.notifyAll() }
            com.seedlessds.app.AppLog.e("DsGlView", "Internal output: ${error.message}")
        } finally {
            started.countDown()
            if (current) {
                GLES20.glFinish()
                overlayRenderer?.release()
                blitter?.release()
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            EGL14.eglDestroyContext(display, copyContext)
            synchronized(lock) {
                pending?.ready?.let { EGL15.eglDestroySync(display, it) }
                pending = null
                busyTexture = 0
                inFlight = false
                lock.notifyAll()
            }
        }
    }

    companion object {
        fun open(onFrameSwapped: () -> Unit): InternalOutput? {
            val display = EGL14.eglGetCurrentDisplay()
            val context = EGL14.eglGetCurrentContext()
            val window = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
            val value = IntArray(1)
            if (!EGL14.eglQueryContext(display, context, EGL14.EGL_CONFIG_ID, value, 0)) return null
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            if (!EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_CONFIG_ID, value[0], EGL14.EGL_NONE), 0, configs, 0, 1, value, 0) || value[0] == 0) return null
            val config = configs[0] ?: return null
            if (!EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_SURFACE_TYPE, value, 0) || value[0] and EGL14.EGL_PBUFFER_BIT == 0) return null
            val pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (pbuffer == null || pbuffer == EGL14.EGL_NO_SURFACE) return null
            val copy = EGL14.eglCreateContext(display, config, context, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (copy == null || copy == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroySurface(display, pbuffer)
                return null
            }
            GLES20.glFinish()
            if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) {
                EGL14.eglDestroyContext(display, copy)
                EGL14.eglDestroySurface(display, pbuffer)
                return null
            }
            val output = InternalOutput(display, context, pbuffer, window, read, copy, onFrameSwapped)
            try {
                output.worker.start()
                output.started.await()
            } catch (error: Throwable) {
                if (output.worker.state == Thread.State.NEW) EGL14.eglDestroyContext(display, copy)
                output.close()
                if (error is InterruptedException) Thread.currentThread().interrupt()
                throw error
            }
            if (synchronized(output.lock) { output.failure != null }) {
                output.close()
                return null
            }
            return output
        }
    }
}
