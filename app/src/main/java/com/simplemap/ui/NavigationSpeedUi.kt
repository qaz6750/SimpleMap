package com.simplemap.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NavigationSpeedBubble(
    currentSpeedKmh: Int,
    speedLimitKmh: Int?,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val speeding = speedLimitKmh?.let { currentSpeedKmh > it } == true
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(speeding) {
        if (!speeding) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        pulse.animateTo(1.12f, animationSpec = tween(240))
        pulse.animateTo(1f, animationSpec = tween(420))
    }
    Box(
        modifier = modifier
            .size(70.dp)
            .semantics {
                contentDescription = if (speeding) {
                    "当前车速 $currentSpeedKmh，已超速"
                } else {
                    "当前车速 $currentSpeedKmh"
                }
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(58.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
            color = when {
                speeding -> SpeedingRed
                nightMode -> NightSpeedPanel
                else -> Color.White
            },
            shape = CircleShape,
            border = BorderStroke(2.dp, NavigationBlueAccent),
            shadowElevation = 10.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "$currentSpeedKmh",
                    color = if (speeding || nightMode) Color.White else DayInkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    "km/h",
                    color = if (speeding) {
                        SpeedingRedContainerText
                    } else if (nightMode) {
                        NavigationSecondaryText
                    } else {
                        DayTertiaryText
                    },
                    fontSize = 9.sp,
                )
            }
        }
        speedLimitKmh?.let { speedLimit ->
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(3.dp, SpeedingRed),
                shadowElevation = 5.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$speedLimit",
                        color = DayInkText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NavigationIntervalSpeedCard(
    averageSpeedKmh: Int,
    remainingMeters: Int?,
    recommendedSpeedKmh: Int?,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append("区间测速 平均 $averageSpeedKmh 公里每小时")
        remainingMeters?.let { append(" 剩余 ${formatNavigationDistance(it)}") }
        recommendedSpeedKmh?.let { append(" 建议 $it 公里每小时") }
    }
    Surface(
        modifier = modifier
            .widthIn(min = 108.dp, max = 156.dp)
            .semantics { contentDescription = description },
        color = if (nightMode) NightSpeedPanel else DayPanelSurface,
        shape = PanelShapeSmall,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = "区间测速",
                color = if (nightMode) NavigationSecondaryText else DaySecondaryText,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$averageSpeedKmh",
                    color = if (nightMode) Color.White else DayInkText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " km/h 平均",
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = if (nightMode) NavigationSecondaryText else DayTertiaryText,
                    fontSize = 8.sp,
                )
            }
            val detail = listOfNotNull(
                remainingMeters?.let { "剩余 ${formatNavigationDistance(it)}" },
                recommendedSpeedKmh?.let { "建议 $it" },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = if (nightMode) NavigationAccentText else GpsPanelAccent,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
