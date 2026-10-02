package com.seedlessds.app.emu

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

object ExternalSurface {
    class Snap(val surface: Surface, val width: Int, val height: Int, val generation: Int,
               val control: android.view.SurfaceControl? = null)

    private val lock = Object()
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var generation = 0
    private var control: android.view.SurfaceControl? = null

    @Volatile var active = false
        private set

    @Volatile var attached = false

    fun publish(s: Surface, w: Int, h: Int, c: android.view.SurfaceControl? = null) = synchronized(lock) {
        surface = s; width = w; height = h; generation++; active = true; control = c
    }

    fun clear() = synchronized(lock) {
        surface = null; width = 0; height = 0; generation++; active = false; control = null
    }

    fun <T> withSurface(block: (Snap?) -> T): T = synchronized(lock) {
        val s = surface
        block(if (s != null && s.isValid) Snap(s, width, height, generation, control) else null)
    }
}

object ExternalLayout {
    @Volatile var dpi: FloatArray? = null

    fun rect(viewW: Int, viewH: Int): FloatArray {
        if (viewW <= 0 || viewH <= 0) return floatArrayOf(0f, 0f, 0f, 0f)
        val cfg = SettingsRepo.current
        val fullscreen = cfg.extDisplayType == 2
        var border = (1f - cfg.extDisplayBorder / 100f) * ControlLayout.extScale.coerceIn(0.25f, 1f)
        var fracW = 1f
        var fracH = 1f
        var alignShift = 0f
        if (cfg.dualScreenPreset != DualScreenPresets.OFF) {
            dpi.let { d -> DualScreenPresets.publishExternal(DualScreenPresets.panel(viewW, viewH, d?.get(0) ?: 0f, d?.get(1) ?: 0f, d?.get(2)?.toInt() ?: 0)) }
            if (cfg.dsSameSize) border = 1f
            val r = DualScreenPresets.rect(
                viewW.toFloat(), viewH.toFloat(),
                keepRatio = cfg.dsKeepRatio,
                integer = cfg.integerScale,
                fillW = cfg.dsExtFillW,
                fillH = cfg.dsExtFillH,
                align = cfg.dsExtAlign,
                sameSize = cfg.dsSameSize,
                own = DualScreenPresets.panelExternal,
                other = DualScreenPresets.panelInternal,
            )
            fracW = r[2] / viewW
            fracH = r[3] / viewH
            alignShift = ((viewH - r[3]) / 2f - r[1]) / viewH * 2f
        } else if (!fullscreen) {
            val srcAspect = 256f / 192f
            val dispAspect = viewW.toFloat() / viewH.toFloat()
            if (dispAspect > srcAspect) fracW = srcAspect / dispAspect else fracH = dispAspect / srcAspect
            if (cfg.integerScale) {
                val fitted = viewW * fracW
                val factor = kotlin.math.floor(fitted / 256f).coerceAtLeast(1f)
                fracW = (256f * factor) / viewW
                fracH = (192f * factor) / viewH
            }
        }
        return floatArrayOf(
            (1f - fracW * border) / 2f * viewW,
            ((1f - fracH * border) / 2f - alignShift / 2f) * viewH,
            fracW * border * viewW,
            fracH * border * viewH,
        )
    }
}

class ExtSurfaceView(context: Context) : SurfaceView(context) {

    @Volatile var touchSink: ((Boolean, Int) -> Unit)? = null

    val screenRectPx: FloatArray get() = ExternalLayout.rect(width, height)

    init {
        holder.setFormat(PixelFormat.RGB_565)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}
            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) =
                ExternalSurface.publish(h.surface, w, hh,
                    if (android.os.Build.VERSION.SDK_INT >= 29) this@ExtSurfaceView.surfaceControl else null)
            override fun surfaceDestroyed(h: SurfaceHolder) = ExternalSurface.clear()
        })
        keepScreenOn = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sink = touchSink ?: return false
        if (SettingsRepo.current.extDisplayScreen and 1 != 1) return false
        val r = screenRectPx
        if (r[2] <= 0f || r[3] <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val lx = (event.x - r[0]) / r[2]
                val ly = (event.y - r[1]) / r[3]
                if (lx in 0f..1f && ly in 0f..1f) {
                    val dx = (lx * 255f).toInt().coerceIn(0, 255)
                    val dy = (ly * 191f).toInt().coerceIn(0, 191)
                    sink(true, (dx shl 16) or dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sink(false, 0)
        }
        return true
    }
}

private class ExtEditOverlay(ctx: Context, private val onTap: () -> Unit) : View(ctx) {
    var glRef: ExtSurfaceView? = null
    private var editing = false
    private var selected = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFC107.toInt() }

    fun setState(e: Boolean, s: Boolean) {
        editing = e; selected = s
        visibility = if (e) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val g = glRef ?: return
        if (!editing) return
        val r = g.screenRectPx
        if (r[2] > 0f && r[3] > 0f) {
            paint.strokeWidth = if (selected) 6f else 3f
            paint.alpha = if (selected) 255 else 95
            paint.pathEffect = DashPathEffect(floatArrayOf(if (selected) 32f else 18f, 18f), 0f)
            val inset = paint.strokeWidth / 2f
            canvas.drawRoundRect(r[0] + inset, r[1] + inset, r[0] + r[2] - inset, r[1] + r[3] - inset, 20f, 20f, paint)
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editing && event.actionMasked == MotionEvent.ACTION_DOWN) { onTap(); return true }
        return false
    }
}

private class DsPresentation(
    ctx: Context, display: Display, touchSink: ((Boolean, Int) -> Unit)?, onTap: () -> Unit
) : Presentation(ctx, display) {
    val glView = ExtSurfaceView(ctx).apply { this.touchSink = touchSink }
    private val overlay = ExtEditOverlay(ctx, onTap).apply { glRef = glView; visibility = View.GONE }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        runCatching {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") display.getRealMetrics(dm)
            val physical = DeviceProfiles.dpiFor(context, display)
            ExternalLayout.dpi = floatArrayOf(physical ?: dm.xdpi, physical ?: dm.ydpi, dm.densityDpi.toFloat())
            com.seedlessds.app.AppLog.w("reconDS", "EXTERNAL dpi=${dm.xdpi}x${dm.ydpi} density=${dm.densityDpi} ${dm.widthPixels}x${dm.heightPixels}")
        }
        val root = FrameLayout(context)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window?.let { w ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
            androidx.core.view.WindowInsetsControllerCompat(w, glView).apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    fun setEdit(editing: Boolean, selected: Boolean) = overlay.setState(editing, selected)
    fun applyDisplaySettings() { overlay.invalidate() }
}

class ExternalDisplayManager(private val ctx: Context) {
    private val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var presentation: DsPresentation? = null

    @Volatile var touchSink: ((Boolean, Int) -> Unit)? = null

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (SettingsRepo.current.extDisplayType == 0) return
        dm.registerDisplayListener(listener, null)
        refresh()
    }

    fun stop() {
        runCatching { dm.unregisterDisplayListener(listener) }
        dismiss()
    }

    val isConnected: Boolean get() = presentation != null
    @Volatile var onTap: (() -> Unit)? = null
    @Volatile private var editing = false
    @Volatile private var extSelected = false
    fun pause() {}
    fun resume() {}
    fun applyDisplaySettings() { presentation?.applyDisplaySettings() }
    fun setEdit(editing: Boolean, selected: Boolean) {
        this.editing = editing; this.extSelected = selected
        presentation?.setEdit(editing, selected)
    }

    private fun isRealDisplay(d: Display): Boolean =
        d.state == Display.STATE_ON && (d.flags and Display.FLAG_PRIVATE) == 0

    private fun dismiss() {
        ExternalSurface.clear()
        presentation?.dismiss()
        presentation = null
    }

    private fun refresh() {
        val disp = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { isRealDisplay(it) }
        ExternalSurface.attached = disp != null
        if (disp == null) { dismiss(); return }
        if (presentation?.display?.displayId == disp.displayId) return
        dismiss()
        presentation = DsPresentation(ctx, disp, touchSink) { onTap?.invoke() }.also {
            runCatching { it.show() }; it.setEdit(editing, extSelected)
        }
    }
}
