package com.example.waterwatchlaredo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WaterPeriwinkle,
    onPrimary = Color.White,
    primaryContainer = PaleWater,
    onPrimaryContainer = DeepNavy,
    secondary = DeepNavy,
    secondaryContainer = PaleWater,
    onSecondaryContainer = DeepNavy,
    onSurfaceVariant = DeepNavy,
    background = Mist,
    surface = Color.White,
    onBackground = DeepNavy,
    onSurface = DeepNavy,
    outline = SoftOutline,
    outlineVariant = SoftOutline,
    surfaceTint = WaterPeriwinkle,
    surfaceContainerLow = Mist,
    surfaceContainer = Mist,
    surfaceContainerHigh = PaleWater
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftOutline,
    onPrimary = NightWater,
    primaryContainer = WaterPeriwinkle,
    onPrimaryContainer = Color.White,
    secondary = PaleWater,
    secondaryContainer = WaterPeriwinkle,
    onSecondaryContainer = Color.White,
    onSurfaceVariant = SoftOutline,
    background = NightWater,
    surface = DeepNavy,
    onBackground = PaleWater,
    onSurface = PaleWater,
    outline = SoftOutline,
    outlineVariant = WaterPeriwinkle,
    surfaceTint = SoftOutline,
    surfaceContainerLow = NightWater,
    surfaceContainer = DeepNavy,
    surfaceContainerHigh = DeepNavy
)

@Composable
fun WaterWatchLaredoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use the water-themed palette consistently instead of the device wallpaper colors.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
