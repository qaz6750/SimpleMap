package com.simplemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun MapBackdrop() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(MapBackdropBase)
            .semantics { contentDescription = "地图区域" },
    ) {
        val road = Path().apply {
            moveTo(-40f, size.height * 0.78f)
            cubicTo(
                size.width * 0.2f,
                size.height * 0.6f,
                size.width * 0.45f,
                size.height * 0.84f,
                size.width + 40f,
                size.height * 0.46f,
            )
        }
        drawPath(road, color = Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round))
        drawPath(road, color = MapBackdropRoadEdge, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(
            MapBackdropBlockNear,
            radius = 92f,
            center = Offset(size.width * 0.18f, size.height * 0.28f),
        )
        drawCircle(
            MapBackdropBlockFar,
            radius = 135f,
            center = Offset(size.width * 0.82f, size.height * 0.22f),
        )
    }
}
