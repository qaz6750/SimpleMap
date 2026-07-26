package com.simplemap.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.ui.theme.SimpleMapBlue

@Composable
internal fun NavigationCurrentRoad(
    road: String,
    nightMode: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = if (compact) 260.dp else 360.dp),
        color = if (nightMode) PortraitNavigationPanelColor else DayPanelSurface,
        shape = RoundedCornerShape(50),
        shadowElevation = 10.dp,
    ) {
        Text(
            text = road.ifBlank { "正在定位当前道路" },
            modifier = Modifier.padding(
                horizontal = if (compact) 13.dp else 18.dp,
                vertical = if (compact) 6.dp else 9.dp,
            ),
            color = if (nightMode) Color.White else NavigationInk,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun NavigationHighwayExit(
    exit: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 220.dp)
            .semantics { contentDescription = "高速出口 $exit" },
        color = NavigationPanelColor,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("高速出口", color = NavigationAccentText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(exit, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
internal fun NavigationJunctionView(
    bitmap: android.graphics.Bitmap?,
    lanes: List<NavigationLane>,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) return
    val revealProgress = remember(bitmap) { Animatable(0f) }
    LaunchedEffect(bitmap) {
        revealProgress.animateTo(1f, animationSpec = tween(320))
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = revealProgress.value
                val scale = 0.94f + revealProgress.value * 0.06f
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = "路口放大图"
                if (lanes.isNotEmpty()) {
                    stateDescription = lanes.joinToString(", ") { lane ->
                        if (lane.recommended) "推荐${lane.direction.label}" else lane.direction.label
                    }
                }
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun NavigationPreviewMap(nightMode: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(if (nightMode) NightPreviewMapBackground else DayPreviewMapBackground),
    ) {
        val road = Path().apply {
            moveTo(size.width * 0.2f, size.height)
            cubicTo(
                size.width * 0.4f,
                size.height * 0.8f,
                size.width * 0.35f,
                size.height * 0.58f,
                size.width * 0.62f,
                size.height * 0.43f,
            )
            cubicTo(
                size.width * 0.8f,
                size.height * 0.33f,
                size.width * 0.7f,
                size.height * 0.18f,
                size.width,
                0f,
            )
        }
        drawPath(
            road,
            if (nightMode) NightPreviewRoad else DayPreviewRoad,
            style = Stroke(40f, cap = StrokeCap.Round),
        )
        drawPath(road, SimpleMapBlue, style = Stroke(10f, cap = StrokeCap.Round))
        drawCircle(
            color = Color.White,
            radius = 13f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
        drawCircle(
            color = SimpleMapBlue,
            radius = 8f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
    }
}

@Composable
internal fun NavigationInstructionCard(
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    lanes: List<NavigationLane>,
    routeNotice: NavigationRouteNotice?,
    compactGuidance: Boolean,
    compactInstruction: Boolean,
    destinationName: String,
    reserveGpsSpace: Boolean = false,
    junctionViewBitmap: android.graphics.Bitmap? = null,
    junctionViewHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .semantics { contentDescription = "竖屏导航信息卡" },
        color = PortraitNavigationPanelColor,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 16.dp,
    ) {
        Column {
            NavigationPortraitInstructionContent(
                guidanceState = guidanceState,
                maneuverIconBitmap = maneuverIconBitmap,
                destinationName = destinationName,
                endPadding = if (reserveGpsSpace) 52.dp else 16.dp,
                compact = compactGuidance || compactInstruction,
            )
            NavigationRouteNoticeBanner(routeNotice)
            if (lanes.isNotEmpty() && junctionViewBitmap == null) {
                NavigationPortraitLaneGuidance(lanes = lanes)
            }
            if (junctionViewBitmap != null) {
                androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    lanes = lanes,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
        }
    }
}

@Composable
internal fun NavigationPortraitInstructionContent(
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    destinationName: String,
    endPadding: androidx.compose.ui.unit.Dp,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, end = endPadding, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 52.dp else 68.dp
        maneuverIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导航转向指示 ${guidanceState.maneuverIconType}",
                modifier = Modifier.size(iconSize),
            )
        } ?: ManeuverIcon(
            iconType = guidanceState.maneuverIconType,
            modifier = Modifier.size(iconSize),
            backgroundColor = Color.Transparent,
            arrowColor = Color.White,
        )
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (guidanceState.maneuverDistanceMeters > 0) {
                Text(
                    text = formatNavigationDistance(guidanceState.maneuverDistanceMeters),
                    color = Color.White,
                    fontSize = if (compact) 27.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = guidanceState.nextRoad.ifBlank { guidanceState.instruction },
                color = Color.White,
                fontSize = if (compact) 17.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (guidanceState.maneuverDistanceMeters <= 0) {
                Text(
                    text = if (guidanceState.phase == NavigationPhase.Arrived) {
                        "已到达目的地附近"
                    } else {
                        "前往 $destinationName"
                    },
                    color = NavigationSecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun NavigationPortraitLaneGuidance(lanes: List<NavigationLane>) {
    androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "竖屏车道引导"
                stateDescription = lanes.joinToString(", ") { lane ->
                    if (lane.recommended) "推荐${lane.direction.label}" else lane.direction.label
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lanes.forEachIndexed { index, lane ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 34.dp)
                        .background(NavigationPanelDivider),
                )
            }
            Box(
                modifier = Modifier.size(width = 42.dp, height = 46.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lane.direction.symbol,
                    color = if (lane.recommended) Color.White else NightLaneInactive,
                    fontSize = if (lane.direction.symbol.length > 1) 14.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
