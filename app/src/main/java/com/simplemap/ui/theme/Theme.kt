package com.simplemap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val SimpleMapColorScheme = lightColorScheme(
    primary = SimpleMapBlue,
    onPrimary = SimpleMapMist,
    secondary = SimpleMapCoral,
    background = SimpleMapMist,
    onBackground = SimpleMapInk,
    surface = SimpleMapMist,
    onSurface = SimpleMapInk,
)

private val SimpleMapShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SimpleMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SimpleMapColorScheme,
        typography = SimpleMapTypography,
        shapes = SimpleMapShapes,
        content = content,
    )
}