package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PlanetRose,
    onPrimary = Color.White,
    primaryContainer = PlanetNavy,
    onPrimaryContainer = PlanetRoseLight,
    secondary = PlanetAmber,
    onSecondary = Color.Black,
    secondaryContainer = PlanetSurface,
    onSecondaryContainer = PlanetGold,
    tertiary = PlanetImaxSky,
    onTertiary = Color.White,
    background = PlanetDarkBg,
    onBackground = PlanetTextPrimary,
    surface = PlanetSurface,
    onSurface = PlanetTextPrimary,
    surfaceVariant = PlanetCardBg,
    onSurfaceVariant = PlanetTextSecondary,
    outline = PlanetBorder,
    outlineVariant = PlanetBorderSubtle
)

@Composable
fun PlanetCheckerTheme(
    content: @Composable () -> Unit
) {
    // The cinema staff monitor is an intentional dark-mode experience with glowing status indicators
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
