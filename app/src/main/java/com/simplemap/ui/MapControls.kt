package com.simplemap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.MapScale

private val CompassIcon = ImageVector.Builder(
    name = "Compass",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF1466D8))) {
        moveTo(18.8f, 5.2f)
        lineTo(14.8f, 14.8f)
        lineTo(5.2f, 18.8f)
        lineTo(9.2f, 9.2f)
        close()
    }
}.build()

@Composable
internal fun MapLayerControls(
    trafficEnabled: Boolean,
    satelliteEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTrafficClick: () -> Unit,
    onSatelliteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 78.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapToolButton("路况", trafficEnabled, onTrafficClick)
                MapToolButton("卫星", satelliteEnabled, onSatelliteClick)
            }
        }
        Surface(
            shape = PanelShapeMedium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shadowElevation = 7.dp,
        ) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.size(44.dp),
            ) {
                MapToolsIcon(
                    expanded = expanded,
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = if (expanded) "收起图层" else "展开图层" },
                )
            }
        }
    }
}

@Composable
internal fun MapLocationControl(
    locationEnabled: Boolean,
    onLocationClick: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = if (isLandscape) 12.dp else 116.dp),
        shape = PanelShapeMedium,
        color = if (locationEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        },
        shadowElevation = 7.dp,
    ) {
        IconButton(onClick = onLocationClick, modifier = Modifier.size(44.dp)) {
            CurrentLocationIcon(
                active = locationEnabled,
                modifier = Modifier
                    .size(22.dp)
                    .semantics {
                        contentDescription = if (locationEnabled) {
                            "当前位置，定位已开启"
                        } else {
                            "定位到当前位置"
                        }
                    },
            )
        }
    }
}

@Composable
internal fun MapViewControls(
    perspectiveMode: AmapPerspectiveMode,
    onPerspectiveModeChange: (AmapPerspectiveMode) -> Unit,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 132.dp, end = 18.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
        ) {
            Row {
                MapPerspectiveButton(
                    label = "2D",
                    selected = perspectiveMode == AmapPerspectiveMode.TwoDimensional,
                    onClick = { onPerspectiveModeChange(AmapPerspectiveMode.TwoDimensional) },
                )
                MapPerspectiveButton(
                    label = "3D",
                    selected = perspectiveMode == AmapPerspectiveMode.ThreeDimensional,
                    onClick = { onPerspectiveModeChange(AmapPerspectiveMode.ThreeDimensional) },
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
        ) {
            IconButton(
                onClick = onResetNorth,
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "地图正北" },
            ) {
                Icon(
                    imageVector = CompassIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MapPerspectiveButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "地图视角 $label" },
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = PanelShapeSmall,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, bottom = if (isLandscape) 12.dp else 116.dp)
            .widthIn(min = 48.dp, max = 48.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = PanelShapeMedium,
        shadowElevation = 7.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MapZoomButton(zoomIn = true, description = "放大地图", onClick = onZoomIn)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            MapZoomButton(zoomIn = false, description = "缩小地图", onClick = onZoomOut)
        }
    }
}

@Composable
internal fun MapScaleIndicator(
    scale: MapScale,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = Color(0xFF27313D)
    val outline = Color.White.copy(alpha = 0.92f)
    val label = if (scale.distanceMeters < 1_000) {
        "${scale.distanceMeters} 米"
    } else {
        "${scale.distanceMeters / 1_000} 公里"
    }
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, bottom = if (isLandscape) 116.dp else 220.dp)
            .semantics { contentDescription = "地图比例尺 $label" },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(width = 46.dp, height = 7.dp),
        ) {
            val lineWidth = scale.widthPixels.coerceIn(18f, size.width)
            val stroke = 1.5.dp.toPx()
            val outlineStroke = 3.5.dp.toPx()
            drawLine(outline, Offset(0f, size.height), Offset(lineWidth, size.height), outlineStroke)
            drawLine(outline, Offset(0f, size.height * 0.35f), Offset(0f, size.height), outlineStroke)
            drawLine(outline, Offset(lineWidth, size.height * 0.35f), Offset(lineWidth, size.height), outlineStroke)
            drawLine(foreground, Offset(0f, size.height), Offset(lineWidth, size.height), stroke)
            drawLine(foreground, Offset(0f, size.height * 0.35f), Offset(0f, size.height), stroke)
            drawLine(foreground, Offset(lineWidth, size.height * 0.35f), Offset(lineWidth, size.height), stroke)
        }
    }
}

@Composable
private fun MapZoomButton(
    zoomIn: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(22.dp)) {
            val strokeWidth = 2.4.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val halfLength = size.minDimension * 0.32f
            drawLine(
                color = accent,
                start = Offset(center.x - halfLength, center.y),
                end = Offset(center.x + halfLength, center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            if (zoomIn) {
                drawLine(
                    color = accent,
                    start = Offset(center.x, center.y - halfLength),
                    end = Offset(center.x, center.y + halfLength),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun MapToolsIcon(expanded: Boolean, modifier: Modifier = Modifier) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        repeat(3) { index ->
            val y = size.height * (0.25f + index * 0.25f)
            drawLine(
                contentColor,
                Offset(size.width * 0.12f, y),
                Offset(size.width * 0.88f, y),
                stroke,
                StrokeCap.Round,
            )
            val x = if ((index + if (expanded) 1 else 0) % 2 == 0) {
                size.width * 0.35f
            } else {
                size.width * 0.66f
            }
            drawCircle(contentColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun CurrentLocationIcon(active: Boolean, modifier: Modifier = Modifier) {
    val iconColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.27f
        drawCircle(iconColor, radius, center, style = Stroke(2.dp.toPx()))
        drawCircle(iconColor, radius * 0.32f, center)
        val gap = radius * 1.25f
        drawLine(iconColor, Offset(center.x, 0f), Offset(center.x, center.y - gap), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(center.x, center.y + gap), Offset(center.x, size.height), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(0f, center.y), Offset(center.x - gap, center.y), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(center.x + gap, center.y), Offset(size.width, center.y), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun MapToolButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String = label,
) {
    val interactionModifier = if (description == label) {
        Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Surface(
        modifier = Modifier
            .size(44.dp)
            .then(interactionModifier)
            .semantics { contentDescription = description },
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = if (label.length == 1) 22.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
