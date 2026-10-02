package com.seedlessds.app.emu

import com.seedlessds.app.R
import kotlin.math.floor

object DualScreenPresets {
    const val OFF = 0
    const val INTERNAL_TOP = 1
    const val INTERNAL_BOTTOM = 2

    const val LAYOUT_FULLSCREEN = 4
    const val EXT_FULLSCREEN = 2

    const val ALIGN_TOP = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_BOTTOM = 2

    fun labels(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.presets_disabled_manual_layout), uiContext.getString(R.string.presets_top_here_bottom_on_tv), uiContext.getString(R.string.presets_bottom_here_top_on_tv))
    fun alignLabels(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.common_top), uiContext.getString(R.string.presets_centre), uiContext.getString(R.string.common_bottom))


    @Volatile var panelInternal: FloatArray? = null
    @Volatile var panelExternal: FloatArray? = null


    @Volatile var version = 0

    fun panel(w: Int, h: Int, xdpi: Float, ydpi: Float, densityDpi: Int): FloatArray =
        floatArrayOf(w.toFloat(), h.toFloat(), xdpi, ydpi, densityDpi.toFloat())

    @Synchronized fun publishInternal(p: FloatArray) {
        if (panelInternal?.contentEquals(p) == true) return
        panelInternal = p; version++
    }

    @Synchronized fun publishExternal(p: FloatArray) {
        if (panelExternal?.contentEquals(p) == true) return
        panelExternal = p; version++
    }


    fun live(s: DsSettings): Boolean = s.dualScreenPreset != OFF && ExternalSurface.attached

    fun singleScreen(s: DsSettings): Boolean = if (s.dualScreenPreset != OFF) live(s) else s.singleScreen

    fun configuredSingle(s: DsSettings): Boolean = s.dualScreenPreset != OFF || s.singleScreen

    fun singleBottom(s: DsSettings): Boolean = if (s.dualScreenPreset != OFF) s.dualScreenPreset == INTERNAL_BOTTOM else s.singleBottom

    fun apply(s: DsSettings, preset: Int): DsSettings {
        if (preset == OFF) return s.copy(dualScreenPreset = OFF)
        return s.copy(
            dualScreenPreset = preset,
            singleScreen = true,
            singleBottom = preset == INTERNAL_BOTTOM,
            screenLayout = LAYOUT_FULLSCREEN,
            swapScreens = false,
            extDisplayType = EXT_FULLSCREEN,
            extDisplayScreen = if (preset == INTERNAL_TOP) 1 else 0,
        )
    }


    private fun fitted(viewW: Float, viewH: Float, keepRatio: Boolean, integer: Boolean): Pair<Float, Float> {
        var w = viewW
        var h = viewH
        if (keepRatio || integer) {
            val a = 256f / 192f
            if (viewW / viewH > a) { h = viewH; w = viewH * a } else { w = viewW; h = viewW / a }
            if (integer) {
                val factor = floor(w / 256f).coerceAtLeast(1f)
                w = 256f * factor
                h = 192f * factor
            }
        }
        return Pair(w, h)
    }


    private fun ppi(own: FloatArray?, other: FloatArray?): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        if (own == null || other == null || own.size < 5 || other.size < 5) return Pair(Pair(1f, 1f), Pair(1f, 1f))
        val realOk = own[2] > 0f && own[3] > 0f && other[2] > 0f && other[3] > 0f
        if (realOk) return Pair(Pair(own[2], own[3]), Pair(other[2], other[3]))
        if (own[4] > 0f && other[4] > 0f) return Pair(Pair(own[4], own[4]), Pair(other[4], other[4]))
        return Pair(Pair(1f, 1f), Pair(1f, 1f))
    }

    fun rect(
        viewW: Float,
        viewH: Float,
        keepRatio: Boolean,
        integer: Boolean,
        fillW: Boolean,
        fillH: Boolean,
        align: Int,
        sameSize: Boolean = false,
        own: FloatArray? = null,
        other: FloatArray? = null,
    ): FloatArray {
        var (w, h) = fitted(viewW, viewH, keepRatio, integer)

        if (sameSize && other != null && other[0] > 0f && other[1] > 0f) {
            val (ow, oh) = fitted(other[0], other[1], keepRatio, integer)
            val (mine, theirs) = ppi(own, other)
            val wMm = w / mine.first
            val hMm = h / mine.second
            val owMm = ow / theirs.first
            val ohMm = oh / theirs.second
            if (owMm * ohMm < wMm * hMm) {
                w = owMm * mine.first
                h = ohMm * mine.second
            }
            w = w.coerceAtMost(viewW)
            h = h.coerceAtMost(viewH)
        } else if (keepRatio || integer) {
            if (fillW) w = viewW
            if (fillH) h = viewH
        }
        val x = (viewW - w) / 2f
        val y = when (align) {
            ALIGN_TOP -> 0f
            ALIGN_BOTTOM -> viewH - h
            else -> (viewH - h) / 2f
        }
        return floatArrayOf(x, y, w, h)
    }
}
