package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun BootInfoOverlay(
    title: String,
    icon: ImageBitmap?,
    coverUrl: String?,
    platformLabel: String,
    statusText: String?,
    romReady: Boolean,
    onFinished: () -> Unit,
) {
    val exit = remember { Animatable(0f) }
    var minTimeElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(650)
        minTimeElapsed = true
    }
    LaunchedEffect(romReady, minTimeElapsed) {
        if (romReady && minTimeElapsed) {
            exit.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    Box(Modifier.fillMaxSize().alpha(1f - exit.value)) {
        BootInfoCard(
            title = title,
            icon = icon,
            coverUrl = coverUrl,
            platformLabel = platformLabel,
            statusText = statusText,
        )
    }
}

@Composable
fun BootInfoCard(
    title: String,
    icon: ImageBitmap?,
    coverUrl: String?,
    platformLabel: String,
    statusText: String?,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(SeedlessColors.emulationBg)) {
        Box(Modifier.fillMaxSize().background(gameGradient(title)).scanlines(0.03f))
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.40f,
                modifier = Modifier.fillMaxSize().blur(22.dp),
            )
        } else if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.38f,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.42f)),
                ),
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(DsBoxArtAspectRatio)
                    .shadow(14.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            ) {
                GameArt(title, icon, Modifier.fillMaxSize(), coverUrl = coverUrl)
            }
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = platformLabel.uppercase(),
                    color = Color.White,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(
                        text = (statusText?.takeIf { it.isNotBlank() } ?: uiContext.getString(R.string.boot_starting_game)).uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = SeedlessMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    BootDots(color = seedless.green)
                }
            }
        }
    }
}

@Composable
private fun BootDots(color: Color) {
    val infinite = rememberInfiniteTransition(label = "bootDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "bootDotsPhase",
    )
    Row(Modifier.padding(start = 9.dp)) {
        repeat(3) { i ->
            val active = phase.toInt() % 3 == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 0.95f else 0.28f)),
            )
        }
    }
}
