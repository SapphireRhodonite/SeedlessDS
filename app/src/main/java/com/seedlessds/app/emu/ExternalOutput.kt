package com.seedlessds.app.emu

import android.opengl.EGL14
import android.opengl.EGL15
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLSync
import android.opengl.GLES20
import com.seedlessds.core.SeedlessCore
import java.util.concurrent.CountDownLatch

internal class ExternalOutput private constructor(
    private val display: EGLDisplay,
    private val filterContext: EGLContext,
    private val config: EGLConfig,
    private val geometry: ExternalSurface.Geometry,
    private val filterSurface: EGLSurface,
    private val onSwap: () -> Unit,
) {
    private data class Frame(val texture: Int, val token: Long, val hasTexture: Boolean, val ready: EGLSync?)

    private val lock = Object()
    private val started = CountDownLatch(1)
    private val framebuffers = IntArray(2)
    private val textures = IntArray(2)
    private val consumed = HashMap<Int, EGLSync>()
    private var index = 0
    private var pending: Frame? = null
    private var busyTexture = 0
    private var nativeOutput = false
    private var closing = false
    private var closed = false
    private var failure: Throwable? = null
    private val worker = Thread({ run() }, "DsExternalCopy")

    val framebuffer: Int get() = framebuffers[index]

    fun matches(value: ExternalSurface.Geometry): Boolean = geometry == value

    fun acquire() {
        val fence = synchronized(lock) {
            while (pending?.texture == textures[index] || busyTexture == textures[index]) {
                checkRunning()
                lock.wait()
            }
            checkRunning()
            consumed.remove(textures[index])
        }
        waitFence(fence)
    }

    fun submit(token: Long, hasTexture: Boolean, releaseInput: (EGLSync?) -> Unit) {
        synchronized(lock) {
            while (pending != null) {
                checkRunning()
                lock.wait()
            }
            checkRunning()
        }
        var source: EGLSync? = null
        var ready: EGLSync? = null
        var accepted = false
        try {
            source = newFence()
            ready = newFence()
            if (source == null || ready == null) GLES20.glFinish()
            GLES20.glFlush()
            synchronized(lock) {
                checkRunning()
                check(pending == null)
                if (nativeOutput && hasTexture) SeedlessCore.recordOutputReady(token)
                pending = Frame(textures[index], token, hasTexture, ready)
                ready = null
                accepted = true
                index = (index + 1) % textures.size
                lock.notifyAll()
            }
            releaseInput(source)
            source = null
        } finally {
            source?.let { EGL15.eglDestroySync(display, it) }
            if (!accepted) ready?.let { EGL15.eglDestroySync(display, it) }
        }
    }

    fun close() {
        if (closed) return
        synchronized(lock) { closing = true; lock.notifyAll() }
        var interrupted = false
        while (worker.isAlive) {
            try { worker.join() } catch (_: InterruptedException) { interrupted = true }
        }
        val current = EGL14.eglMakeCurrent(display, filterSurface, filterSurface, filterContext)
        if (current) {
            GLES20.glFinish()
            GLES20.glDeleteFramebuffers(framebuffers.size, framebuffers, 0)
            GLES20.glDeleteTextures(textures.size, textures, 0)
        }
        consumed.values.forEach { EGL15.eglDestroySync(display, it) }
        consumed.clear()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, filterSurface)
        closed = true
        if (interrupted) Thread.currentThread().interrupt()
        check(current) { "External output could not restore its filter context" }
    }

    private fun checkRunning() {
        failure?.let { throw IllegalStateException("External output failed", it) }
        check(!closing && !closed) { "External output is closed" }
    }

    private fun newFence(): EGLSync? {
        val fence = EGL15.eglCreateSync(display, EGL15.EGL_SYNC_FENCE, longArrayOf(EGL14.EGL_NONE.toLong()), 0)
        return if (fence == EGL15.EGL_NO_SYNC) null else fence
    }

    private fun waitFence(fence: EGLSync?) {
        if (fence == null) return
        try {
            if (!EGL15.eglWaitSync(display, fence, 0)) {
                check(EGL15.eglClientWaitSync(display, fence, 0, EGL15.EGL_FOREVER) == EGL15.EGL_CONDITION_SATISFIED) {
                    "External output fence wait failed"
                }
            }
        } finally {
            EGL15.eglDestroySync(display, fence)
        }
    }

    private fun allocateTargets(): Boolean {
        val value = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, value, 0)
        val unit = value[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, value, 0)
        val texture = value[0]
        GLES20.glGenFramebuffers(framebuffers.size, framebuffers, 0)
        GLES20.glGenTextures(textures.size, textures, 0)
        var complete = true
        for (i in textures.indices) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, geometry.width, geometry.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[i], 0)
            complete = complete && framebuffers[i] != 0 && textures[i] != 0 &&
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glActiveTexture(unit)
        return complete
    }

    private fun matches(snap: ExternalSurface.Snap?): Boolean = snap != null &&
        snap.generation == geometry.generation && snap.width == geometry.width && snap.height == geometry.height

    private fun run() {
        var context: EGLContext? = null
        var window: EGLSurface? = null
        var blitter: DirectBlit? = null
        var current = false
        var timingActive = false
        var surfaceOutput = 0L
        var surfaceFramebuffer = 0
        try {
            val copy = EGL14.eglCreateContext(display, config, filterContext, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(copy != null && copy != EGL14.EGL_NO_CONTEXT) { "External copy context unavailable" }
            context = copy
            if (android.os.Build.VERSION.SDK_INT >= 31 && geometry.intervalNs > 0L) {
                val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                    window = surface
                    if (EGL14.eglMakeCurrent(display, surface, surface, copy)) {
                        current = true
                        ExternalSurface.withSurface { snap ->
                            check(matches(snap)) { "External surface changed during output creation" }
                            surfaceOutput = SeedlessCore.createSurfaceOutput(snap!!.surface, geometry.width, geometry.height, geometry.intervalNs)
                        }
                    }
                    if (surfaceOutput == 0L) {
                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        current = false
                        EGL14.eglDestroySurface(display, surface)
                        window = null
                    } else {
                        surfaceFramebuffer = SeedlessCore.acquireSurfaceOutput(surfaceOutput)
                        check(surfaceFramebuffer != 0) { "External buffer unavailable" }
                    }
                }
            }
            if (surfaceOutput == 0L) ExternalSurface.withSurface { snap ->
                check(matches(snap)) { "External surface changed during output creation" }
                val surface = EGL14.eglCreateWindowSurface(display, config, snap!!.surface, intArrayOf(EGL14.EGL_NONE), 0)
                check(surface != null && surface != EGL14.EGL_NO_SURFACE) { "External copy window unavailable" }
                window = surface
                check(EGL14.eglMakeCurrent(display, surface, surface, copy)) { "External copy context could not become current" }
                current = true
            }
            val activeWindow = checkNotNull(window)
            val target = DirectBlit().also { blitter = it }
            nativeOutput = surfaceOutput != 0L
            started.countDown()
            while (true) {
                val frame = synchronized(lock) {
                    while (pending == null && !closing) lock.wait()
                    val next = pending ?: return
                    pending = null
                    busyTexture = next.texture
                    lock.notifyAll()
                    next
                }
                var ready = frame.ready
                var used: EGLSync? = null
                var token = frame.token
                var prepareNext = false
                try {
                    ExternalSurface.withSurface { snap ->
                        if (!matches(snap)) return@withSurface
                        val wait = ready
                        ready = null
                        waitFence(wait)
                        val complete = token
                        token = 0L
                        if (frame.hasTexture) timingActive = true
                        if (surfaceOutput != 0L) SeedlessCore.completeSurfaceOutputFrame(surfaceOutput, complete, frame.hasTexture)
                        else SeedlessCore.completeOutputFrame(complete, frame.hasTexture)
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, surfaceFramebuffer)
                        target.draw(frame.texture, floatArrayOf(0f, 0f, geometry.width.toFloat(), geometry.height.toFloat()), geometry.height, flipY = true)
                        used = newFence()
                        if (used == null) GLES20.glFinish()
                        if (surfaceOutput == 0L && frame.hasTexture) SeedlessCore.recordOutputBufferAge()
                        GLES20.glFlush()
                        val old = synchronized(lock) {
                            val previous = consumed.remove(frame.texture)
                            used?.let { consumed[frame.texture] = it }
                            used = null
                            busyTexture = 0
                            lock.notifyAll()
                            previous
                        }
                        old?.let { EGL15.eglDestroySync(display, it) }
                        if (frame.hasTexture) {
                            if (surfaceOutput != 0L) SeedlessCore.refreshSurfaceOutputGpu(surfaceOutput, frame.token)
                            else SeedlessCore.refreshOutputGpu(frame.token)
                        }
                        if (surfaceOutput != 0L) {
                            check(SeedlessCore.presentSurfaceOutput(surfaceOutput, if (frame.hasTexture) frame.token else 0L)) { "External buffer submission failed" }
                        } else check(EGL14.eglSwapBuffers(display, activeWindow)) { "External copy swap failed" }
                        onSwap()
                        prepareNext = frame.hasTexture || surfaceOutput != 0L
                    }
                    if (prepareNext) {
                        if (surfaceOutput != 0L) {
                            if (ExternalSurface.geometry == geometry) {
                                surfaceFramebuffer = SeedlessCore.acquireSurfaceOutput(surfaceOutput)
                                check(surfaceFramebuffer != 0) { "External buffer unavailable" }
                            }
                        } else ExternalSurface.withSurface { snap ->
                            if (matches(snap)) {
                                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                                GLES20.glViewport(0, 0, geometry.width, geometry.height)
                                GLES20.glClearColor(0f, 0f, 0f, 1f)
                                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                                GLES20.glFlush()
                            }
                        }
                    }
                } finally {
                    ready?.let { EGL15.eglDestroySync(display, it) }
                    used?.let { EGL15.eglDestroySync(display, it) }
                    if (token != 0L) SeedlessCore.completeOutputFrame(token, false)
                    synchronized(lock) { busyTexture = 0; lock.notifyAll() }
                }
            }
        } catch (error: Throwable) {
            synchronized(lock) { failure = error; lock.notifyAll() }
            com.seedlessds.app.AppLog.w("reconDS", "External output: ${error.message}")
        } finally {
            started.countDown()
            try {
                if (current) {
                    GLES20.glFinish()
                    if (surfaceOutput != 0L) SeedlessCore.closeSurfaceOutput(surfaceOutput)
                    blitter?.release()
                }
            } finally {
                if (timingActive && surfaceOutput == 0L) SeedlessCore.releaseOutputTiming()
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                window?.let { EGL14.eglDestroySurface(display, it) }
                context?.let { EGL14.eglDestroyContext(display, it) }
                val abandoned = synchronized(lock) {
                    pending.also { pending = null; busyTexture = 0; lock.notifyAll() }
                }
                abandoned?.ready?.let { EGL15.eglDestroySync(display, it) }
                abandoned?.let { SeedlessCore.completeOutputFrame(it.token, false) }
            }
        }
    }

    companion object {
        fun open(display: EGLDisplay, context: EGLContext, config: EGLConfig, geometry: ExternalSurface.Geometry, onSwap: () -> Unit): ExternalOutput? {
            val value = IntArray(1)
            if (!EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_SURFACE_TYPE, value, 0) || value[0] and EGL14.EGL_PBUFFER_BIT == 0) return null
            val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                EGL14.eglDestroySurface(display, surface)
                return null
            }
            val output = ExternalOutput(display, context, config, geometry, surface, onSwap)
            try {
                if (!output.allocateTargets()) {
                    output.close()
                    return null
                }
                output.worker.start()
                output.started.await()
            } catch (error: Throwable) {
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
