package com.seedlessds.app.emu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val seedless: SeedlessColors
    @Composable get() = LocalSeedlessColors.current

val SeedlessShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(17.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

private fun seedlessColorScheme(c: SeedlessColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.red,
        onPrimary = Color.White,
        primaryContainer = c.red.copy(alpha = 0.16f),
        onPrimaryContainer = c.red,
        secondary = c.green,
        onSecondary = Color.White,
        secondaryContainer = c.greenDim,
        onSecondaryContainer = c.green,
        tertiary = SeedlessColors.gold,
        onTertiary = Color.White,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.text2,
        surfaceContainerLowest = c.bg,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface2,
        surfaceContainerHighest = c.surface3,
        inverseSurface = c.text,
        inverseOnSurface = c.bg,
        outline = c.text3,
        outlineVariant = c.line,
        error = c.red,
        onError = Color.White,
        scrim = c.shadow,
    )
}

@Composable
fun SeedlessTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) DarkSeedlessColors else LightSeedlessColors

    CompositionLocalProvider(LocalSeedlessColors provides colors) {
        MaterialTheme(
            colorScheme = seedlessColorScheme(colors),
            typography = SeedlessTypography,
            shapes = SeedlessShapes,
            content = content,
        )
    }
}
