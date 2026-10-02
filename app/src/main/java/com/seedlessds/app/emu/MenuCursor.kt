package com.seedlessds.app.emu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

class MenuCursor(initial: Int = 0) {
    var index by mutableIntStateOf(initial)
        internal set

    fun select(i: Int) {
        index = i
    }
}

@Composable
fun rememberMenuCursor(
    count: Int,
    columns: Int = 1,
    initial: Int = 0,
    onAccept: (Int) -> Unit,
    onCancel: () -> Unit,
    onTab: ((Int) -> Unit)? = null,
): MenuCursor {
    val cursor = remember { MenuCursor(initial) }
    LaunchedEffect(count) {
        if (count > 0 && cursor.index > count - 1) cursor.index = count - 1
    }

    val accept by androidx.compose.runtime.rememberUpdatedState(onAccept)
    val cancel by androidx.compose.runtime.rememberUpdatedState(onCancel)
    val tab by androidx.compose.runtime.rememberUpdatedState(onTab)

    DisposableEffect(count, columns) {
        MenuKeyRouter.handler = handler@{ key ->
            if (MenuKeyRouter.isCancel(key)) { cancel(); return@handler true }
            tab?.let { switch ->
                if (MenuKeyRouter.isPrevTab(key)) { switch(-1); return@handler true }
                if (MenuKeyRouter.isNextTab(key)) { switch(1); return@handler true }
            }
            val last = count - 1
            if (last < 0) return@handler false
            when {
                MenuKeyRouter.isUp(key) -> cursor.index = (cursor.index - columns).coerceAtLeast(0)
                MenuKeyRouter.isDown(key) -> cursor.index = (cursor.index + columns).coerceAtMost(last)
                MenuKeyRouter.isLeft(key) -> cursor.index = (cursor.index - 1).coerceAtLeast(0)
                MenuKeyRouter.isRight(key) -> cursor.index = (cursor.index + 1).coerceAtMost(last)
                MenuKeyRouter.isAccept(key) -> accept(cursor.index)
                MenuKeyRouter.isCancel(key) -> onCancel()
                else -> return@handler false
            }
            true
        }
        onDispose { MenuKeyRouter.handler = null }
    }
    return cursor
}

class RowCursorState {
    var next = 0
    var selected by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    val actions = HashMap<Int, () -> Unit>()
}

val LocalRowCursor = androidx.compose.runtime.compositionLocalOf<RowCursorState?> { null }

@Composable
fun RowCursorHost(
    onCancel: () -> Unit,
    resetKey: Any? = Unit,
    onTab: ((Int) -> Unit)? = null,
    scroll: ScrollState? = null,
    content: @Composable () -> Unit,
) {
    val state = remember(resetKey) { RowCursorState() }
    val cursor = rememberMenuCursor(
        count = state.total,
        onAccept = { state.actions[state.selected]?.invoke() },
        onCancel = onCancel,
        onTab = onTab,
    )
    LaunchedEffect(cursor.index) { state.selected = cursor.index }
    val center = remember { CenterScroll() }
    if (scroll != null) center.Drive(scroll, state.selected)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalRowCursor provides state,
        LocalCenterScroll provides if (scroll != null) center else null,
    ) { content() }
}

@Composable
fun MenuCursor.KeepVisible(state: LazyListState) {
    LaunchedEffect(index) { runCatching { state.animateScrollToItem(index) } }
}

@Composable
fun MenuCursor.KeepVisible(state: LazyGridState, headerItems: Int = 0) {
    LaunchedEffect(index) { runCatching { state.animateScrollToItem(index + headerItems) } }
}

class CenterScroll {
    internal var viewportTop = 0f
    internal var viewportHeight = 0
    internal var rowTop = 0f
    internal var rowHeight = 0
}

val LocalCenterScroll = androidx.compose.runtime.compositionLocalOf<CenterScroll?> { null }

fun Modifier.centerScrollViewport(cs: CenterScroll?): Modifier =
    if (cs == null) this else this.onGloballyPositioned {
        cs.viewportTop = it.positionInRoot().y
        cs.viewportHeight = it.size.height
    }

fun Modifier.centerScrollTarget(cs: CenterScroll?, active: Boolean): Modifier =
    if (cs == null) this else this.onGloballyPositioned {
        if (active) {
            cs.rowTop = it.positionInRoot().y
            cs.rowHeight = it.size.height
        }
    }

@Composable
fun CenterScroll.Drive(scroll: ScrollState, key: Any?) {
    LaunchedEffect(key) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        if (rowHeight <= 0 || viewportHeight <= 0) return@LaunchedEffect
        val offsetInViewport = rowTop - viewportTop
        val target = scroll.value + offsetInViewport - (viewportHeight - rowHeight) / 2f
        runCatching { scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue)) }
    }
}
