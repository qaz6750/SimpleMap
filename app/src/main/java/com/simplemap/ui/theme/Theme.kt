package com.simplemap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SimpleMapColorScheme = lightColorScheme(
    primary = SimpleMapBlue,
    onPrimary = Color.White,
    primaryContainer = SimpleMapBlueLight,
    onPrimaryContainer = Color(0xFF003A81),
    secondary = SimpleMapGreen,
    onSecondary = Color.White,
    secondaryContainer = SimpleMapGreenContainer,
    onSecondaryContainer = Color(0xFF003920),
    tertiary = SimpleMapTrafficSlow,
    onTertiary = Color(0xFF4B2C00),
    tertiaryContainer = Color(0xFFFFE2AA),
    onTertiaryContainer = Color(0xFF6D4500),
    background = SimpleMapMist,
    onBackground = SimpleMapInk,
    surface = LightSurfaceLow,
    onSurface = SimpleMapInk,
    surfaceVariant = LightSurface,
    onSurfaceVariant = Color(0xFF516173),
    surfaceBright = LightSurfaceLowest,
    surfaceDim = LightSurfaceHighest,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = Color(0xFF233140),
    inverseOnSurface = SimpleMapInkInverse,
    inversePrimary = SimpleMapBlueNight,
    error = SimpleMapTrafficJam,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF690008),
)

private val SimpleMapDarkColorScheme = darkColorScheme(
    primary = SimpleMapBlueNight,
    onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF0B4EAE),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = SimpleMapGreenNight,
    onSecondary = Color(0xFF003920),
    secondaryContainer = SimpleMapGreenContainerNight,
    onSecondaryContainer = Color(0xFFB4F1CB),
    tertiary = SimpleMapTrafficSlowNight,
    onTertiary = Color(0xFF442C00),
    tertiaryContainer = Color(0xFF6A4A00),
    onTertiaryContainer = Color(0xFFFFE2AA),
    background = DarkBackground,
    onBackground = SimpleMapInkInverse,
    surface = DarkSurface,
    onSurface = SimpleMapInkInverse,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB6C4D5),
    surfaceBright = DarkSurfaceHigh,
    surfaceDim = DarkSurfaceDim,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceDim,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = Color(0xFFE3ECF6),
    inverseOnSurface = Color(0xFF1B2936),
    inversePrimary = SimpleMapBlue,
    error = SimpleMapTrafficJamNight,
    onError = Color(0xFF690008),
    errorContainer = Color(0xFF8E101A),
    onErrorContainer = Color(0xFFFFDADB),
)

private val SimpleMapShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun SimpleMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SimpleMapDarkColorScheme else SimpleMapColorScheme,
        typography = SimpleMapTypography,
        shapes = SimpleMapShapes,
        content = content,
    )
}
