package com.simplemap.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
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
    simulated: Boolean = false,
    onNavigationStarted: () -> Unit = {},
    settings: NavigationSettings = NavigationSettings(),
    onSettingsChanged: (NavigationSettings) -> Unit = {},
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
    var settingsPanelVisible by remember { mutableStateOf(false) }
    var voiceGuidanceEnabled by remember(settings.voiceGuidance) { mutableStateOf(settings.voiceGuidance) }
    var trafficLayerEnabled by remember(settings.trafficLayer) { mutableStateOf(settings.trafficLayer) }
    var routeAlertsEnabled by remember(settings.routeAlerts) { mutableStateOf(settings.routeAlerts) }
    var trafficBarEnabled by remember(settings.trafficBar) { mutableStateOf(settings.trafficBar) }
    var eagleMapEnabled by remember(settings.eagleMap) { mutableStateOf(settings.eagleMap) }
    var autoZoomEnabled by remember(settings.autoZoom) { mutableStateOf(settings.autoZoom) }
    var satelliteDialogVisible by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val originalOrientation = remember(activity) { activity?.requestedOrientation }

    BackHandler {
        controller?.stop()
        onExit()
    }
    DisposableEffect(activity) {
        onDispose {
            originalOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = if (isLandscape) {
                        "横屏车机导航布局"
                    } else {
                        "竖屏手机导航布局"
                    }
                },
        )
        val serviceAreaBottomPadding = if (mapInteracting) 146.dp else 104.dp
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
                    navigationController.start(origin, destination, plan.mode, simulated)
                },
                voiceGuidance = settings.voiceGuidance,
                trafficLayer = settings.trafficLayer,
                routeAlerts = settings.routeAlerts,
                trafficBar = settings.trafficBar,
                eagleMap = settings.eagleMap,
                autoZoom = settings.autoZoom,
                isLandscape = isLandscape,
                simulated = simulated,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationPreviewMap()
        }
        if (isLandscape) {
            NavigationLandscapeInformation(
                state = state,
                destinationName = destination.name,
                mapInteracting = mapInteracting,
                onOverview = { controller?.overview() },
                onSettings = { settingsPanelVisible = true },
                onExit = {
                    controller?.stop()
                    onExit()
                },
                modifier = Modifier.align(Alignment.TopStart).width(360.dp),
            )
        } else {
            NavigationInstructionCard(
                state = state,
                destinationName = destination.name,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        NavigationGpsStatus(
            state = state,
            onClick = { satelliteDialogVisible = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 18.dp, end = 22.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(
                    start = 16.dp,
                    top = if (isLandscape) 214.dp else 118.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavigationSpeedBubble(state = state)
            NavigationIntervalSpeed(state = state)
        }
        NavigationCurrentRoad(
            road = state.currentRoad,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 18.dp else if (mapInteracting) 132.dp else 90.dp),
        )
        NavigationServiceAreas(
            serviceAreas = state.serviceAreas,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(
                    start = 14.dp,
                    bottom = if (isLandscape) 18.dp else serviceAreaBottomPadding,
                ),
        )
        if (!isLandscape) {
            NavigationStatusCard(
                state = state,
                mapInteracting = mapInteracting,
                onOverview = { controller?.overview() },
                onSettings = { settingsPanelVisible = true },
                onExit = {
                    controller?.stop()
                    onExit()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (satelliteDialogVisible) {
            NavigationSatelliteDialog(
                state = state,
                onDismiss = { satelliteDialogVisible = false },
            )
        }
        if (settingsPanelVisible) {
            NavigationSettingsPanel(
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                trafficLayerEnabled = trafficLayerEnabled,
                routeAlertsEnabled = routeAlertsEnabled,
                trafficBarEnabled = trafficBarEnabled,
                eagleMapEnabled = eagleMapEnabled,
                autoZoomEnabled = autoZoomEnabled,
                isLandscape = isLandscape,
                onVoiceGuidanceChange = { enabled ->
                    voiceGuidanceEnabled = enabled
                    controller?.setVoiceGuidance(enabled)
                    onSettingsChanged(
                        settings.copy(
                            voiceGuidance = enabled,
                            trafficLayer = trafficLayerEnabled,
                            routeAlerts = routeAlertsEnabled,
                            trafficBar = trafficBarEnabled,
                            eagleMap = eagleMapEnabled,
                            autoZoom = autoZoomEnabled,
                        ),
                    )
                },
                onTrafficLayerChange = { enabled ->
                    trafficLayerEnabled = enabled
                    controller?.setTrafficLayer(enabled)
                    onSettingsChanged(
                        settings.copy(
                            voiceGuidance = voiceGuidanceEnabled,
                            trafficLayer = enabled,
                            routeAlerts = routeAlertsEnabled,
                            trafficBar = trafficBarEnabled,
                            eagleMap = eagleMapEnabled,
                            autoZoom = autoZoomEnabled,
                        ),
                    )
                },
                onRouteAlertsChange = { enabled ->
                    routeAlertsEnabled = enabled
                    controller?.setRouteAlerts(enabled)
                    onSettingsChanged(
                        settings.copy(
                            voiceGuidance = voiceGuidanceEnabled,
                            trafficLayer = trafficLayerEnabled,
                            routeAlerts = enabled,
                            trafficBar = trafficBarEnabled,
                            eagleMap = eagleMapEnabled,
                            autoZoom = autoZoomEnabled,
                        ),
                    )
                },
                onTrafficBarChange = { enabled ->
                    trafficBarEnabled = enabled
                    controller?.setTrafficBar(enabled)
                    onSettingsChanged(settings.copy(trafficBar = enabled, eagleMap = eagleMapEnabled, autoZoom = autoZoomEnabled))
                },
                onEagleMapChange = { enabled ->
                    eagleMapEnabled = enabled
                    controller?.setEagleMap(enabled)
                    onSettingsChanged(settings.copy(trafficBar = trafficBarEnabled, eagleMap = enabled, autoZoom = autoZoomEnabled))
                },
                onAutoZoomChange = { enabled ->
                    autoZoomEnabled = enabled
                    controller?.setAutoZoom(enabled)
                    onSettingsChanged(settings.copy(trafficBar = trafficBarEnabled, eagleMap = eagleMapEnabled, autoZoom = enabled))
                },
                onOverview = { controller?.overview() },
                onOrientationChange = {
                    activity?.requestedOrientation = if (isLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                },
                onDismiss = { settingsPanelVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun NavigationCurrentRoad(
    road: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 360.dp),
        color = Color(0xE61B2B3A),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
    ) {
        Text(
            text = road.ifBlank { "正在定位当前道路" },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NavigationSettingsPanel(
    voiceGuidanceEnabled: Boolean,
    trafficLayerEnabled: Boolean,
    routeAlertsEnabled: Boolean,
    trafficBarEnabled: Boolean,
    eagleMapEnabled: Boolean,
    autoZoomEnabled: Boolean,
    isLandscape: Boolean,
    onVoiceGuidanceChange: (Boolean) -> Unit,
    onTrafficLayerChange: (Boolean) -> Unit,
    onRouteAlertsChange: (Boolean) -> Unit,
    onTrafficBarChange: (Boolean) -> Unit,
    onEagleMapChange: (Boolean) -> Unit,
    onAutoZoomChange: (Boolean) -> Unit,
    onOverview: () -> Unit,
    onOrientationChange: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x52000000))
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { contentDescription = "关闭导航设置" },
    ) {
        Surface(
            modifier = modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp)
                .widthIn(max = 320.dp)
                .clickable(enabled = false) {},
            color = Color(0xFCFFFFFF),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("导航设置", color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("当前行程", color = Color(0xFF66758B), fontSize = 11.sp)
                    }
                    Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("完成") }
                }
                NavigationSettingToggle("语音播报", voiceGuidanceEnabled, { onVoiceGuidanceChange(!voiceGuidanceEnabled) })
                NavigationSettingToggle("实时路况", trafficLayerEnabled, { onTrafficLayerChange(!trafficLayerEnabled) })
                NavigationSettingToggle("偏航与拥堵提醒", routeAlertsEnabled, { onRouteAlertsChange(!routeAlertsEnabled) })
                NavigationSettingToggle("路况柱", trafficBarEnabled, { onTrafficBarChange(!trafficBarEnabled) })
                NavigationSettingToggle("鹰眼总览", eagleMapEnabled, { onEagleMapChange(!eagleMapEnabled) })
                NavigationSettingToggle("自动缩放", autoZoomEnabled, { onAutoZoomChange(!autoZoomEnabled) })
                NavigationSettingCommand("路线总览", "查看完整路线与剩余路段") {
                    onOverview()
                    onDismiss()
                }
                NavigationSettingCommand(
                    if (isLandscape) "切换竖屏" else "切换横屏",
                    "切换当前导航显示方向",
                    onOrientationChange,
                )
                Text(
                    "语音语言跟随高德内置语音资源与系统地区设置，当前 SDK 未提供运行时语言包切换接口。",
                    color = Color(0xFF788497),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun NavigationSettingCommand(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label 导航设置" },
        color = Color(0xFFF2F5F9),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = Color(0xFF243B5A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = Color(0xFF788497), fontSize = 10.sp)
        }
    }
}

@Composable
private fun NavigationPreviewMap() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCAD6D2)),
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
        drawPath(road, Color(0xFFE8EEEB), style = Stroke(40f, cap = StrokeCap.Round))
        drawPath(road, Color(0xFF147D64), style = Stroke(10f, cap = StrokeCap.Round))
        drawCircle(
            color = Color.White,
            radius = 13f,
            center = Offset(size.width * 0.44f, size.height * 0.63f),
        )
        drawCircle(
            color = Color(0xFF147D64),
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
        color = Color(0xE61B2B3A),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp,
    ) {
        NavigationInstructionContent(state, destinationName)
    }
}

@Composable
private fun NavigationLandscapeInformation(
    state: NavigationUiState,
    destinationName: String,
    mapInteracting: Boolean,
    onOverview: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 14.dp, top = 10.dp),
        color = Color(0xE61B2B3A),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp,
    ) {
        Column {
            NavigationInstructionContent(state, destinationName)
            androidx.compose.material3.HorizontalDivider(color = Color(0x405B706A))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationLandscapeMetric(formatNavigationTime(state.remainingTimeSeconds), "时间")
                NavigationLandscapeDivider()
                NavigationLandscapeMetric(formatNavigationDistance(state.remainingDistanceMeters), "剩余")
                NavigationLandscapeDivider()
                NavigationLandscapeMetric("${state.remainingTrafficLights}", "红绿灯")
                NavigationLandscapeDivider()
                NavigationLandscapeMetric(formatArrivalTime(state.remainingTimeSeconds), "预计")
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Color(0xFFBFD5CE),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = mapInteracting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction("总览", Color(0xFF294139), Color(0xFFDCECE6), onOverview, Modifier.weight(1f))
                    NavigationAction("设置", Color(0xFF294139), Color(0xFFDCECE6), onSettings, Modifier.weight(1f))
                    NavigationAction("结束", Color(0xFF5B3535), Color(0xFFFFD4D0), onExit, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavigationInstructionContent(
    state: NavigationUiState,
    destinationName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.maneuverIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导航转向指示 ${state.maneuverIconType}",
                modifier = Modifier.size(64.dp),
            )
        } ?: ManeuverIcon(
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
                Text(
                    text = "${formatNavigationDistance(state.maneuverDistanceMeters)} 后",
                    color = Color(0xFF83D2BA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
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
    }
}

@Composable
private fun NavigationLandscapeMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Color(0xFF9FB6AF), fontSize = 9.sp)
    }
}

@Composable
private fun NavigationLandscapeDivider() {
    Box(Modifier.size(width = 1.dp, height = 28.dp).background(Color(0x405B706A)))
}

@Composable
private fun NavigationGpsStatus(
    state: NavigationUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "GPS 卫星状态" },
        color = if (state.gpsAvailable) Color(0xF527405F) else Color(0xF56A3138),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
    ) {
        Text(
            text = if (state.gpsAvailable) {
                "GPS ${state.satelliteStatus.usedInFixCount}"
            } else {
                "GPS 弱"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = if (state.gpsAvailable) Color(0xFF8EC7FF) else Color(0xFFFFB7B7),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NavigationSatelliteDialog(
    state: NavigationUiState,
    onDismiss: () -> Unit,
) {
    val satellite = state.satelliteStatus
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("卫星定位详情", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("可见 ${satellite.visibleCount} 颗 · 参与定位 ${satellite.usedInFixCount} 颗")
                Text("平均信号强度 %.1f dB-Hz".format(satellite.averageCn0DbHz))
                if (satellite.systems.isEmpty()) {
                    Text("正在等待卫星数据", color = Color(0xFF66758B))
                } else {
                    satellite.systems.forEach { (system, count) ->
                        Text("$system：$count 颗")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("知道了")
            }
        },
    )
}

@Composable
private fun NavigationIntervalSpeed(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    val averageSpeed = state.intervalAverageSpeedKmh ?: return
    Surface(
        modifier = modifier
            .size(66.dp)
            .semantics { contentDescription = "区间测速 平均 $averageSpeed 公里每小时" },
        color = Color(0xFFF9FCFA),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF147D64)),
        shadowElevation = 8.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("区间", color = Color(0xFF4F625D), fontSize = 9.sp)
            Text("$averageSpeed", color = Color(0xFF172033), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("均速", color = Color(0xFF4F625D), fontSize = 9.sp)
        }
    }
}

@Composable
private fun NavigationServiceAreas(
    serviceAreas: List<com.simplemap.navigation.NavigationServiceArea>,
    modifier: Modifier = Modifier,
) {
    if (serviceAreas.isEmpty()) return
    Column(
        modifier = modifier.widthIn(max = 210.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        serviceAreas.take(2).forEach { serviceArea ->
            Surface(
                color = Color(0xE61B2B3A),
                shape = RoundedCornerShape(7.dp),
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "${serviceArea.name} ${formatNavigationDistance(serviceArea.distanceMeters)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (serviceArea.remainingTimeSeconds > 0) {
                        Text(
                            text = "约 ${formatNavigationTime(serviceArea.remainingTimeSeconds)}后到达",
                            color = Color(0xFFB9C8DD),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 10.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${state.currentSpeedKmh}",
                    color = Color(0xFF172033),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text("km/h", color = Color(0xFF66758B), fontSize = 9.sp)
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
    onSettings: () -> Unit,
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
        shape = RoundedCornerShape(18.dp),
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
            androidx.compose.animation.AnimatedVisibility(visible = mapInteracting) {
                Column {
                    Spacer(Modifier.height(7.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFFE8ECEA))
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationAction("总览", Color(0xFFEDF3FC), Color(0xFF243B5A), onOverview, Modifier.weight(1f))
                        NavigationAction("设置", Color(0xFFEDF3FC), Color(0xFF243B5A), onSettings, Modifier.weight(1f))
                        NavigationAction("结束", Color(0xFFF7E7E5), Color(0xFFB43E36), onExit, Modifier.weight(1f))
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

private fun formatArrivalTime(remainingSeconds: Int): String = java.time.LocalTime.now()
    .plusSeconds(remainingSeconds.toLong())
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))