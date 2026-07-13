package com.simplemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.AmapNavigationController
import com.simplemap.navigation.AmapNavigationView
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationUiState
import com.simplemap.route.RoutePlan
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings

@Composable
internal fun NavigationScreen(
    origin: Place,
    destination: Place,
    plan: RoutePlan,
    showLiveNavigation: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    settings: NavigationSettings = NavigationSettings(),
    previewState: NavigationUiState? = null,
) {
    var controller by remember { mutableStateOf<AmapNavigationController?>(null) }
    var state by remember {
        mutableStateOf(
            previewState ?: NavigationUiState(
                phase = NavigationPhase.Preparing,
                instruction = "正在准备前往 ${destination.name}",
                remainingDistanceMeters = plan.distanceMeters,
                remainingTimeSeconds = plan.durationSeconds.toInt(),
            ),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showLiveNavigation) {
            AmapNavigationView(
                onControllerReady = { navigationController ->
                    controller = navigationController
                    navigationController.setOnStateChanged { state = it }
                    navigationController.start(origin, destination, plan.mode)
                },
                voiceGuidance = settings.voiceGuidance,
                trafficLayer = settings.trafficLayer,
                routeAlerts = settings.routeAlerts,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationPreviewMap()
        }
        NavigationInstructionCard(
            state = state,
            destinationName = destination.name,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        NavigationRoadStatus(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 142.dp),
        )
        NavigationStatusCard(
            state = state,
            onOverview = { controller?.overview() },
            onRecover = { controller?.recoverFollowing() },
            onExit = {
                controller?.stop()
                onExit()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NavigationPreviewMap() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15342D)),
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
        drawPath(road, Color(0xFF3F534D), style = Stroke(40f, cap = StrokeCap.Round))
        drawPath(road, Color(0xFF74D3AE), style = Stroke(10f, cap = StrokeCap.Round))
        drawCircle(
            color = Color.White,
            radius = 13f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
        drawCircle(
            color = Color(0xFF126B56),
            radius = 8f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
    }
}

@Composable
private fun NavigationInstructionCard(
    state: NavigationUiState,
    destinationName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xF516211F),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManeuverIcon(
                iconType = state.maneuverIconType,
                modifier = Modifier.size(72.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text = state.nextRoad.ifBlank { state.instruction },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                if (state.maneuverDistanceMeters > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatNavigationDistance(state.maneuverDistanceMeters),
                            color = Color(0xFF71E0B5),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " 后",
                            color = Color(0xFFB8CCC5),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Text(
                        text = when {
                            state.phase == NavigationPhase.Arrived -> "已到达目的地附近"
                            state.message != null -> state.message
                            else -> "前往 $destinationName"
                        },
                        color = Color(0xFFB8CCC5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationRoadStatus(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .widthIn(max = 650.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xEFFFFFFF),
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 5.dp,
        ) {
            Text(
                text = state.currentRoad.ifBlank { "正在定位当前道路" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFF263330),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Surface(
            color = if (state.gpsAvailable) Color(0xEFFFFFFF) else Color(0xFFFBE8E5),
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 5.dp,
        ) {
            Text(
                text = if (state.gpsAvailable) "GPS" else "GPS 弱",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                color = if (state.gpsAvailable) Color(0xFF126B56) else Color(0xFFB43E36),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ManeuverIcon(
    iconType: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "导航转向指示 $iconType"
        },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF2B3B37), radius = size.minDimension / 2f, center = center)
        val path = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.78f)
            lineTo(size.width * 0.35f, size.height * 0.43f)
            cubicTo(
                size.width * 0.35f,
                size.height * 0.28f,
                size.width * 0.47f,
                size.height * 0.22f,
                size.width * 0.62f,
                size.height * 0.22f,
            )
            lineTo(size.width * 0.72f, size.height * 0.22f)
            moveTo(size.width * 0.62f, size.height * 0.12f)
            lineTo(size.width * 0.74f, size.height * 0.22f)
            lineTo(size.width * 0.62f, size.height * 0.32f)
        }
        drawPath(
            path = path,
            color = Color(0xFF71E0B5),
            style = Stroke(width = 7f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun NavigationStatusCard(
    state: NavigationUiState,
    onOverview: () -> Unit,
    onRecover: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationMetric(
                    value = formatNavigationTime(state.remainingTimeSeconds),
                    label = "预计用时",
                    modifier = Modifier.weight(1.35f),
                    emphasized = true,
                )
                NavigationMetricDivider()
                NavigationMetric(
                    value = "剩余 ${formatNavigationDistance(state.remainingDistanceMeters)}",
                    label = "里程",
                    modifier = Modifier.weight(1f),
                )
                NavigationMetricDivider()
                NavigationMetric(
                    value = "${state.currentSpeedKmh}",
                    label = "km/h",
                    modifier = Modifier.weight(.8f),
                )
            }
            if (state.remainingTrafficLights > 0) {
                Text(
                    text = "前方还有 ${state.remainingTrafficLights} 个红绿灯",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Color(0xFF6A7572),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(color = Color(0xFFE8ECEA))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NavigationAction("总览", Color(0xFFEDF3F0), Color(0xFF263330), onOverview, Modifier.weight(1f))
                NavigationAction("回正", Color(0xFFEDF3F0), Color(0xFF263330), onRecover, Modifier.weight(1f))
                NavigationAction("结束", Color(0xFFF7E7E5), Color(0xFFB43E36), onExit, Modifier.weight(1f))
            }
            if (state.phase == NavigationPhase.Failed || state.phase == NavigationPhase.Arrived) {
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17211F)),
                ) {
                    Text(if (state.phase == NavigationPhase.Arrived) "完成行程" else "返回路线规划")
                }
            }
        }
    }
}

@Composable
private fun NavigationMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (emphasized) Color(0xFF126B56) else Color(0xFF17211F),
            fontWeight = FontWeight.Bold,
            fontSize = if (emphasized) 23.sp else 19.sp,
            maxLines = 1,
        )
        Text(label, color = Color(0xFF7A8582), fontSize = 11.sp)
    }
}

@Composable
private fun NavigationMetricDivider() {
    Box(Modifier.size(width = 1.dp, height = 34.dp).background(Color(0xFFE2E8E5)))
}

@Composable
private fun NavigationAction(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label 导航" },
        color = background,
        shape = RoundedCornerShape(7.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (label) {
                    "总览" -> "⌖"
                    "回正" -> "↑"
                    else -> "■"
                },
                color = foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
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

internal fun formatNavigationDistance(distanceMeters: Int): String = when {
    distanceMeters < 0 -> "--"
    distanceMeters < 1_000 -> "$distanceMeters 米"
    else -> "%.1f 公里".format(distanceMeters / 1_000f)
}

internal fun formatNavigationTime(remainingSeconds: Int): String {
    val minutes = (remainingSeconds.coerceAtLeast(0) + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        remainingMinutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分"
    }
}