package com.seedlessds.app.emu

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.window.layout.FoldingFeature
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class FoldSeam(val horizontal: Boolean, val start: Int, val end: Int) {
    fun lcdSlots(width: Int, height: Int, cfg: DsSettings): Pair<FloatArray, FloatArray> {
        val firstW = if (horizontal) width else start
        val secondW = if (horizontal) width else width - end
        val firstH = if (horizontal) start else height
        val secondH = if (horizontal) height - end else height
        val preset = cfg.dualScreenPreset != DualScreenPresets.OFF
        val first = DualScreenPresets.panel(firstW, firstH, 1f, 1f, 1)
        val second = DualScreenPresets.panel(secondW, secondH, 1f, 1f, 1)
        val integer = cfg.integerScale && minOf(firstW, secondW) >= 256 && minOf(firstH, secondH) >= 192
        fun slot(internal: Boolean): FloatArray {
            val own = if (internal) first else second
            return DualScreenPresets.rect(own[0], own[1], !preset || cfg.dsKeepRatio, integer,
                preset && (if (internal) cfg.dsIntFillW else cfg.dsExtFillW),
                preset && (if (internal) cfg.dsIntFillH else cfg.dsExtFillH),
                if (!preset) DualScreenPresets.ALIGN_CENTER else if (internal) cfg.dsIntAlign else cfg.dsExtAlign,
                preset && cfg.dsSameSize, own, if (internal) second else first)
        }
        val secondRect = slot(false)
        secondRect[if (horizontal) 1 else 0] += end.toFloat()
        return slot(true) to secondRect
    }

    fun avoid(anchor: Alignment, offset: IntOffset, scale: Float): Alignment = object : Alignment {
        override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
            val dx = if (layoutDirection == LayoutDirection.Ltr) offset.x else -offset.x
            val origin = anchor.align(size, space, layoutDirection) + IntOffset(dx, offset.y)
            val extent = if (horizontal) size.height else size.width
            val margin = ceil(extent * (scale - 1f) / 2f).toInt()
            val pos = (if (horizontal) origin.y else origin.x) - margin
            val length = extent + 2 * margin
            if (pos >= end || pos + length <= start) return origin
            val before = start - length
            val limit = (if (horizontal) space.height else space.width) - length
            val safe = if (end > limit || before >= 0 && pos - before <= end - pos) before else end
            val placed = safe.coerceAtLeast(0) + margin
            return if (horizontal) origin.copy(y = placed) else origin.copy(x = placed)
        }
    }

    companion object {
        fun continuousSlots(width: Int, height: Int, density: Float, gapPx: Float, cfg: DsSettings): Pair<FloatArray, FloatArray> {
            val stacked = height >= width || height >= 480f * density
            val extent = if (stacked) height else width
            val gap = gapPx.roundToInt().coerceIn(0, (extent - 2).coerceAtLeast(0))
            val half = (extent - gap) / 2
            if (cfg.dsKeepRatio || cfg.integerScale) {
                return FoldSeam(stacked, half, half + gap).lcdSlots(width, height, cfg)
            }
            val lcdW = minOf(if (stacked) width.toFloat() else half.toFloat(),
                (if (stacked) half else height) * (4f / 3f))
            val lcdH = lcdW * 0.75f
            val left = if (stacked) (width - lcdW) / 2f else (width - 2 * lcdW - gap) / 2f
            val top = if (stacked) 0f else (height - lcdH) / 2f
            val secondX = if (stacked) left else left + lcdW + gap
            val secondY = if (stacked) top + lcdH + gap else top
            return floatArrayOf(left, top, lcdW, lcdH) to floatArrayOf(secondX, secondY, lcdW, lcdH)
        }

        fun divides(feature: FoldingFeature): Boolean =
            feature.isSeparating || feature.occlusionType == FoldingFeature.OcclusionType.FULL

        fun inWindow(feature: FoldingFeature?, origin: IntOffset, size: IntSize): FoldSeam? {
            if (feature == null || !divides(feature)) return null
            val b = feature.bounds
            val horizontal = feature.orientation == FoldingFeature.Orientation.HORIZONTAL
            val start = if (horizontal) b.top - origin.y else b.left - origin.x
            val end = if (horizontal) b.bottom - origin.y else b.right - origin.x
            val fullSpan = if (horizontal) b.left <= origin.x && b.right >= origin.x + size.width
                else b.top <= origin.y && b.bottom >= origin.y + size.height
            val limit = if (horizontal) size.height else size.width
            return if (fullSpan && start > 0 && end < limit) FoldSeam(horizontal, start, end) else null
        }
    }
}
