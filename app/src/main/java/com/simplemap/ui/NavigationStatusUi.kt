package com.simplemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationPhase

@Composable
internal fun NavigationStatusCard(
    phase: NavigationPhase,
    tripSummaryState: NavigationTripSummaryState,
    message: String?,
    nightMode: Boolean,
    mapInteracting: Boolean,
    onOverview: () -> Unit,
    onRecoverFollowing: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onFindParking: () -> Unit,
    onSaveParkingLocation: () -> Unit,
    parkingLocationAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .semantics { contentDescription = "竖屏导航状态卡" },
        color = if (nightMode) NavigationPanelColor else DayPanelSurface,
        shape = PanelShapeMedium,
        shadowElevation = 16.dp,
    ) {
        Column {
            if (!mapInteracting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "竖屏底部行程信息" },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavigationTripMetric(
                        "剩余",
                        formatNavigationTime(tripSummaryState.remainingTimeSeconds),
                        nightMode,
                        false,
                        Modifier.weight(1f),
                    )
                    NavigationTripMetric(
                        "距离",
                        formatNavigationDistance(tripSummaryState.remainingDistanceMeters),
                        nightMode,
                        false,
                        Modifier.weight(1f),
                    )
                    NavigationTripMetric(
                        "预计到达",
                        formatNavigationArrivalTime(tripSummaryState.remainingTimeSeconds),
                        nightMode,
                        false,
                        Modifier.weight(1f),
                    )
                    if (tripSummaryState.remainingTrafficLights > 0) {
                        NavigationTripMetric(
                            "红绿灯",
                            "${tripSummaryState.remainingTrafficLights} 个",
                            nightMode,
                            false,
                            Modifier.weight(1f),
                        )
                    }
                }
                message?.takeIf(String::isNotBlank)?.let { statusMessage ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavigationStatusBadge(
                            text = statusMessage,
                            nightMode = nightMode,
                            emphasized = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (phase != NavigationPhase.Arrived && phase != NavigationPhase.Failed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction(
                        if (mapInteracting) "继续导航" else "总览",
                        if (nightMode) NightActionContainer else MaterialTheme.colorScheme.primaryContainer,
                        if (nightMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        if (mapInteracting) onRecoverFollowing else onOverview,
                        Modifier.weight(1f),
                    )
                    NavigationAction(
                        "设置",
                        if (nightMode) NightActionContainer else MaterialTheme.colorScheme.primaryContainer,
                        if (nightMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        onSettings,
                        Modifier.weight(1f),
                    )
                    NavigationAction(
                        "结束",
                        if (nightMode) NightErrorContainer else MaterialTheme.colorScheme.errorContainer,
                        if (nightMode) NightOnErrorContainer else MaterialTheme.colorScheme.onErrorContainer,
                        onExit,
                        Modifier.weight(1f),
                    )
                }
            }
            if (phase == NavigationPhase.Arrived) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    NavigationArrivalActions(
                        onFindParking = onFindParking,
                        onSaveParkingLocation = onSaveParkingLocation,
                        parkingLocationAvailable = parkingLocationAvailable,
                        onExit = onExit,
                    )
                }
            } else if (phase == NavigationPhase.Failed) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Button(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                        shape = PanelShapeSmall,
                        colors = ButtonDefaults.buttonColors(containerColor = NavigationInk),
                    ) {
                        Text("返回路线规划")
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavigationTripMetric(
    label: String,
    value: String,
    nightMode: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = if (compact) 2.dp else 4.dp)
            .semantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            color = if (nightMode) Color.White else DayInkText,
            fontSize = if (compact) 11.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = if (nightMode) NavigationSecondaryText else DaySecondaryText,
            fontSize = if (compact) 8.sp else 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun NavigationArrivalActions(
    onFindParking: () -> Unit,
    onSaveParkingLocation: () -> Unit,
    parkingLocationAvailable: Boolean,
    onExit: () -> Unit,
) {
    Spacer(Modifier.size(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onFindParking,
            modifier = Modifier.weight(1f),
            shape = PanelShapeSmall,
        ) {
            Text("附近停车场", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onSaveParkingLocation,
            enabled = parkingLocationAvailable,
            modifier = Modifier.weight(1f),
            shape = PanelShapeSmall,
        ) {
            Text("保存停车位置", fontSize = 12.sp)
        }
    }
    Button(
        onClick = onExit,
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShapeSmall,
        colors = ButtonDefaults.buttonColors(containerColor = NavigationInk),
    ) {
        Text("完成行程")
    }
}

@Composable
internal fun NavigationStatusBadge(
    text: String,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        color = if (emphasized) {
            if (nightMode) NightInfoContainer else MaterialTheme.colorScheme.primaryContainer
        } else if (nightMode) {
            NightSurfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = PanelShapeSmall,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (emphasized) {
                    if (nightMode) NavigationAccentText else MaterialTheme.colorScheme.onPrimaryContainer
                } else if (nightMode) {
                    NavigationSecondaryText
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun NavigationAction(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label 导航" },
        color = background,
        shape = RoundedCornerShape(7.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavigationActionIcon(label = label, color = foreground, modifier = Modifier.size(17.dp))
            Text(
                text = label,
                modifier = Modifier.padding(start = 7.dp),
                color = foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun NavigationActionIcon(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (label) {
            "退出", "结束" -> {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.18f), Offset(size.width * 0.18f, size.height * 0.82f), 1.8f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.18f), Offset(size.width * 0.55f, size.height * 0.18f), 1.8f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.82f), Offset(size.width * 0.55f, size.height * 0.82f), 1.8f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.38f, center.y), Offset(size.width * 0.92f, center.y), 2.2f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.3f), Offset(size.width * 0.92f, center.y), 2.2f, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.7f), Offset(size.width * 0.92f, center.y), 2.2f, StrokeCap.Round)
            }
            "总览" -> {
                drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(1.8f))
                drawLine(color, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height * 0.24f), 1.8f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.76f), Offset(size.width * 0.5f, size.height), 1.8f)
                drawLine(color, Offset(0f, size.height * 0.5f), Offset(size.width * 0.24f, size.height * 0.5f), 1.8f)
                drawLine(color, Offset(size.width * 0.76f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1.8f)
            }
            "设置" -> {
                drawCircle(color, radius = size.minDimension * 0.34f, center = center, style = Stroke(1.8f))
                drawCircle(color, radius = size.minDimension * 0.1f, center = center)
                repeat(4) { index ->
                    val horizontal = index % 2 == 0
                    val start = if (horizontal) Offset(0f, center.y) else Offset(center.x, 0f)
                    val end = if (horizontal) Offset(size.width, center.y) else Offset(center.x, size.height)
                    drawLine(color, start, end, 1.8f, StrokeCap.Round)
                }
            }
            else -> drawCircle(color, radius = size.minDimension * 0.34f, center = center)
        }
    }
}
