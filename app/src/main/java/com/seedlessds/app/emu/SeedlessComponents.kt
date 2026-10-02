package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun RequestInitialFocus(focusRequester: FocusRequester) {
    LaunchedEffect(focusRequester) {
        repeat(12) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(30)
        }
    }
}

@Composable
fun SeedlessSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = seedless
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.green else colors.switchOff,
        animationSpec = tween(180),
        label = "switch_track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 19.dp else 0.dp,
        animationSpec = tween(180),
        label = "switch_knob",
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 25.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .let {
                if (onCheckedChange != null && enabled) {
                    it.clickable { onCheckedChange(!checked) }
                } else {
                    it
                }
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(19.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun SeedlessSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = seedless
    Text(
        text = text.uppercase(),
        color = colors.text3,
        fontFamily = SeedlessMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 2.dp, top = 20.dp, bottom = 9.dp),
    )
}

@Composable
fun SeedlessCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = seedless
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(15.dp)),
        content = content,
    )
}

@Composable
fun SeedlessRowSeparator() {
    val colors = seedless
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
}

@Composable
fun SeedlessRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    external: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    minHeight: androidx.compose.ui.unit.Dp = 42.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.(focused: Boolean) -> Unit,
) {
    val colors = seedless
    val rowCursor = LocalRowCursor.current
    val myIndex = remember { if (enabled) rowCursor?.let { it.next++ } ?: -1 else -1 }
    androidx.compose.runtime.SideEffect {
        if (rowCursor != null) {
            if (rowCursor.total < rowCursor.next) rowCursor.total = rowCursor.next
            if (myIndex >= 0) rowCursor.actions[myIndex] = onClick
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val focusedByPointer by interactionSource.collectIsFocusedAsState()
    val isFocused = if (rowCursor != null) {
        myIndex >= 0 && rowCursor.selected == myIndex
    } else {
        focusedByPointer || selected
    }
    if (external != null) {
        androidx.compose.runtime.LaunchedEffect(isFocused) {
            if (isFocused) ExternalInfoBus.show(external)
        }
    }
    val center = LocalCenterScroll.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .centerScrollTarget(center, isFocused)
            .heightIn(min = minHeight)
            .clip(shape)
            .alpha(if (enabled) 1f else 0.4f)
            .background(
                when {
                    isFocused -> colors.text.copy(alpha = 0.16f)
                    selected -> colors.text.copy(alpha = 0.10f)
                    else -> colors.text.copy(alpha = 0.045f)
                }
            )
            .let { if (isFocused) it.border(2.dp, colors.red, shape) else it }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content(isFocused)
    }
}

@Composable
fun SeedlessToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    external: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = seedless
    SeedlessRow(
        modifier = modifier,
        enabled = enabled,
        external = external,
        focusRequester = focusRequester,
        onClick = { onToggle(!checked) },
    ) {
        Text(
            text = label,
            color = colors.text,
            fontFamily = Manrope,
            fontSize = 13.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SeedlessSwitch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

data class GamepadHint(val button: String?, val label: String)

@Composable
fun GamepadHintsFooter(
    hints: List<GamepadHint>,
    modifier: Modifier = Modifier,
    showTopBorder: Boolean = true,
) {
    val colors = seedless
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTopBorder) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            hints.forEach { hint ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hint.button == null) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = colors.text3,
                            modifier = Modifier.size(15.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(17.dp)
                                .border(1.5.dp, colors.text3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = hint.button,
                                color = colors.text3,
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Text(
                        text = hint.label,
                        color = colors.text3,
                        fontFamily = SeedlessMono,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SeedlessNavHints(
    acceptLabel: String = androidx.compose.ui.res.stringResource(R.string.common_accept),
    backLabel: String = androidx.compose.ui.res.stringResource(R.string.common_back),
    modifier: Modifier = Modifier,
) = run {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    GamepadHintsFooter(
    hints = listOf(
        GamepadHint(null, uiContext.getString(R.string.common_navigate)),
        GamepadHint(uiContext.getString(R.string.button_a), acceptLabel),
        GamepadHint(uiContext.getString(R.string.button_b), backLabel),
    ),
    modifier = modifier,
)
}
