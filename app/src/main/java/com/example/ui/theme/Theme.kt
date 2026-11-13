package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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

private val PlanetShapes = Shapes(
    extraSmall = RoundedCornerShape(PlanetRadius.xs),
    small = RoundedCornerShape(PlanetRadius.sm),
    medium = RoundedCornerShape(PlanetRadius.md),
    large = RoundedCornerShape(PlanetRadius.lg),
    extraLarge = RoundedCornerShape(PlanetRadius.xl)
)

@Composable
fun PlanetCheckerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = PlanetShapes,
        content = content
    )
}
