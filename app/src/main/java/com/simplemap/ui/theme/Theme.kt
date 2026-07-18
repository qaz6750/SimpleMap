package com.simplemap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SimpleMapColorScheme = lightColorScheme(
    primary = SimpleMapBlue,
    onPrimary = SimpleMapMist,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = Color(0xFF113B69),
    secondary = SimpleMapCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9E5),
    onSecondaryContainer = Color(0xFF6E201B),
    background = SimpleMapMist,
    onBackground = SimpleMapInk,
    surface = Color(0xFFFFFFFF),
    onSurface = SimpleMapInk,
    surfaceVariant = Color(0xFFF0F4F7),
    onSurfaceVariant = Color(0xFF5E6B78),
    outline = Color(0xFF7C8996),
    outlineVariant = Color(0xFFDDE4EA),
    errorContainer = Color(0xFFFFE9E6),
    onErrorContainer = Color(0xFF8C2822),
)

private val SimpleMapDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8EC7FF),
    onPrimary = Color(0xFF002F5E),
    primaryContainer = Color(0xFF193B5D),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFFFB4A8),
    onSecondary = Color(0xFF5F160F),
    secondaryContainer = Color(0xFF652821),
    onSecondaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E8F0),
    surface = Color(0xFF171C21),
    onSurface = Color(0xFFE1E8F0),
    surfaceVariant = Color(0xFF252C33),
    onSurfaceVariant = Color(0xFFB8C3CE),
    outline = Color(0xFF84909C),
    outlineVariant = Color(0xFF3A444E),
    errorContainer = Color(0xFF5B2423),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val SimpleMapShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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