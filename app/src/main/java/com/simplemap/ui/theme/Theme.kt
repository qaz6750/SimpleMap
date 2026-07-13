package com.simplemap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SimpleMapColorScheme = lightColorScheme(
    primary = SimpleMapGreen,
    onPrimary = SimpleMapMist,
    secondary = SimpleMapCoral,
    background = SimpleMapMist,
    onBackground = SimpleMapInk,
    surface = SimpleMapMist,
    onSurface = SimpleMapInk,
)

@Composable
fun SimpleMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SimpleMapColorScheme,
        typography = SimpleMapTypography,
        content = content,
    )
}