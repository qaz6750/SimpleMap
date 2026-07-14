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
    onNavigationStarted: () -> Unit = {},
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
    var navigationRecorded by remember { mutableStateOf(false) }
    var mapInteracting by remember(previewState) { mutableStateOf(previewState != null) }
    var quickSettingsVisible by remember { mutableStateOf(false) }
    var voiceGuidanceEnabled by remember(settings.voiceGuidance) { mutableStateOf(settings.voiceGuidance) }
    var trafficLayerEnabled by remember(settings.trafficLayer) { mutableStateOf(settings.trafficLayer) }

    Box(modifier = modifier.fillMaxSize()) {
        if (showLiveNavigation) {
            AmapNavigationView(
                onControllerReady = { navigationController ->
                    controller = navigationController
                    navigationController.setOnStateChanged { state = it }
                    navigationController.setOnNavigationStarted {
                        if (!navigationRecorded) {
                            navigationRecorded = true
                            onNavigationStarted()
                        }
                    }
                    navigationController.setOnMapInteractionChanged { mapInteracting = it }
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
                .padding(top = 128.dp),
        )
        NavigationSpeedBubble(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    bottom = when {
                        quickSettingsVisible -> 176.dp
                        mapInteracting -> 132.dp
                        else -> 92.dp
                    },
                ),
        )
        NavigationStatusCard(
            state = state,
            mapInteracting = mapInteracting,
            onOverview = { controller?.overview() },
            onRecover = { controller?.recoverFollowing() },
            onSettings = { quickSettingsVisible = !quickSettingsVisible },
            quickSettingsVisible = quickSettingsVisible,
            voiceGuidanceEnabled = voiceGuidanceEnabled,
            trafficLayerEnabled = trafficLayerEnabled,
            onVoiceGuidanceChange = { enabled ->
                voiceGuidanceEnabled = enabled
                controller?.setVoiceGuidance(enabled)
            },
            onTrafficLayerChange = { enabled ->
                trafficLayerEnabled = enabled
                controller?.setTrafficLayer(enabled)
            },
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
            .background(Color(0xFF16243A)),
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
        drawPath(road, Color(0xFF40506A), style = Stroke(40f, cap = StrokeCap.Round))
        drawPath(road, Color(0xFF5BA6FF), style = Stroke(10f, cap = StrokeCap.Round))
        drawCircle(
            color = Color.White,
            radius = 13f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
        drawCircle(
            color = Color(0xFF1769E0),
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
        color = Color(0xF5162438),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManeuverIcon(
                iconType = state.maneuverIconType,
                modifier = Modifier.size(64.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
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
                            color = Color(0xFF75B8FF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " 后",
                            color = Color(0xFFC5D2E5),
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
                        color = Color(0xFFC5D2E5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            Surface(
                color = if (state.gpsAvailable) Color(0xFF27405F) else Color(0xFF6A3138),
                shape = RoundedCornerShape(5.dp),
            ) {
                Text(
                    text = if (state.gpsAvailable) "GPS" else "GPS 弱",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = if (state.gpsAvailable) Color(0xFF8EC7FF) else Color(0xFFFFB7B7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun NavigationRoadStatus(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .widthIn(max = 650.dp),
        horizontalAlignment = Alignment.Start,
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
        if (state.serviceAreaName != null || state.intervalRemainingMeters != null || state.cameraDistanceMeters != null) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.serviceAreaName?.let { name ->
                    NavigationInfoChip(
                        text = "$name ${formatNavigationDistance(state.serviceAreaDistanceMeters ?: 0)}",
                    )
                }
                state.intervalRemainingMeters?.let { distance ->
                    NavigationInfoChip(
                        text = "区间测速 ${formatNavigationDistance(distance)}" +
                            state.intervalAverageSpeedKmh?.let { " · 均速 $it" }.orEmpty(),
                    )
                } ?: state.cameraDistanceMeters?.let { distance ->
                    NavigationInfoChip(text = "测速 ${formatNavigationDistance(distance)}")
                }
            }
        }
    }
}

@Composable
private fun NavigationInfoChip(text: String) {
    Surface(
        color = Color(0xEFFFFFFF),
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = Color(0xFF33435A),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavigationSpeedBubble(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(70.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).size(58.dp),
            color = Color(0xF5162438),
            shape = CircleShape,
            shadowElevation = 10.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${state.currentSpeedKmh}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text("km/h", color = Color(0xFFB9C8DD), fontSize = 9.sp)
            }
        }
        state.speedLimitKmh?.let { speedLimit ->
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                color = Color.White,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFD83A3A)),
                shadowElevation = 5.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$speedLimit",
                        color = Color(0xFF202A3A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
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
        drawCircle(Color(0xFF263650), radius = size.minDimension / 2f, center = center)
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
            color = Color(0xFF75B8FF),
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun NavigationStatusCard(
    state: NavigationUiState,
    mapInteracting: Boolean,
    onOverview: () -> Unit,
    onRecover: () -> Unit,
    onSettings: () -> Unit,
    quickSettingsVisible: Boolean,
    voiceGuidanceEnabled: Boolean,
    trafficLayerEnabled: Boolean,
    onVoiceGuidanceChange: (Boolean) -> Unit,
    onTrafficLayerChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatNavigationTime(state.remainingTimeSeconds),
                    color = Color(0xFF1769E0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                )
                Text(
                    text = "剩余 ${formatNavigationDistance(state.remainingDistanceMeters)}",
                    color = Color(0xFF172033),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                if (state.remainingTrafficLights > 0) {
                    Text(
                        text = "${state.remainingTrafficLights} 个灯",
                        color = Color(0xFF66758B),
                        fontSize = 11.sp,
                    )
                }
            }
            state.message?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = Color(0xFFEDF3FC),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFF24558F),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (mapInteracting) {
                Spacer(Modifier.height(7.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFFE8ECEA))
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction("总览", Color(0xFFEDF3FC), Color(0xFF243B5A), onOverview, Modifier.weight(1f))
                    NavigationAction("回正", Color(0xFFEDF3FC), Color(0xFF243B5A), onRecover, Modifier.weight(1f))
                    NavigationAction("设置", Color(0xFFEDF3FC), Color(0xFF243B5A), onSettings, Modifier.weight(1f))
                    NavigationAction("结束", Color(0xFFF7E7E5), Color(0xFFB43E36), onExit, Modifier.weight(1f))
                }
                if (quickSettingsVisible) {
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationSettingToggle(
                            label = "语音",
                            enabled = voiceGuidanceEnabled,
                            onClick = { onVoiceGuidanceChange(!voiceGuidanceEnabled) },
                            modifier = Modifier.weight(1f),
                        )
                        NavigationSettingToggle(
                            label = "实时路况",
                            enabled = trafficLayerEnabled,
                            onClick = { onTrafficLayerChange(!trafficLayerEnabled) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
private fun NavigationSettingToggle(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { contentDescription = "$label 导航设置" },
        color = if (enabled) Color(0xFFE5F0FF) else Color(0xFFF2F4F7),
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            text = "$label ${if (enabled) "已开启" else "已关闭"}",
            modifier = Modifier.padding(vertical = 8.dp),
            color = if (enabled) Color(0xFF1769E0) else Color(0xFF66758B),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
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
            color = if (emphasized) Color(0xFF1769E0) else Color(0xFF172033),
            fontWeight = FontWeight.Bold,
            fontSize = if (emphasized) 20.sp else 17.sp,
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
private fun NavigationActionIcon(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (label) {
            "总览" -> {
                drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(1.8f))
                drawLine(color, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height * 0.24f), 1.8f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.76f), Offset(size.width * 0.5f, size.height), 1.8f)
                drawLine(color, Offset(0f, size.height * 0.5f), Offset(size.width * 0.24f, size.height * 0.5f), 1.8f)
                drawLine(color, Offset(size.width * 0.76f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1.8f)
            }
            "回正" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.05f)
                    lineTo(size.width * 0.82f, size.height * 0.92f)
                    lineTo(size.width * 0.5f, size.height * 0.72f)
                    lineTo(size.width * 0.18f, size.height * 0.92f)
                    close()
                }
                drawPath(path, color)
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