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
    onPrimaryContainer = Color(0xFF073A75),
    secondary = SimpleMapTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F1EB),
    onSecondaryContainer = Color(0xFF064B45),
    tertiary = SimpleMapCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF6A1B14),
    background = SimpleMapMist,
    onBackground = SimpleMapInk,
    surface = LightSurface,
    onSurface = SimpleMapInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF536172),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = Color(0xFF29313B),
    inverseOnSurface = Color(0xFFF2F4F8),
    inversePrimary = Color(0xFFA9CAFF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

private val SimpleMapDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9CAFF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF164A86),
    onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFF8CD4C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF18534D),
    onSecondaryContainer = Color(0xFFB6F1E8),
    tertiary = Color(0xFFFFB4A8),
    onTertiary = Color(0xFF60140D),
    tertiaryContainer = Color(0xFF84291F),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = DarkBackground,
    onBackground = Color(0xFFE4E9F1),
    surface = DarkSurface,
    onSurface = Color(0xFFE4E9F1),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBBC5D1),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = Color(0xFFE4E9F1),
    inverseOnSurface = Color(0xFF29313B),
    inversePrimary = SimpleMapBlue,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val SimpleMapShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
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
