package com.seedlessds.app.emu

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SeedlessColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val red: Color,
    val redGlow: Color,
    val green: Color,
    val greenDim: Color,
    val switchOff: Color,
    val shadow: Color,
) {
    companion object {
        val gold = Color(0xFFD4A017)
        val externalEdit = Color(0xFFFFC107)
        val emulationBg = Color(0xFF0B0A0D)
        val tvBg = Color(0xFF121116)
    }
}

val DarkSeedlessColors = SeedlessColors(
    isDark = true,
    bg = Color(0xFF141317),
    surface = Color(0xFF1E1D24),
    surface2 = Color(0xFF2A2833),
    surface3 = Color(0xFF343240),
    line = Color(0x14FFFFFF),
    text = Color(0xFFF6F5F3),
    text2 = Color(0xFFB3B1BD),
    text3 = Color(0xFF7A7885),
    red = Color(0xFFF44336),
    redGlow = Color(0x59F44336),
    green = Color(0xFF6FBF4A),
    greenDim = Color(0x2E6FBF4A),
    switchOff = Color(0x29FFFFFF),
    shadow = Color(0x73000000),
)

val LightSeedlessColors = SeedlessColors(
    isDark = false,
    bg = Color(0xFFF4F3F0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEECE7),
    surface3 = Color(0xFFE3E1DB),
    line = Color(0x14000000),
    text = Color(0xFF1B1A1F),
    text2 = Color(0xFF5B5962),
    text3 = Color(0xFF8D8B94),
    red = Color(0xFFE8392C),
    redGlow = Color(0x47E8392C),
    green = Color(0xFF4F8A2F),
    greenDim = Color(0x214F8A2F),
    switchOff = Color(0x29000000),
    shadow = Color(0x24000000),
)

val LocalSeedlessColors = staticCompositionLocalOf { DarkSeedlessColors }
