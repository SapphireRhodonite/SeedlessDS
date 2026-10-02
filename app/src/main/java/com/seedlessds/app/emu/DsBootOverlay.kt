package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

enum class DsBootScreenHalf { BOTH, TOP, BOTTOM }

@Composable
fun DsBootOverlay(
    half: DsBootScreenHalf,
    romReady: Boolean,
    romTitle: String?,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    onFinished: () -> Unit,
) {
    val intro = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var introDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(durationMillis = 820, easing = FastOutSlowInEasing))
        introDone = true
    }
    LaunchedEffect(romReady, introDone) {
        if (romReady && introDone) {
            delay(90)
            exit.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    val introV = intro.value
    val contentAlpha = 1f - smooth(exit.value)

    val showTop = half == DsBootScreenHalf.BOTH || half == DsBootScreenHalf.TOP
    val showBottom = half == DsBootScreenHalf.BOTH || half == DsBootScreenHalf.BOTTOM
    val bothScreens = half == DsBootScreenHalf.BOTH
    val openProgress = smooth((introV - 0.04f) / 0.42f)
    val backlight = ((introV - 0.42f) / 0.34f).coerceIn(0f, 1f).let { smooth(it) }
    val logoAlpha = ((introV - 0.58f) / 0.32f).coerceIn(0f, 1f)
    val ledLit = ((introV - 0.16f) / 0.2f).coerceIn(0f, 1f)

    val screenHeightFraction = if (bothScreens) 0.36f else 0.48f

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .background(SeedlessColors.emulationBg),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(if (bothScreens) 0.78f else 0.64f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF191A20), Color(0xFF0D0E12)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val dsScreenHeight = maxHeight * screenHeightFraction
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight(),
            ) {
                if (showTop) {
                    DsScreen(
                        backlight = backlight,
                        modifier = Modifier
                            .height(dsScreenHeight)
                            .aspectRatio(4f / 3f)
                            .then(
                                if (bothScreens) {
                                    Modifier.graphicsLayer {
                                        translationY = (1f - openProgress) * 22.dp.toPx()
                                        alpha = 0.2f + 0.8f * openProgress
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        DsWordmark(alpha = logoAlpha)
                    }
                }

                if (bothScreens) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .width(52.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(seedless.red.copy(alpha = 0.20f + 0.45f * openProgress)),
                    )
                }

                if (showBottom) {
                    DsScreen(
                        backlight = backlight,
                        modifier = Modifier
                            .height(dsScreenHeight)
                            .aspectRatio(4f / 3f),
                    ) {
                        DsBottomLabel(
                            title = romTitle,
                            waiting = introDone && !romReady,
                            alpha = logoAlpha,
                        )
                    }
                }
            }

            PowerLed(
                lit = ledLit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 2.dp, bottom = 2.dp),
            )
        }

        if (!statusText.isNullOrBlank()) {
            Text(
                text = statusText.uppercase(),
                color = Color(0xFF9AA2B0),
                fontFamily = SeedlessMono,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 22.dp)
                    .alpha(logoAlpha),
            )
        }
    }
}

@Composable
private fun DsScreen(
    backlight: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glow = seedless.red
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .drawBehind {
                drawRect(Color(0xFF090A0D))
                if (backlight > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF2B3852).copy(alpha = 0.55f * backlight),
                                Color(0xFF11151F).copy(alpha = 0.55f * backlight),
                            ),
                        ),
                    )
                    val period = 3.dp.toPx()
                    val stop = (1.dp.toPx() / period).coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.12f * backlight),
                            stop to Color.Black.copy(alpha = 0.12f * backlight),
                            stop to Color.Transparent,
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = period,
                            tileMode = TileMode.Repeated,
                        ),
                    )
                }
            }
            .border(1.dp, glow.copy(alpha = 0.30f * backlight), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DsWordmark(alpha: Float) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(alpha),
    ) {
        Text(
            text = uiContext.getString(R.string.boot_seedless),
            color = Color(0xFFC8D0DC),
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 20.sp,
        )
        Text(
            text = uiContext.getString(R.string.common_ds),
            color = seedless.green,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun DsBottomLabel(title: String?, waiting: Boolean, alpha: Float) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(alpha)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = title?.uppercase() ?: uiContext.getString(R.string.boot_nintendo_ds),
            color = Color(0xFFB3BBC7),
            fontFamily = SeedlessMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        if (waiting) {
            Spacer(Modifier.height(7.dp))
            LoadingDots(color = Color(0xFF7C8492))
        }
    }
}

@Composable
private fun LoadingDots(color: Color) {
    val infinite = rememberInfiniteTransition(label = "dsBootDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "dsBootDotsPhase",
    )
    Row {
        repeat(3) { i ->
            val active = phase.toInt() % 3 == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 0.95f else 0.30f)),
            )
        }
    }
}

@Composable
private fun PowerLed(lit: Float, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dsBootLed")
    val breathe by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "dsBootLedBreathe",
    )
    val green = seedless.green
    val intensity = lit * breathe
    Box(modifier = modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(green.copy(alpha = 0.30f * intensity)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(green.copy(alpha = 0.45f + 0.55f * intensity)),
        )
    }
}

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
