package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeedlessLogo(
    modifier: Modifier = Modifier,
    slabWidth: Dp = 17.dp,
    slabHeight: Dp = 11.dp,
) {
    val colors = seedless
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(slabWidth, slabHeight).clip(RoundedCornerShape(3.dp)).background(colors.red))
        Box(Modifier.size(slabWidth, slabHeight).clip(RoundedCornerShape(3.dp)).background(colors.green))
    }
}

@Composable
fun SeedlessWordmark(
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.text)) { append(uiContext.getString(R.string.boot_seedless)) }
            withStyle(SpanStyle(color = colors.green)) { append(uiContext.getString(R.string.common_ds)) }
        },
        fontFamily = SpaceGrotesk,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun SeedlessBrandHeader(
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = seedless
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SeedlessLogo()
        Spacer(Modifier.width(10.dp))
        Column {
            SeedlessWordmark()
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SeedlessScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hints: List<GamepadHint> = defaultScreenHints(),
    actions: @Composable (RowScopeShim.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Scaffold(
        modifier = modifier,
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(Modifier.background(colors.bg).statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = uiContext.getString(R.string.common_back),
                            tint = colors.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle.uppercase(),
                                color = colors.text3,
                                fontFamily = SeedlessMono,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    actions?.invoke(RowScopeShim)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
        },
        bottomBar = {
            GamepadHintsFooter(
                modifier = Modifier.background(colors.bg).navigationBarsPadding(),
                hints = hints,
            )
        },
        content = content,
    )
}

object RowScopeShim

@Composable
private fun defaultScreenHints(): List<GamepadHint> = run {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    listOf(
    GamepadHint(null, uiContext.getString(R.string.common_navigate)),
    GamepadHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.common_accept)),
    GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.common_back)),
)
}

@Composable
fun SeedlessHeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = seedless
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.text2,
            modifier = Modifier.size(21.dp),
        )
    }
}
