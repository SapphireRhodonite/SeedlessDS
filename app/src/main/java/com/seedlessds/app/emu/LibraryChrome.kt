@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryFilterChip(
    label: String,
    active: Boolean,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.red else colors.surface)
            .let {
                when {
                    focused -> it.border(2.dp, colors.text, shape)
                    active -> it
                    else -> it.border(1.dp, colors.line, shape)
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color.White else (iconTint ?: colors.text3),
                modifier = Modifier.size(13.dp).padding(end = 0.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label.uppercase(),
            color = if (active) Color.White else colors.text2,
            fontFamily = SeedlessMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun LibraryFilterRow(
    selected: RomLibrary.Filter,
    onSelect: (RomLibrary.Filter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item {
            LibraryFilterChip(uiContext.getString(R.string.common_all), selected == RomLibrary.Filter.ALL) { onSelect(RomLibrary.Filter.ALL) }
        }
        item {
            LibraryFilterChip(
                label = uiContext.getString(R.string.common_favorites),
                active = selected == RomLibrary.Filter.FAVORITES,
                icon = Icons.Filled.Star,
                iconTint = FavoriteStar,
                onClick = { onSelect(RomLibrary.Filter.FAVORITES) },
            )
        }
        item { LibraryFilterChip(uiContext.getString(R.string.common_ds), selected == RomLibrary.Filter.DS) { onSelect(RomLibrary.Filter.DS) } }
        item { LibraryFilterChip(uiContext.getString(R.string.common_dsiware), selected == RomLibrary.Filter.DSIWARE) { onSelect(RomLibrary.Filter.DSIWARE) } }
        item {
            LibraryFilterChip(
                label = uiContext.getString(R.string.library_ra),
                active = selected == RomLibrary.Filter.RETRO_ACHIEVEMENTS,
                icon = Icons.Rounded.EmojiEvents,
                iconTint = SeedlessColors.gold,
                onClick = { onSelect(RomLibrary.Filter.RETRO_ACHIEVEMENTS) },
            )
        }
    }
}

val FavoriteStar = Color(0xFFFFD23F)

@Composable
fun VirtualFolderChip(
    folder: RomLibrary.VirtualFolder,
    active: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.greenDim else colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.red
                    active -> colors.green.copy(alpha = 0.45f)
                    else -> colors.line
                },
                shape = shape,
            )
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Rounded.Folder, null, tint = colors.green, modifier = Modifier.size(19.dp))
        Column(Modifier.padding(start = 9.dp)) {
            Text(
                text = folder.name,
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = uiContext.resources.getQuantityString(R.plurals.common_games, folder.gameCount, folder.gameCount),
                color = colors.text3,
                fontFamily = SeedlessMono,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
fun SortChip(
    label: String,
    active: Boolean,
    descending: Boolean,
    onClick: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.greenDim else Color.Transparent)
            .let { if (focused) it.border(2.dp, colors.red, shape) else it }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = if (active) colors.green else colors.text3,
            fontFamily = SeedlessMono,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        if (active) {
            Text(
                text = if (descending) uiContext.getString(R.string.sort_descending_suffix) else uiContext.getString(R.string.sort_ascending_suffix),
                color = colors.green,
                fontFamily = SeedlessMono,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ViewModeToggle(mode: RomLibrary.ViewMode, onSelect: (RomLibrary.ViewMode) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(11.dp))
            .padding(2.dp),
    ) {
        listOf(
            RomLibrary.ViewMode.GRID to Icons.Rounded.GridView,
            RomLibrary.ViewMode.LIST to Icons.Rounded.ViewList,
        ).forEach { (value, icon) ->
            val active = value == mode
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.red else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (value == RomLibrary.ViewMode.GRID) uiContext.getString(R.string.library_grid_view) else uiContext.getString(R.string.library_list_view),
                    tint = if (active) Color.White else colors.text3,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun AlphabetIndexRail(
    letters: List<Char>,
    activeLetter: Char?,
    onLetter: (Char) -> Unit,
    onTop: () -> Unit,
    modifier: Modifier = Modifier,
    showTopIcon: Boolean = true,
) {
    if (letters.isEmpty()) return
    val colors = seedless
    val totalItems = letters.size + (if (showTopIcon) 1 else 0)

    var hoverChar by remember { mutableStateOf<Char?>(null) }
    var hoverTop by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }

    fun handleDrag(yPx: Float) {
        if (barHeightPx <= 0 || totalItems == 0) return
        val itemHeight = barHeightPx.toFloat() / totalItems
        val clamped = (yPx / itemHeight).toInt().coerceIn(0, totalItems - 1)
        if (showTopIcon && clamped == 0) {
            if (!hoverTop) { hoverTop = true; hoverChar = null; onTop() }
            return
        }
        hoverTop = false
        val ch = letters.getOrNull(clamped - (if (showTopIcon) 1 else 0)) ?: return
        if (ch != hoverChar) { hoverChar = ch; onLetter(ch) }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(24.dp)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
                .onSizeChanged { barHeightPx = it.height }
                .pointerInput(letters, showTopIcon) {
                    detectVerticalDragGestures(
                        onDragStart = { touching = true; handleDrag(it.y) },
                        onDragEnd = { touching = false; hoverChar = null; hoverTop = false },
                        onDragCancel = { touching = false; hoverChar = null; hoverTop = false },
                    ) { change, _ ->
                        handleDrag(change.position.y)
                        change.consume()
                    }
                },
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showTopIcon) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusProperties { canFocus = false }
                            .clickable(onClick = onTop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Folder, null,
                            tint = if (hoverTop) colors.green else colors.text3,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                letters.forEach { ch ->
                    val hovered = hoverChar == ch
                    val highlighted = hovered || (hoverChar == null && !touching && activeLetter == ch)
                    val scale by animateFloatAsState(
                        targetValue = if (hovered) 1.7f else if (highlighted) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "letter_scale",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusProperties { canFocus = false }
                            .clickable { onLetter(ch) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ch.toString(),
                            modifier = Modifier.scale(scale),
                            fontFamily = SeedlessMono,
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp,
                            color = if (highlighted) colors.green else colors.text3,
                            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (touching && (hoverChar != null || hoverTop)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.red),
                contentAlignment = Alignment.Center,
            ) {
                if (hoverTop) {
                    Icon(Icons.Rounded.Folder, null, tint = Color.White, modifier = Modifier.size(48.dp))
                } else {
                    Text(
                        text = hoverChar.toString(),
                        color = Color.White,
                        fontFamily = SpaceGrotesk,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

fun indexLetter(title: String): Char {
    val c = title.trimStart().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}
