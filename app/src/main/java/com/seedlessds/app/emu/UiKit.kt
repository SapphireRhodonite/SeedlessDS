package com.seedlessds.app.emu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(state: ScrollState, color: Color, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max > 0) {
            val viewH = size.height
            val thumbH = (viewH * viewH / (viewH + max)).coerceAtLeast(28.dp.toPx())
            val thumbY = (state.value.toFloat() / max) * (viewH - thumbH)
            val w = width.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - w, thumbY),
                size = Size(w, thumbH),
                cornerRadius = CornerRadius(w / 2, w / 2)
            )
        }
    }

fun Modifier.verticalScrollbar(state: LazyGridState, color: Color, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total > 0 && visible in 1 until total) {
            val viewH = size.height
            val thumbH = (viewH * visible / total).coerceAtLeast(28.dp.toPx())
            val firstIdx = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val progress = firstIdx.toFloat() / (total - visible).coerceAtLeast(1)
            val thumbY = progress * (viewH - thumbH)
            val w = width.toPx()
            drawRoundRect(color, Offset(size.width - w, thumbY), Size(w, thumbH), CornerRadius(w / 2, w / 2))
        }
    }

fun Modifier.verticalScrollbar(state: LazyListState, color: Color, width: Dp = 4.dp): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total > 0 && visible in 1 until total) {
            val viewH = size.height
            val thumbH = (viewH * visible / total).coerceAtLeast(28.dp.toPx())
            val firstIdx = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val progress = firstIdx.toFloat() / (total - visible).coerceAtLeast(1)
            val thumbY = progress * (viewH - thumbH)
            val w = width.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - w, thumbY),
                size = Size(w, thumbH),
                cornerRadius = CornerRadius(w / 2, w / 2)
            )
        }
    }
