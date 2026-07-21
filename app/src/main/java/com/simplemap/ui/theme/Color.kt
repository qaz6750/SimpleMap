package com.simplemap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Bright map-forward accents keep chrome legible without competing with navigation content.
val SimpleMapBlue = Color(0xFF0C6DFF)
val SimpleMapBlueLight = Color(0xFFD9E8FF)
internal val SimpleMapBlueNight = Color(0xFF93C2FF)

val SimpleMapGreen = Color(0xFF1F9863)
internal val SimpleMapGreenContainer = Color(0xFFD3F4E0)
internal val SimpleMapGreenNight = Color(0xFF86DCAD)
internal val SimpleMapGreenContainerNight = Color(0xFF0E4F33)

val SimpleMapTeal = SimpleMapGreen
val SimpleMapCoral = Color(0xFFD84C53)

internal val SimpleMapTrafficFree = Color(0xFF22A65E)
internal val SimpleMapTrafficFreeNight = Color(0xFF7FDE9D)
internal val SimpleMapTrafficSlow = Color(0xFFFFBE2E)
internal val SimpleMapTrafficSlowNight = Color(0xFFFFD66C)
internal val SimpleMapTrafficBusy = Color(0xFFFF875A)
internal val SimpleMapTrafficBusyNight = Color(0xFFFFB18A)
internal val SimpleMapTrafficJam = Color(0xFFD84C53)
internal val SimpleMapTrafficJamNight = Color(0xFFFF9EA1)

val SimpleMapInk = Color(0xFF152033)
internal val SimpleMapInkInverse = Color(0xFFF1F5FA)
val SimpleMapMist = Color(0xFFF3F7FB)
internal val LightSurfaceLowest = Color(0xFFFFFFFF)
internal val LightSurfaceLow = Color(0xFFF8FAFD)
internal val LightSurface = Color(0xFFF0F4F9)
internal val LightSurfaceHigh = Color(0xFFE8EEF5)
internal val LightSurfaceHighest = Color(0xFFDEE7F0)
internal val LightOutline = Color(0xFF68788B)
internal val LightOutlineVariant = Color(0xFFD4DDE7)

internal val DarkBackground = Color(0xFF08131E)
internal val DarkSurfaceDim = Color(0xFF0D1823)
internal val DarkSurface = Color(0xFF101B27)
internal val DarkSurfaceVariant = Color(0xFF152230)
internal val DarkSurfaceHigh = Color(0xFF1A2938)
internal val DarkSurfaceHighest = Color(0xFF223447)
internal val DarkOutline = Color(0xFF8FA0B3)
internal val DarkOutlineVariant = Color(0xFF334252)

internal val ColorScheme.isLightScheme: Boolean
    get() = background.luminance() > 0.5f

internal val ColorScheme.navigationAccent: Color
    get() = if (isLightScheme) SimpleMapGreen else SimpleMapGreenNight

internal val ColorScheme.trafficClear: Color
    get() = if (isLightScheme) SimpleMapTrafficFree else SimpleMapTrafficFreeNight

internal val ColorScheme.trafficSlow: Color
    get() = if (isLightScheme) SimpleMapTrafficSlow else SimpleMapTrafficSlowNight

internal val ColorScheme.trafficBusy: Color
    get() = if (isLightScheme) SimpleMapTrafficBusy else SimpleMapTrafficBusyNight

internal val ColorScheme.trafficJam: Color
    get() = if (isLightScheme) SimpleMapTrafficJam else SimpleMapTrafficJamNight

internal val ColorScheme.panelBorder: Color
    get() = outlineVariant

internal val ColorScheme.sectionSurface: Color
    get() = if (isLightScheme) surfaceContainerLow else surfaceContainer

internal val ColorScheme.sectionSurfaceEmphasis: Color
    get() = if (isLightScheme) surfaceContainerHigh else surfaceContainerHighest
