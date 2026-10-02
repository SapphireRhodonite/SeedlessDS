package com.seedlessds.app.emu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

const val DsBoxArtAspectRatio: Float = 512f / 458f

fun gameGradient(title: String): Brush {
    var hash = 0
    for (ch in title) hash = ch.code + ((hash shl 5) - hash)
    val hue = ((hash % 360) + 360) % 360
    return Brush.linearGradient(
        listOf(
            hsl(hue.toFloat(), 0.42f, 0.34f),
            hsl(((hue + 38) % 360).toFloat(), 0.46f, 0.20f),
        )
    )
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

fun Modifier.scanlines(alpha: Float = 0.045f): Modifier = drawWithContent {
    drawContent()
    val pitch = 3.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(0f, y),
            size = Size(size.width, 1.dp.toPx()),
        )
        y += pitch
    }
}

@Composable
fun GameArt(
    title: String,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    showInitial: Boolean = true,
    coverUrl: String? = null,
    raIconUrl: String? = null,
    loading: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    var coverFailed by remember(coverUrl) { mutableStateOf(false) }
    var raFailed by remember(raIconUrl) { mutableStateOf(false) }
    var artLoaded by remember(coverUrl, raIconUrl) { mutableStateOf(false) }

    val activeUrl = when {
        coverUrl != null && !coverFailed -> coverUrl
        raIconUrl != null && !raFailed -> raIconUrl
        else -> null
    }
    val activeScale = ContentScale.Crop

    Box(
        modifier = modifier
            .background(gameGradient(title))
            .let { if (artLoaded) it else it.scanlines() },
        contentAlignment = Alignment.Center,
    ) {
        if (!artLoaded) {
            when {
                icon != null -> Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
                activeUrl != null || loading -> {
                    if (showInitial) {
                        Text(
                            text = title.take(1).uppercase(),
                            color = Color.White.copy(alpha = 0.18f),
                            fontFamily = SpaceGrotesk,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White.copy(alpha = 0.85f),
                        strokeWidth = 2.dp,
                    )
                }
                showInitial -> Text(
                    text = title.take(1).uppercase(),
                    color = Color.White.copy(alpha = 0.30f),
                    fontFamily = SpaceGrotesk,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (activeUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(true)
                    .listener(
                        onSuccess = { _, _ -> artLoaded = true },
                        onError = { _, _ ->
                            if (activeUrl == coverUrl) coverFailed = true else raFailed = true
                            artLoaded = false
                        },
                    )
                    .build(),
                contentDescription = null,
                contentScale = activeScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        content()
    }
}

@Composable
fun BoxScope.RomIconBadge(
    icon: ImageBitmap?,
    size: Dp = 27.dp,
    align: Alignment = Alignment.BottomStart,
    padding: Dp = 6.dp,
) {
    if (icon == null) return
    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .align(align)
            .padding(padding)
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
    )
}
