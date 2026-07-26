package com.simplemap.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal enum class HomeDestination(val label: String) {
    Map("地图"),
    Routes("路线"),
    Trips("行程"),
    Profile("我的"),
}

private val BottomDestinations = listOf(
    HomeDestination.Map,
    HomeDestination.Trips,
    HomeDestination.Profile,
)

internal val FloatingNavigationClearance = 94.dp

@Composable
internal fun FloatingNavigation(
    selected: HomeDestination,
    isLandscape: Boolean,
    backdrop: LayerBackdrop,
    onSelected: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscapeWidth = (LocalConfiguration.current.screenWidthDp.dp - 176.dp).coerceIn(154.dp, 240.dp)
    val shape = RoundedCornerShape(if (isLandscape) 18.dp else 30.dp)
    val glassSurface = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
    }
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .then(
                if (isLandscape) {
                    Modifier.width(landscapeWidth)
                } else {
                    Modifier.fillMaxWidth().widthIn(max = 440.dp)
                },
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(6.dp.toPx())
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                    )
                },
                onDrawSurface = { drawRect(glassSurface) },
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                shape,
            )
            .semantics { contentDescription = "沉浸式底部导航" },
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomDestinations.forEach { destination ->
                NavigationItem(
                    label = destination.label,
                    selected = selected == destination,
                    onClick = { onSelected(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "导航项背景",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "导航项前景",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(180),
        label = "导航项图标缩放",
    )
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(25.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeDestinationIcon(
                label = label,
                color = contentColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun HomeDestinationIcon(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
        when (label) {
            "地图" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.18f)
                    lineTo(size.width * 0.36f, size.height * 0.08f)
                    lineTo(size.width * 0.64f, size.height * 0.2f)
                    lineTo(size.width * 0.92f, size.height * 0.1f)
                    lineTo(size.width * 0.92f, size.height * 0.82f)
                    lineTo(size.width * 0.64f, size.height * 0.92f)
                    lineTo(size.width * 0.36f, size.height * 0.8f)
                    lineTo(size.width * 0.08f, size.height * 0.9f)
                    close()
                    moveTo(size.width * 0.36f, size.height * 0.08f)
                    lineTo(size.width * 0.36f, size.height * 0.8f)
                    moveTo(size.width * 0.64f, size.height * 0.2f)
                    lineTo(size.width * 0.64f, size.height * 0.92f)
                }
                drawPath(path, color, style = stroke)
            }
            "路线" -> {
                drawCircle(
                    color,
                    size.minDimension * 0.1f,
                    Offset(size.width * 0.23f, size.height * 0.76f),
                    style = stroke,
                )
                drawCircle(
                    color,
                    size.minDimension * 0.1f,
                    Offset(size.width * 0.77f, size.height * 0.24f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(size.width * 0.31f, size.height * 0.7f)
                    cubicTo(
                        size.width * 0.7f,
                        size.height * 0.66f,
                        size.width * 0.3f,
                        size.height * 0.32f,
                        size.width * 0.69f,
                        size.height * 0.29f,
                    )
                }
                drawPath(path, color, style = stroke)
            }
            "行程" -> {
                drawCircle(color, size.minDimension * 0.4f, center, style = stroke)
                drawLine(color, center, Offset(size.width * 0.5f, size.height * 0.25f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * 0.7f, size.height * 0.58f), stroke.width, StrokeCap.Round)
            }
            else -> {
                drawCircle(
                    color,
                    size.minDimension * 0.18f,
                    Offset(size.width * 0.5f, size.height * 0.32f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.88f)
                    cubicTo(
                        size.width * 0.2f,
                        size.height * 0.58f,
                        size.width * 0.8f,
                        size.height * 0.58f,
                        size.width * 0.82f,
                        size.height * 0.88f,
                    )
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}
