package com.simplemap.ui

import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationRouteNotice

@Composable
internal fun NavigationRouteNoticeBanner(notice: NavigationRouteNotice?) {
    androidx.compose.animation.AnimatedContent(
        targetState = notice,
        transitionSpec = {
            (androidx.compose.animation.fadeIn() +
                androidx.compose.animation.slideInVertically(initialOffsetY = { -it / 2 }))
                .togetherWith(
                    androidx.compose.animation.fadeOut() +
                        androidx.compose.animation.slideOutVertically(targetOffsetY = { -it / 2 }),
                )
        },
        label = "route notice",
    ) { currentNotice ->
        if (currentNotice == null) return@AnimatedContent
        val accent = if (currentNotice.important) NightWarningText else NavigationAccentText
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (currentNotice.important) NightImportantNoticeContainer else NightInfoContainer)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .semantics { contentDescription = "路线提示 ${currentNotice.title}" },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentNotice.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                currentNotice.distanceMeters?.let { distance ->
                    Text(formatNavigationDistance(distance), color = accent, fontSize = 11.sp)
                }
            }
            if (currentNotice.detail.isNotBlank()) {
                Text(currentNotice.detail, color = NavigationSecondaryText, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun NavigationLaneGuidancePanel(lanes: List<NavigationLane>, modifier: Modifier = Modifier) {
    if (lanes.isEmpty()) return
    Surface(
        modifier = modifier,
        color = LaneGuidanceBlue,
        shape = PanelShapeSmall,
        shadowElevation = 12.dp,
    ) {
        NavigationLaneGuidance(lanes)
    }
}

@Composable
internal fun NavigationLaneGuidance(lanes: List<NavigationLane>) {
    if (lanes.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp)
            .semantics {
                contentDescription = lanes.joinToString(", ") { lane ->
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
                        .size(width = 1.dp, height = 32.dp)
                        .background(LaneGuidanceDivider),
                )
            }
            Box(
                modifier = Modifier.size(width = 40.dp, height = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lane.direction.symbol,
                    color = if (lane.recommended) Color.White else LaneGuidanceInactive,
                    fontSize = if (lane.direction.symbol.length > 1) 12.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun ManeuverIcon(
    iconType: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ManeuverIconBackground,
    arrowColor: Color = ManeuverIconArrow,
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "导航转向指示 $iconType"
        },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        if (backgroundColor.alpha > 0f) {
            drawCircle(backgroundColor, radius = size.minDimension / 2f, center = center)
        }
        val rightTurn = iconType in setOf(2, 4, 6, 10, 12)
        val leftTurn = iconType in setOf(3, 5, 7, 11, 13)
        val uTurn = iconType in setOf(8, 9)
        val path = Path().apply {
            when {
                uTurn -> {
                    moveTo(size.width * 0.64f, size.height * 0.8f)
                    lineTo(size.width * 0.64f, size.height * 0.38f)
                    cubicTo(
                        size.width * 0.64f,
                        size.height * 0.17f,
                        size.width * 0.34f,
                        size.height * 0.17f,
                        size.width * 0.34f,
                        size.height * 0.38f,
                    )
                    lineTo(size.width * 0.34f, size.height * 0.52f)
                    moveTo(size.width * 0.22f, size.height * 0.4f)
                    lineTo(size.width * 0.34f, size.height * 0.54f)
                    lineTo(size.width * 0.46f, size.height * 0.4f)
                }
                rightTurn || leftTurn -> {
                    val direction = if (rightTurn) 1f else -1f
                    val startX = if (rightTurn) 0.36f else 0.64f
                    val endX = if (rightTurn) 0.74f else 0.26f
                    moveTo(size.width * startX, size.height * 0.8f)
                    lineTo(size.width * startX, size.height * 0.43f)
                    cubicTo(
                        size.width * startX,
                        size.height * 0.29f,
                        size.width * (startX + 0.12f * direction),
                        size.height * 0.22f,
                        size.width * endX,
                        size.height * 0.22f,
                    )
                    moveTo(size.width * (endX - 0.12f * direction), size.height * 0.1f)
                    lineTo(size.width * endX, size.height * 0.22f)
                    lineTo(size.width * (endX - 0.12f * direction), size.height * 0.34f)
                }
                else -> {
                    moveTo(size.width * 0.5f, size.height * 0.82f)
                    lineTo(size.width * 0.5f, size.height * 0.18f)
                    moveTo(size.width * 0.36f, size.height * 0.32f)
                    lineTo(size.width * 0.5f, size.height * 0.16f)
                    lineTo(size.width * 0.64f, size.height * 0.32f)
                }
            }
        }
        drawPath(
            path = path,
            color = arrowColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}
