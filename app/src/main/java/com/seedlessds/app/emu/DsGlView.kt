package com.seedlessds.app.emu

import com.seedlessds.core.SeedlessCore

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.seedlessds.app.filesystem.PathCache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class DsGlView(context: Context) : SurfaceView(context) {

    private val renderer = DsRenderer()
    private val thread = RenderThread()

    val bottomScreenRect: FloatArray get() = renderer.bottomScreenRect
    val topScreenRect: FloatArray get() = renderer.topScreenRect

    fun applyDisplaySettings() = queueEvent { renderer.reapplyDisplay() }

    @Volatile var onFirstFrame: (() -> Unit)? = null

    @Volatile private var padState = 0
    @Volatile private var touchActive = false
    @Volatile private var touchPacked = 0
    @Volatile private var autofireMask = 0

    private fun pushInput() {
        SeedlessCore.input(padState or (if (touchActive) 0x80000000.toInt() else 0), touchPacked, autofireMask)
    }

    fun toggleRapidFire() {
        autofireMask = (padState and 0x3F0) xor autofireMask
        pushInput()
    }

    fun applyFastForward() {
        SeedlessCore.applyConfig(SettingsRepo.current.pack())
    }

    fun pushTouch(active: Boolean, packed: Int) { touchActive = active; touchPacked = packed; pushInput() }

    fun setButton(index: Int, pressed: Boolean) {
        if (index < 0) return
        padState = if (pressed) padState or (1 shl index) else padState and (1 shl index).inv()
        pushInput()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = renderer.bottomScreenRect
        if (r[2] <= 0f || r[3] <= 0f) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val lx = (event.x - r[0]) / r[2]
                val ly = (event.y - r[1]) / r[3]
                if (lx in 0f..1f && ly in 0f..1f) {
                    val dx = (lx * 255f).toInt().coerceIn(0, 255)
                    val dy = (ly * 191f).toInt().coerceIn(0, 191)
                    touchActive = true; touchPacked = (dx shl 16) or dy; pushInput()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchActive = false; touchPacked = 0; pushInput()
            }
        }
        return true
    }

    init {
        keepScreenOn = true
        holder.setFormat(PixelFormat.RGB_565)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}
            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) =
                thread.onSurfaceReady(h.surface, w, hh)
            override fun surfaceDestroyed(h: SurfaceHolder) = thread.onSurfaceGone()
        })
        thread.start()
    }

    fun queueEvent(work: Runnable) = thread.post(work)

    fun onPause() {
        SeedlessCore.signalScreen()
        thread.setPaused(true)
    }

    fun onResume() = thread.setPaused(false)

    override fun onDetachedFromWindow() {
        thread.shutdown()
        super.onDetachedFromWindow()
    }

    private inner class RenderThread : Thread("DsRender") {
        private val lock = Object()
        private val events = ArrayDeque<Runnable>()
        private var pending: Surface? = null
        private var pendingW = 0
        private var pendingH = 0
        private var surfaceChanged = false
        private var dropWindow = false
        private var exiting = false
        private var paused = false

        private val egl = EGLContext.getEGL() as EGL10
        private var dpy: EGLDisplay? = null
        private var cfg: EGLConfig? = null
        private var ctx: EGLContext? = null
        private var win: EGLSurface? = null
        private var winW = 0
        private var winH = 0
        private var started = false

        fun onSurfaceReady(surface: Surface, w: Int, h: Int) = synchronized(lock) {
            pending = surface; pendingW = w; pendingH = h; surfaceChanged = true
            lock.notifyAll()
        }

        fun onSurfaceGone() {
            SeedlessCore.signalScreen()
            synchronized(lock) {
                pending = null
                dropWindow = true
                lock.notifyAll()
                val deadline = System.currentTimeMillis() + 800
                while (dropWindow && System.currentTimeMillis() < deadline) {
                    runCatching { lock.wait(100) }
                }
            }
        }

        fun setPaused(value: Boolean) = synchronized(lock) {
            paused = value
            lock.notifyAll()
        }

        fun post(work: Runnable) = synchronized(lock) {
            events.addLast(work)
            lock.notifyAll()
        }

        fun shutdown() {
            SeedlessCore.signalScreen()
            synchronized(lock) { exiting = true; lock.notifyAll() }
            runCatching { join(1000) }
        }

        override fun run() {
            while (true) {
                var surface: Surface? = null
                var w = 0
                var h = 0
                var fresh = false
                var job: Runnable? = null
                synchronized(lock) {
                    while (true) {
                        if (dropWindow) { destroyWindow(); dropWindow = false; lock.notifyAll() }
                        if (exiting) { teardown(); return }
                        if (!paused && pending != null) {
                            surface = pending; w = pendingW; h = pendingH
                            fresh = surfaceChanged; surfaceChanged = false
                            if (win != null && events.isNotEmpty()) job = events.removeFirst()
                            break
                        }
                        runCatching { lock.wait() }
                    }
                }
                if (!ensureWindow(surface!!, w, h, fresh)) {
                    runCatching { sleep(16) }
                    continue
                }
                job?.run()
                val drew = renderer.draw(cfg)
                val tSwap0 = android.os.SystemClock.elapsedRealtimeNanos()
                egl.eglSwapBuffers(dpy, win)
                val tSwap1 = android.os.SystemClock.elapsedRealtimeNanos()
                renderer.probeSwap(tSwap1 - tSwap0, android.os.SystemClock.elapsedRealtimeNanos() - tSwap1)
                if (drew) renderer.afterSwap()
            }
        }

        private fun ensureWindow(surface: Surface, w: Int, h: Int, fresh: Boolean): Boolean {
            if (!initEgl()) return false
            if (win != null && (fresh || !surfaceIsCurrent(surface))) destroyWindow()
            if (win == null) {
                val s = egl.eglCreateWindowSurface(dpy, cfg, surface, null)
                if (s == null || s == EGL10.EGL_NO_SURFACE) {
                    com.seedlessds.app.AppLog.e(TAG, "window surface failed: 0x${Integer.toHexString(egl.eglGetError())}")
                    return false
                }
                win = s
                current = surface
                if (!egl.eglMakeCurrent(dpy, s, s, ctx)) {
                    com.seedlessds.app.AppLog.e(TAG, "makeCurrent failed: 0x${Integer.toHexString(egl.eglGetError())}")
                    destroyWindow()
                    return false
                }
                winW = 0; winH = 0
                if (!started) { renderer.created(cfg); started = true }
            }
            if (w != winW || h != winH) {
                winW = w; winH = h
                renderer.changed(w, h)
            }
            return true
        }

        private var current: Surface? = null
        private fun surfaceIsCurrent(surface: Surface) = current === surface && surface.isValid
        private fun initEgl(): Boolean {
            if (ctx != null) return true
            val d = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (d == null || d == EGL10.EGL_NO_DISPLAY) return false
            if (!egl.eglInitialize(d, IntArray(2))) return false
            val attribs = intArrayOf(
                EGL10.EGL_RED_SIZE, 5,
                EGL10.EGL_GREEN_SIZE, 6,
                EGL10.EGL_BLUE_SIZE, 5,
                EGL10.EGL_ALPHA_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE,
            )
            val out = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            if (!egl.eglChooseConfig(d, attribs, out, 1, n) || n[0] == 0) return false
            val c = out[0] ?: return false
            val context = egl.eglCreateContext(d, c, EGL10.EGL_NO_CONTEXT,
                intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE))
            if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                com.seedlessds.app.AppLog.e(TAG, "no context: 0x${Integer.toHexString(egl.eglGetError())}")
                return false
            }
            dpy = d; cfg = c; ctx = context
            return true
        }

        private fun destroyWindow() {
            val d = dpy ?: return
            win?.let {
                egl.eglMakeCurrent(d, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                runCatching { egl.eglDestroySurface(d, it) }
            }
            win = null
            current = null
        }

        private fun teardown() {
            destroyWindow()
            val d = dpy ?: return
            ctx?.let { runCatching { egl.eglDestroyContext(d, it) } }
            ctx = null
            runCatching { egl.eglTerminate(d) }
            dpy = null
            started = false
        }
    }

    private companion object {
        const val TAG = "DsGlView"
        const val EGL_OPENGL_ES2_BIT = 4
        const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        const val PERIOD_NS = 16_666_667L
    }

    private inner class DsRenderer {
        private val FX_UV_BLOCK = floatArrayOf(
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
        )
        private var topTex = 0
        private var bottomTex = 0
        private var topTex2 = 0
        private var bottomTex2 = 0
        private val doubleTextures: Boolean = false
        private var vbo = 0
        private var viewW = 0
        private var viewH = 0
        private var topPxW = 256; private var topPxH = 192
        private var geoVersion = -1
        private var geoExternal = false
        private var displayKey = ""
        private var botPxW = 256; private var botPxH = 192
        var bottomScreenRect = floatArrayOf(0f, 0f, 0f, 0f)
        var topScreenRect = floatArrayOf(0f, 0f, 0f, 0f)
        private var texHires = false; private var tex16 = false

        private val nativeDeliveryRead: Boolean by lazy {
            com.seedlessds.app.control.RuntimeControl.nativeDelivery
        }
        private fun nativeDelivery(): Boolean = nativeDeliveryRead



        private var pageScaleShown = 0
        private fun srcDims(): Pair<Int, Int> {
            val n = if (!SettingsRepo.current.hires3D) 1
                else if (nativeDelivery()) {
                    val core = SettingsRepo.coreScale
                    if (pageScaleShown > 0) pageScaleShown
                    else if (core > 0) core else SettingsRepo.irScales.getOrElse(SettingsRepo.current.internalRes) { 2 }
                } else 2
            val d = dimsCache
            if (d != null && d.first == 256 * n) return d
            return (256 * n to 192 * n).also { dimsCache = it }
        }
        private var dimsCache: Pair<Int, Int>? = null

        private var externalThread: ExternalThread? = null
        private var frame = 0L
        private val dpy14 get() = android.opengl.EGL14.eglGetCurrentDisplay()

        private var texPages = 0
        private var fboPages = 0

        fun created(config: EGLConfig?) {
            ringForget()
            displayKey = ""
            pageScaleShown = 0; dimsCache = null
            externalThread?.terminate()
            externalThread = null
            texPages = if (runCatching { com.seedlessds.app.SeedlessBridge.pagesActive() }.getOrDefault(false))
                runCatching { com.seedlessds.app.SeedlessBridge.texturePages() }.getOrDefault(0) else 0
            if (texPages != 0) com.seedlessds.app.AppLog.i("reconDS", "ZERO COPY: texture $texPages over the core pages")
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            val tex = IntArray(4)
            GLES20.glGenTextures(4, tex, 0)
            topTex = tex[0]
            bottomTex = tex[1]
            createScreenTexture(topTex)
            createScreenTexture(bottomTex)
            texHires = SettingsRepo.current.hires3D; tex16 = SettingsRepo.current.gl16bit
            SeedlessCore.clearScreens(topTex, bottomTex)
            if (doubleTextures) {
                topTex2 = tex[2]; bottomTex2 = tex[3]
                createScreenTexture(topTex2); createScreenTexture(bottomTex2)
                SeedlessCore.clearScreens(topTex2, bottomTex2)
            }

            val buf = IntArray(1)
            GLES20.glGenBuffers(1, buf, 0)
            vbo = buf[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4416, null, GLES20.GL_DYNAMIC_DRAW)

            val fullQuad = floatArrayOf(-1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)
            writeVbo(96, fullQuad)
            writeVbo(144, fullQuad)
            writeVbo(2208, FX_UV_BLOCK)

            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        private fun createScreenTexture(id: Int) {
            val cfg = SettingsRepo.current
            val (sw, sh) = srcDims()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            if (cfg.gl16bit)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, sw, sh, 0,
                    GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, null)
            else
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, sw, sh, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun changed(width: Int, height: Int) {
            viewW = width
            viewH = height
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            writeScreenGeometry(width, height)

            val fxPath = PathCache.SYS_PREFIX + "shaders/${SettingsRepo.current.filter}.dfx"
            val fxResult = SeedlessCore.fxLoad(fxPath, 0, 2208)
            if (fxResult != 0) com.seedlessds.app.AppLog.w("reconDS", "fxLoad failed ($fxResult): $fxPath, using the default shader")

            val (sw, sh) = srcDims()
            SeedlessCore.fxSetup(sw, sh, 0, 0, width, height)

            SeedlessCore.applyConfig(SettingsRepo.current.pack())
        }

        private fun quadNdc(w: Int, h: Int, r: FloatArray): FloatArray {
            fun ndx(px: Float) = px / w * 2f - 1f
            fun ndy(px: Float) = 1f - px / h * 2f
            val x0 = ndx(r[0]); val x1 = ndx(r[0] + r[2]); val yt = ndy(r[1]); val yb = ndy(r[1] + r[3])
            return floatArrayOf(x0, yt, x0, yb, x1, yb, x0, yt, x1, yb, x1, yt)
        }

        private fun fitAspect(maxW: Float, maxH: Float): Pair<Float, Float> {
            val a = 256f / 192f
            val (w, h) = if (maxW / maxH > a) Pair(maxH * a, maxH) else Pair(maxW, maxW / a)
            if (!SettingsRepo.current.integerScale) return Pair(w, h)
            val factor = kotlin.math.floor(w / 256f).coerceAtLeast(1f)
            return Pair(256f * factor, 192f * factor)
        }

        private fun xform(r: FloatArray, w: Int, h: Int, sc: Float, offX: Float, offY: Float): FloatArray {
            val cx = w / 2f; val cy = h / 2f
            val nx = cx + (r[0] - cx) * sc + offX * w
            val ny = cy + (r[1] - cy) * sc + offY * h
            return floatArrayOf(nx, ny, r[2] * sc, r[3] * sc)
        }

        private fun rawRects(w: Int, h: Int, cfg: DsSettings): Pair<FloatArray, FloatArray?> {
            if (DualScreenPresets.singleScreen(cfg)) {
                if (cfg.dualScreenPreset != DualScreenPresets.OFF) {
                    context.resources.displayMetrics.let { dm ->
                        val own = (context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                        val physical = DeviceProfiles.dpiFor(context, own)
                        DualScreenPresets.publishInternal(DualScreenPresets.panel(w, h, physical ?: dm.xdpi, physical ?: dm.ydpi, dm.densityDpi))
                    }
                    return DualScreenPresets.rect(
                        w.toFloat(), h.toFloat(),
                        keepRatio = cfg.dsKeepRatio,
                        integer = cfg.integerScale,
                        fillW = cfg.dsIntFillW,
                        fillH = cfg.dsIntFillH,
                        align = cfg.dsIntAlign,
                        sameSize = cfg.dsSameSize,
                        own = DualScreenPresets.panelInternal,
                        other = DualScreenPresets.panelExternal,
                    ) to null
                }
                if (cfg.screenLayout == 4) return floatArrayOf(0f, 0f, w.toFloat(), h.toFloat()) to null
                val (sw, sh) = fitAspect(w.toFloat(), h.toFloat())
                return floatArrayOf((w - sw) / 2f, (h - sh) / 2f, sw, sh) to null
            }
            return when (cfg.screenLayout) {
                2 -> {
                    val (sw, sh) = fitAspect(w.toFloat(), h / 2f)
                    val x = (w - sw) / 2f
                    floatArrayOf(x, h / 2f - sh, sw, sh) to floatArrayOf(x, h / 2f, sw, sh)
                }
                1 -> {
                    val (bw, bh) = fitAspect(w * 0.72f, h.toFloat())
                    val top = floatArrayOf(0f, (h - bh) / 2f, bw, bh)
                    val smW = w * 0.24f; val smH = smW / (256f / 192f)
                    val bot = floatArrayOf(w - smW - 8f, h - smH - 8f, smW, smH)
                    top to bot
                }
                4 -> {
                    val halfW = w / 2f
                    floatArrayOf(0f, 0f, halfW, h.toFloat()) to floatArrayOf(halfW, 0f, halfW, h.toFloat())
                }
                else -> {
                    val halfW = w / 2f
                    val (sw, sh) = fitAspect(halfW, h.toFloat())
                    val y = (h - sh) / 2f
                    floatArrayOf((halfW - sw) / 2f, y, sw, sh) to floatArrayOf(halfW + (halfW - sw) / 2f, y, sw, sh)
                }
            }
        }

        private fun writeScreenGeometry(w: Int, h: Int) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            val cfg = SettingsRepo.current
            geoVersion = DualScreenPresets.version
            geoExternal = ExternalSurface.attached
            val geo = FloatArray(24)
            val (rawTop, rawBot) = rawRects(w, h, cfg)
            val zero = floatArrayOf(0f, 0f, 0f, 0f)
            if (rawBot == null) {
                val showingBottom = DualScreenPresets.singleBottom(cfg) != cfg.swapScreens
                val r = if (showingBottom)
                    xform(rawTop, w, h, ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY)
                else
                    xform(rawTop, w, h, ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY)
                System.arraycopy(quadNdc(w, h, r), 0, geo, if (showingBottom) 12 else 0, 12)
                topPxW = r[2].toInt(); topPxH = r[3].toInt()
                botPxW = topPxW; botPxH = topPxH
                if (showingBottom) { bottomScreenRect = r; topScreenRect = zero }
                else { topScreenRect = r; bottomScreenRect = zero }
            } else {
                var slot0 = rawTop
                var slot1 = rawBot
                if (cfg.swapScreens) { val t = slot0; slot0 = slot1; slot1 = t }
                val topRect = xform(slot0, w, h, ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY)
                val botRect = xform(slot1, w, h, ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY)
                System.arraycopy(quadNdc(w, h, topRect), 0, geo, 0, 12)
                System.arraycopy(quadNdc(w, h, botRect), 0, geo, 12, 12)
                topPxW = topRect[2].toInt(); topPxH = topRect[3].toInt()
                botPxW = botRect[2].toInt(); botPxH = botRect[3].toInt()
                topScreenRect = topRect
                bottomScreenRect = botRect
            }
            writeVbo(0, geo)
            writeVbo(2208, FX_UV_BLOCK)
        }




        private fun followPageScale() {
            if (!nativeDelivery() || !SettingsRepo.current.hires3D) return
            val ps = runCatching { SeedlessCore.pageScale() }.getOrDefault(0)
            if (ps < 1 || ps > 8 || ps == pageScaleShown) return
            pageScaleShown = ps; dimsCache = null
            createScreenTexture(topTex); createScreenTexture(bottomTex)
            if (topTex2 != 0) { createScreenTexture(topTex2); createScreenTexture(bottomTex2) }
            val (sw, sh) = srcDims()
            if (viewW != 0 && viewH != 0) SeedlessCore.fxSetup(sw, sh, 0, 0, viewW, viewH)
        }

        private fun uploadPage(pageIdx: Int, scale: Int) {
            val buf = com.seedlessds.app.SeedlessBridge.pageBuffer() ?: return
            if (SettingsRepo.current.gl16bit) return
            val ceiling = SettingsRepo.coreScale.coerceAtLeast(2)
            val pagePair = 2 * 0x30000 * ceiling * ceiling
            val screen = 0x30000 * scale * scale
            val sw = 256 * scale; val sh = 192 * scale
            for (s in 0 until 2) {
                val b = buf.duplicate(); b.position(pageIdx * pagePair + s * screen)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (s == 0) topTex else bottomTex)
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, sw, sh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
            }
        }

        private fun copyPage(pageIdx: Int) {
            val (sw, sh) = srcDims()
            if (fboPages == 0) {
                val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); fboPages = f[0]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPages)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texPages, 0)
                val st = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                com.seedlessds.app.AppLog.i("reconDS", "ZERO COPY: FBO over the page texture, status 0x${Integer.toHexString(st)}")
            } else GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPages)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, topTex)
            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, pageIdx * 2 * sh, sw, sh)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bottomTex)
            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, pageIdx * 2 * sh + sh, sw, sh)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        private fun writeVbo(offsetBytes: Int, data: FloatArray) {
            val b = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            b.asFloatBuffer().put(data).flip()
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, offsetBytes, data.size * 4, b)
        }

        private fun displayKeyOf(cfg: DsSettings): String = listOf(
            viewW, viewH, cfg.filter, cfg.hires3D, cfg.gl16bit, cfg.internalRes, cfg.integerScale, cfg.screenLayout,
            cfg.singleScreen, cfg.singleBottom, cfg.swapScreens, cfg.dualScreenPreset, cfg.dsKeepRatio, cfg.dsIntFillW,
            cfg.dsIntFillH, cfg.dsIntAlign, cfg.dsSameSize, cfg.extDisplayType, cfg.extDisplayScreen,
            ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY,
            ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY,
            DualScreenPresets.version, ExternalSurface.attached,
        ).joinToString("|")

        fun reapplyDisplay() {
            if (viewW == 0 || viewH == 0) return
            val cfg = SettingsRepo.current
            val key = displayKeyOf(cfg)
            if (key == displayKey) return
            displayKey = key
            if (cfg.hires3D != texHires || cfg.gl16bit != tex16) {
                createScreenTexture(topTex); createScreenTexture(bottomTex)
                texHires = cfg.hires3D; tex16 = cfg.gl16bit
                SeedlessCore.clearScreens(topTex, bottomTex)
                if (topTex2 != 0) { createScreenTexture(topTex2); createScreenTexture(bottomTex2); SeedlessCore.clearScreens(topTex2, bottomTex2) }
            }
            val (sw, sh) = srcDims()
            val fxPath = PathCache.SYS_PREFIX + "shaders/${cfg.filter}.dfx"
            val fxResult = SeedlessCore.fxLoad(fxPath, 0, 2208)
            if (fxResult != 0) com.seedlessds.app.AppLog.w("reconDS", "fxLoad failed ($fxResult): $fxPath, using the default shader")
            SeedlessCore.fxSetup(sw, sh, 0, 0, viewW, viewH)
            writeScreenGeometry(viewW, viewH)
        }

        private var presentSet = false
        private val niceEmu: Int? = null
        private fun setPresentMode() {
            if (presentSet) return
            val mode = 3 or (if (texPages != 0) 4 else 0) or (if (niceEmu != null) 8 else 0)
            niceEmu?.let { runCatching { com.seedlessds.app.SeedlessBridge.niceEmu(it) } }
            presentSet = runCatching { com.seedlessds.app.SeedlessBridge.presentMode(mode) }.getOrDefault(false)
        }

        private var ringN = 0; private var ringW = 0; private var ringH = 0; private var ringFull = 0
        private var ringFbo = IntArray(0); private var ringTex = IntArray(0)
        private val ringBlitter = DirectBlit()

        private fun ringForget() {
            ringN = 0; ringW = 0; ringH = 0; ringFull = 0
            ringFbo = IntArray(0); ringTex = IntArray(0)
            ringBlitter.forget()
        }


        private fun ringSlot(n: Int, k: Long): Int {
            if (ringN != n + 1 || ringW != viewW || ringH != viewH) {
                if (ringFbo.isNotEmpty()) { GLES20.glDeleteFramebuffers(ringFbo.size, ringFbo, 0); GLES20.glDeleteTextures(ringTex.size, ringTex, 0) }
                ringN = n + 1; ringW = viewW; ringH = viewH; ringFull = 0
                ringFbo = IntArray(ringN); ringTex = IntArray(ringN)
                GLES20.glGenFramebuffers(ringN, ringFbo, 0); GLES20.glGenTextures(ringN, ringTex, 0)
                for (i in 0 until ringN) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ringTex[i])
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, viewW, viewH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ringFbo[i])
                    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ringTex[i], 0)
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
            return ringFbo[(k % ringN).toInt()]
        }
        private fun ringBlit(n: Int, k: Long) {
            ringFull++
            val read = if (ringFull > n) ((k - n) % ringN).toInt() else (k % ringN).toInt()
            ringBlitter.draw(ringTex[read], floatArrayOf(0f, 0f, viewW.toFloat(), viewH.toFloat()), viewH, flipY = false)
        }

        fun ringDelay(): Int = if (externalThread != null) SettingsRepo.current.extDisplayDelay.coerceIn(0, 2) else 0



        private fun externalThreadAlive(): ExternalThread? {
            if (externalThread == null && ExternalSurface.active) {
                externalThread = ExternalThread(dpy14, android.opengl.EGL14.eglGetCurrentContext()).also { it.start() }
                com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL: own thread started")
            }
            return externalThread
        }

        fun draw(config: EGLConfig?): Boolean {
            setPresentMode()
            externalThreadAlive()
            if (viewW != 0 && viewH != 0 && (geoVersion != DualScreenPresets.version || geoExternal != ExternalSurface.attached)) writeScreenGeometry(viewW, viewH)
            val frameInfo = SeedlessCore.frameWord()
            val i7 = frameInfo and 0xFFFF
            val delayC = ringDelay()
            var fboRet = 0
            if (delayC > 0 && viewW != 0 && viewH != 0) {
                fboRet = ringSlot(delayC, frame)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboRet)
                GLES20.glViewport(0, 0, viewW, viewH)
                com.seedlessds.app.SeedlessBridge.fboDest(fboRet)
            }
            if (fboRet == 0) GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (i7 != 0) { if (fboRet != 0) { com.seedlessds.app.SeedlessBridge.fboDest(0); GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) }; return false }
            val tWait = android.os.SystemClock.elapsedRealtimeNanos()
            SeedlessCore.waitScreen()
            val tUpload = android.os.SystemClock.elapsedRealtimeNanos()
            if (topTex2 != 0) { val a = topTex; val b = bottomTex; topTex = topTex2; bottomTex = bottomTex2; topTex2 = a; bottomTex2 = b }
            GLES20.glDisable(GLES20.GL_BLEND)
            externalThread?.acquire(topTex, bottomTex)?.let { v -> android.opengl.EGL15.eglWaitSync(dpy14, v, 0); runCatching { android.opengl.EGL15.eglDestroySync(dpy14, v) } }
            followPageScale()
            if (texPages != 0) {
                val page = com.seedlessds.app.SeedlessBridge.pageList()
                if (page >= 0) { if (pageScaleShown == 0 || pageScaleShown == SettingsRepo.coreScale) copyPage(page) else uploadPage(page, pageScaleShown) }
            }
            SeedlessCore.fxRender(topTex, bottomTex, 0, 6, 18, topPxW, topPxH, botPxW, botPxH, false)
            if (fboRet != 0) {
                com.seedlessds.app.SeedlessBridge.fboDest(0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glViewport(0, 0, viewW, viewH)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                ringBlit(delayC, frame)
            }
            val ext = externalThread
            if (ext != null) {
                frame++
                val (sw, sh) = srcDims()
                val v = android.opengl.EGL15.eglCreateSync(dpy14, android.opengl.EGL15.EGL_SYNC_FENCE, longArrayOf(android.opengl.EGL14.EGL_NONE.toLong()), 0)
                GLES20.glFlush()
                ext.publish(frame, v, topTex, bottomTex, sw, sh)
            }
            val tEnd = android.os.SystemClock.elapsedRealtimeNanos()
            if (probeTimes && probeTotal in probeSince..(probeSince + 1200)) com.seedlessds.app.AppLog.i("reconDS",
                "T ${probeTotal} ${tWait / 1000} ${(tUpload - tWait) / 1000} ${(tEnd - tUpload) / 1000} ${if (probePrev != 0L) (tWait - probePrev) / 1000 else 0}" +
                " swap ${probeSwapNs / 1000} ${probePresNs / 1000}")
            probeTotal++
            probeSwapNs = 0; probePresNs = 0
            probeWait += tUpload - tWait
            probeDraw += tEnd - tUpload
            if (probePrev != 0L) {
                probeSwap += tWait - probePrev
                val period = tEnd - probePrev
                if (period > pacingMax) pacingMax = period
                when {
                    period < 25_000_000L -> pacing1++
                    period < 41_700_000L -> pacing2++
                    else -> pacing3++
                }
            }
            probePrev = tEnd
            if (pacingFlips0 < 0) pacingFlips0 = runCatching { SeedlessCore.presentFlips() }.getOrDefault(0)
            if (++probeN >= 600) {
                val flips = runCatching { SeedlessCore.presentFlips() }.getOrDefault(0)
                val hud = runCatching { SeedlessCore.hudWord() }.getOrDefault(-1)
                com.seedlessds.app.AppLog.i("pacing",
                    "n=%d 1v=%d 2v=%d 3v+=%d max=%.1fms emu/app=%.2f hud=%s wait=%.2f draw=%.2f swap=%.2f ms source=%dx%d target=%dx%d".format(
                        probeN, pacing1, pacing2, pacing3, pacingMax / 1e6,
                        (flips - pacingFlips0).toDouble() / probeN,
                        if (hud <= 0) "off" else "%.1f%%/%.1f%%".format((hud ushr 16) * 0.0625f, (hud and 0xFFFF) * 0.0625f),
                        probeWait / 1e6 / probeN,
                        probeDraw / 1e6 / probeN,
                        probeSwap / 1e6 / probeN,
                        srcDims().first, srcDims().second,
                        topPxW, topPxH))
                probeN = 0; probeWait = 0; probeDraw = 0; probeSwap = 0
                pacing1 = 0; pacing2 = 0; pacing3 = 0; pacingMax = 0L; pacingFlips0 = flips
            }
            return true
        }

        private var probeN = 0
        private var probeTotal = 0
        private var probeSwapNs = 0L; private var probePresNs = 0L
        fun probeSwap(swap: Long, pres: Long) { probeSwapNs = swap; probePresNs = pres }
        private val probeTimes: Boolean = false
        private val probeSince: Int = 600
        private var probeWait = 0L
        private var probeDraw = 0L
        private var probeSwap = 0L
        private var probePrev = 0L
        private var pacing1 = 0; private var pacing2 = 0; private var pacing3 = 0
        private var pacingMax = 0L; private var pacingFlips0 = -1

        fun afterSwap() {
            com.seedlessds.app.ra.RetroAchievements.doFrame()
            onFirstFrame?.let { cb ->
                onFirstFrame = null
                post { cb() }
            }
        }
    }
}
