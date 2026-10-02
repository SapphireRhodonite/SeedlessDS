package com.seedlessds.app.emu

import com.seedlessds.app.R
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun SeedlessDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 330.dp,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = seedless
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.86f)
        Column(
            modifier = modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .shadow(22.dp, RoundedCornerShape(17.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                .clip(RoundedCornerShape(17.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(17.dp))
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

@Composable
fun SeedlessButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
) {
    val colors = seedless
    val content = if (accent.luminance() > 0.45f) colors.text else Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .let { if (fillWidth) it.fillMaxWidth() else it }
            .heightIn(min = 42.dp)
            .clip(shape)
            .alpha(if (enabled) 1f else 0.4f)
            .background(accent)
            .let { if (focused) it.border(2.dp, colors.text, shape) else it }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = content,
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SeedlessConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = androidx.compose.ui.res.stringResource(R.string.common_cancel),
    icon: ImageVector = Icons.Rounded.WarningAmber,
    destructive: Boolean = true,
) {
    val colors = seedless
    SeedlessDialog(onDismiss = onDismiss, width = 330.dp) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) colors.red else colors.green,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.padding(top = 12.dp))
            Text(
                text = title,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedlessButton(cancelLabel, colors.surface2, onDismiss, Modifier.weight(1f), fillWidth = true)
                SeedlessButton(
                    text = confirmLabel,
                    accent = if (destructive) colors.red else colors.green,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    fillWidth = true,
                )
            }
        }
    }
}

@Composable
fun SeedlessChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    descriptions: List<String>? = null,
) {
    val colors = seedless
    SeedlessDialog(onDismiss = onDismiss, width = 360.dp) {
        Text(
            text = title,
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            options.forEachIndexed { index, label ->
                SeedlessRow(
                    modifier = Modifier.padding(vertical = 2.dp),
                    selected = index == selectedIndex,
                    onClick = { onSelect(index); onDismiss() },
                ) {
                    Box(
                        Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
                            .border(2.dp, if (index == selectedIndex) colors.green else colors.text3, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == selectedIndex) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.green))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = label,
                            color = colors.text,
                            fontFamily = Manrope,
                            fontSize = 13.5.sp,
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        descriptions?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                color = colors.text3,
                                fontFamily = Manrope,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 14.dp))
    }
}
