package com.simplemap.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simplemap.navigation.AmapNavigationController
import com.simplemap.navigation.AmapNavigationView
import com.simplemap.navigation.NavigationAlternativeRoute
import com.simplemap.navigation.NavigationFacilityKind
import com.simplemap.navigation.NavigationLocationIssue
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteFacility
import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.navigation.NavigationTrafficAlert
import com.simplemap.navigation.NavigationTrafficIncident
import com.simplemap.navigation.NavigationTrafficLevel
import com.simplemap.navigation.NavigationUiState
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.ui.theme.SimpleMapBlue
import kotlinx.coroutines.delay

private val NavigationPanelColor = Color(0xF21A2B42)
private val NavigationPanelDivider = Color(0x405F8FC4)
private val NavigationSecondaryText = Color(0xFFB9CBE4)
private val NavigationAccentText = Color(0xFF8EC7FF)

@Composable
internal fun NavigationScreen(
    origin: Place,
    destination: Place,
    plan: RoutePlan,
    routeRequest: RouteRequest = RouteRequest(origin, destination, mode = plan.mode),
    showLiveNavigation: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    simulated: Boolean = false,
    onNavigationStarted: () -> Unit = {},
    onNavigationFinished: (NavigationPhase, NavigationUiState) -> Unit = { _, _ -> },
    onFindParking: () -> Unit = {},
    onSaveParkingLocation: (Double, Double) -> Unit = { _, _ -> },
    settings: NavigationSettings = NavigationSettings(),
    onSettingsChanged: (NavigationSettings) -> Unit = {},
    previewState: NavigationUiState? = null,
    previewMapInteracting: Boolean = false,
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
    var navigationFinished by remember { mutableStateOf(false) }
    var mapInteracting by remember(previewMapInteracting) { mutableStateOf(previewMapInteracting) }
    var settingsPanelVisible by remember { mutableStateOf(false) }
    var facilitiesPanelVisible by remember { mutableStateOf(false) }
    var voiceGuidanceEnabled by remember(settings.voiceGuidance) { mutableStateOf(settings.voiceGuidance) }
    var trafficLayerEnabled by remember(settings.trafficLayer) { mutableStateOf(settings.trafficLayer) }
    var routeAlertsEnabled by remember(settings.routeAlerts) { mutableStateOf(settings.routeAlerts) }
    var trafficBarEnabled by remember(settings.trafficBar) { mutableStateOf(settings.trafficBar) }
    var eagleMapEnabled by remember(settings.eagleMap) { mutableStateOf(settings.eagleMap) }
    var autoZoomEnabled by remember(settings.autoZoom) { mutableStateOf(settings.autoZoom) }
    var nightModeEnabled by remember(settings.nightMode) { mutableStateOf(settings.nightMode) }
    var satelliteDialogVisible by remember { mutableStateOf(false) }
    var satelliteDismissSeconds by remember { mutableStateOf(5) }
    var visibleRouteNotice by remember { mutableStateOf<NavigationRouteNotice?>(null) }
    val activity = LocalActivity.current
    val originalOrientation = remember(activity) { activity?.requestedOrientation }

    LaunchedEffect(state.phase) {
        if (!navigationFinished &&
            (state.phase == NavigationPhase.Arrived || state.phase == NavigationPhase.Failed)
        ) {
            navigationFinished = true
            onNavigationFinished(state.phase, state)
        }
    }

    LaunchedEffect(satelliteDialogVisible) {
        if (!satelliteDialogVisible) return@LaunchedEffect
        satelliteDismissSeconds = 5
        while (satelliteDismissSeconds > 0) {
            delay(1_000L)
            satelliteDismissSeconds -= 1
        }
        satelliteDialogVisible = false
    }

    LaunchedEffect(state.routeNotice?.id) {
        visibleRouteNotice = state.routeNotice
        if (visibleRouteNotice != null) {
            delay(6_000L)
            visibleRouteNotice = null
        }
    }

    fun persistCurrentSettings() {
        onSettingsChanged(
            settings.copy(
                voiceGuidance = voiceGuidanceEnabled,
                trafficLayer = trafficLayerEnabled,
                routeAlerts = routeAlertsEnabled,
                trafficBar = trafficBarEnabled,
                eagleMap = eagleMapEnabled,
                autoZoom = autoZoomEnabled,
                nightMode = nightModeEnabled,
            ),
        )
    }

    BackHandler {
        if (satelliteDialogVisible) {
            satelliteDialogVisible = false
            return@BackHandler
        }
        if (settingsPanelVisible) {
            settingsPanelVisible = false
            return@BackHandler
        }
        if (facilitiesPanelVisible) {
            facilitiesPanelVisible = false
            return@BackHandler
        }
        if (!navigationFinished) {
            navigationFinished = true
            onNavigationFinished(state.phase, state)
        }
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
        val landscapeInformationWidth = minOf(maxWidth * 0.5f, 360.dp)
        val landscapeJunctionHeight = minOf(
            landscapeInformationWidth * 9f / 16f,
            maxHeight * 0.2f,
        )
        val portraitJunctionHeight = minOf(
            (maxWidth - 28.dp) * 9f / 16f,
            maxHeight * 0.2f,
        )
        val compactGuidance = if (isLandscape) maxHeight < 360.dp else maxHeight < 600.dp
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
                    navigationController.start(routeRequest, simulated)
                },
                voiceGuidance = settings.voiceGuidance,
                trafficLayer = settings.trafficLayer,
                routeAlerts = settings.routeAlerts,
                trafficBar = settings.trafficBar,
                eagleMap = settings.eagleMap,
                autoZoom = settings.autoZoom,
                nightMode = nightModeEnabled,
                isLandscape = isLandscape,
                simulated = simulated,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationPreviewMap(nightMode = nightModeEnabled)
        }
        if (isLandscape) {
            NavigationLandscapeInformation(
                state = state,
                routeNotice = visibleRouteNotice,
                compactGuidance = compactGuidance,
                destinationName = destination.name,
                junctionViewBitmap = state.junctionViewBitmap,
                junctionViewHeight = landscapeJunctionHeight,
                mapInteracting = mapInteracting,
                onRecoverFollowing = { controller?.recoverFollowing() },
                onSettings = {
                    satelliteDialogVisible = false
                    facilitiesPanelVisible = false
                    settingsPanelVisible = true
                },
                onExit = {
                    if (!navigationFinished) {
                        navigationFinished = true
                        onNavigationFinished(state.phase, state)
                    }
                    controller?.stop()
                    onExit()
                },
                onFindParking = onFindParking,
                onSaveParkingLocation = {
                    val latitude = state.latitude
                    val longitude = state.longitude
                    if (latitude != null && longitude != null) onSaveParkingLocation(latitude, longitude)
                },
                parkingLocationAvailable = state.latitude != null && state.longitude != null,
                modifier = Modifier.align(Alignment.TopStart).width(landscapeInformationWidth),
            )
        } else {
            NavigationInstructionCard(
                state = state,
                routeNotice = visibleRouteNotice,
                compactGuidance = compactGuidance,
                destinationName = destination.name,
                reserveGpsSpace = true,
                junctionViewBitmap = state.junctionViewBitmap,
                junctionViewHeight = portraitJunctionHeight,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        if (!satelliteDialogVisible && !settingsPanelVisible && !facilitiesPanelVisible) {
            NavigationGpsStatus(
                state = state,
                onClick = {
                    satelliteDismissSeconds = 5
                    satelliteDialogVisible = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 18.dp, end = 22.dp),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isLandscape || state.junctionViewBitmap == null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(
                    start = if (isLandscape) landscapeInformationWidth + 24.dp else 16.dp,
                    top = if (isLandscape) 18.dp else 118.dp,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NavigationSpeedBubble(state = state)
                NavigationIntervalSpeed(state = state)
            }
        }
        NavigationCurrentRoad(
            road = state.currentRoad,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 18.dp else if (mapInteracting) 132.dp else 90.dp),
        )
        val overlayVisible = satelliteDialogVisible || settingsPanelVisible || facilitiesPanelVisible
        androidx.compose.animation.AnimatedVisibility(
            visible = (state.highwayExit.isNotBlank() || state.routeFacilities.isNotEmpty()) && !overlayVisible &&
                !(isLandscape && state.junctionViewBitmap != null),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(
                    start = 14.dp,
                    bottom = if (isLandscape) 18.dp else serviceAreaBottomPadding,
                ),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.highwayExit.isNotBlank()) {
                    NavigationHighwayExit(exit = state.highwayExit)
                }
                NavigationFacilitiesPreview(
                    facilities = state.routeFacilities,
                    onClick = {
                        facilitiesPanelVisible = true
                    },
                )
            }
        }
        if (!isLandscape) {
            NavigationStatusCard(
                state = state,
                mapInteracting = mapInteracting,
                onRecoverFollowing = { controller?.recoverFollowing() },
                onSettings = {
                    satelliteDialogVisible = false
                    facilitiesPanelVisible = false
                    settingsPanelVisible = true
                },
                onExit = {
                    if (!navigationFinished) {
                        navigationFinished = true
                        onNavigationFinished(state.phase, state)
                    }
                    controller?.stop()
                    onExit()
                },
                onFindParking = onFindParking,
                onSaveParkingLocation = {
                    val latitude = state.latitude
                    val longitude = state.longitude
                    if (latitude != null && longitude != null) onSaveParkingLocation(latitude, longitude)
                },
                parkingLocationAvailable = state.latitude != null && state.longitude != null,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (satelliteDialogVisible) {
            NavigationSatellitePanel(
                state = state,
                dismissSeconds = satelliteDismissSeconds,
                onDismiss = { satelliteDialogVisible = false },
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.CenterStart)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp)
                        .width(landscapeInformationWidth)
                        .heightIn(max = maxHeight * 0.9f)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.55f)
                },
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
                nightModeEnabled = nightModeEnabled,
                isLandscape = isLandscape,
                alternativeRoutes = state.alternativeRoutes,
                onVoiceGuidanceChange = { enabled ->
                    voiceGuidanceEnabled = enabled
                    controller?.setVoiceGuidance(enabled)
                    persistCurrentSettings()
                },
                onTrafficLayerChange = { enabled ->
                    trafficLayerEnabled = enabled
                    controller?.setTrafficLayer(enabled)
                    persistCurrentSettings()
                },
                onRouteAlertsChange = { enabled ->
                    routeAlertsEnabled = enabled
                    controller?.setRouteAlerts(enabled)
                    persistCurrentSettings()
                },
                onTrafficBarChange = { enabled ->
                    trafficBarEnabled = enabled
                    controller?.setTrafficBar(enabled)
                    persistCurrentSettings()
                },
                onEagleMapChange = { enabled ->
                    eagleMapEnabled = enabled
                    controller?.setEagleMap(enabled)
                    persistCurrentSettings()
                },
                onAutoZoomChange = { enabled ->
                    autoZoomEnabled = enabled
                    controller?.setAutoZoom(enabled)
                    persistCurrentSettings()
                },
                onNightModeChange = { enabled ->
                    nightModeEnabled = enabled
                    controller?.setNightMode(enabled)
                    persistCurrentSettings()
                },
                onOverview = { controller?.overview() },
                onAlternativeRouteSelected = { controller?.selectAlternativeRoute(it) },
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
        if (facilitiesPanelVisible) {
            NavigationFacilitiesPanel(
                facilities = state.routeFacilities,
                onDismiss = { facilitiesPanelVisible = false },
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.CenterStart)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp)
                        .width(landscapeInformationWidth)
                        .fillMaxHeight()
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.55f)
                },
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
        color = NavigationPanelColor,
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
private fun NavigationHighwayExit(
    exit: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 220.dp)
            .semantics { contentDescription = "高速出口 $exit" },
        color = NavigationPanelColor,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("高速出口", color = NavigationAccentText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(exit, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
private fun NavigationJunctionView(
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) return
    Box(
        modifier = modifier.semantics { contentDescription = "路口放大图" },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
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
    nightModeEnabled: Boolean,
    isLandscape: Boolean,
    alternativeRoutes: List<NavigationAlternativeRoute>,
    onVoiceGuidanceChange: (Boolean) -> Unit,
    onTrafficLayerChange: (Boolean) -> Unit,
    onRouteAlertsChange: (Boolean) -> Unit,
    onTrafficBarChange: (Boolean) -> Unit,
    onEagleMapChange: (Boolean) -> Unit,
    onAutoZoomChange: (Boolean) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    onOverview: () -> Unit,
    onAlternativeRouteSelected: (Long) -> Unit,
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
                NavigationSettingToggle("夜间模式", nightModeEnabled, { onNightModeChange(!nightModeEnabled) })
                NavigationSettingCommand("路线总览", "查看完整路线与剩余路段") {
                    onOverview()
                    onDismiss()
                }
                if (alternativeRoutes.size > 1) {
                    Text(
                        "导航中备选路线",
                        color = Color(0xFF243B5A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    alternativeRoutes.forEach { route ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !route.selected,
                                    role = Role.Button,
                                    onClick = {
                                        onAlternativeRouteSelected(route.pathId)
                                        onDismiss()
                                    },
                                )
                                .semantics { contentDescription = "选择备选路线 ${route.label}" },
                            color = if (route.selected) Color(0xFFE5F0FF) else Color(0xFFF2F5F9),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(
                                    if (route.selected) "${route.label} · 当前路线" else route.label,
                                    color = Color(0xFF243B5A),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    "${formatNavigationTime(route.durationSeconds)} · ${formatNavigationDistance(route.distanceMeters)} · 过路费 ${route.tollCostYuan} 元",
                                    color = Color(0xFF788497),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
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
private fun NavigationPreviewMap(nightMode: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(if (nightMode) Color(0xFF111A29) else Color(0xFFD8E2F0)),
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
            if (nightMode) Color(0xFF2D3C54) else Color(0xFFF3F6FB),
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
private fun NavigationInstructionCard(
    state: NavigationUiState,
    routeNotice: NavigationRouteNotice?,
    compactGuidance: Boolean,
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
        color = NavigationPanelColor,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp,
    ) {
        Column {
            NavigationInstructionContent(
                state = state,
                destinationName = destinationName,
                endPadding = if (reserveGpsSpace) 76.dp else 14.dp,
            )
            NavigationGuidanceAlerts(state, routeNotice, compactGuidance)
            if (junctionViewBitmap != null) {
                androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
        }
    }
}

@Composable
private fun NavigationLandscapeInformation(
    state: NavigationUiState,
    routeNotice: NavigationRouteNotice?,
    compactGuidance: Boolean,
    destinationName: String,
    junctionViewBitmap: android.graphics.Bitmap?,
    junctionViewHeight: androidx.compose.ui.unit.Dp,
    mapInteracting: Boolean,
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
            .statusBarsPadding()
            .padding(start = 14.dp, top = 10.dp)
            .semantics { contentDescription = "横屏导航信息卡" },
        color = NavigationPanelColor,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp,
    ) {
        Column {
            NavigationInstructionContent(state, destinationName)
            NavigationGuidanceAlerts(state, routeNotice, compactGuidance)
            if (junctionViewBitmap != null) {
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
            androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
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
                    color = NavigationSecondaryText,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.phase == NavigationPhase.Arrived) {
                NavigationArrivalActions(
                    onFindParking = onFindParking,
                    onSaveParkingLocation = onSaveParkingLocation,
                    parkingLocationAvailable = parkingLocationAvailable,
                    onExit = onExit,
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = mapInteracting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction("跟随", Color(0xFF263F62), Color.White, onRecoverFollowing, Modifier.weight(1f))
                    NavigationAction("设置", Color(0xFF263F62), Color.White, onSettings, Modifier.weight(1f))
                    NavigationAction("结束", Color(0xFF5B3535), Color(0xFFFFD4D0), onExit, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavigationGuidanceAlerts(
    state: NavigationUiState,
    routeNotice: NavigationRouteNotice?,
    compact: Boolean,
) {
    if (compact) {
        when {
            routeNotice != null -> NavigationRouteNoticeBanner(routeNotice)
            state.trafficIncident != null -> NavigationTrafficIncidentBanner(state.trafficIncident)
            else -> NavigationTrafficBanner(state.trafficAlert)
        }
    } else {
        NavigationRouteNoticeBanner(routeNotice)
        NavigationTrafficBanner(state.trafficAlert)
        NavigationTrafficIncidentBanner(state.trafficIncident)
    }
}

@Composable
private fun NavigationTrafficIncidentBanner(incident: NavigationTrafficIncident?) {
    androidx.compose.animation.AnimatedVisibility(visible = incident != null) {
        incident ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF402F35))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "${incident.typeLabel} ${incident.title} 距离 ${formatNavigationDistance(incident.distanceMeters)}"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(incident.typeLabel, color = Color(0xFFFFB4AB), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(incident.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(
                formatNavigationDistance(incident.distanceMeters),
                color = Color(0xFFFFB4AB),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NavigationTrafficBanner(alert: NavigationTrafficAlert?) {
    androidx.compose.animation.AnimatedContent(
        targetState = alert,
        transitionSpec = {
            androidx.compose.animation.fadeIn().togetherWith(androidx.compose.animation.fadeOut())
        },
        label = "live traffic",
    ) { currentAlert ->
        if (currentAlert == null) return@AnimatedContent
        val label = when (currentAlert.level) {
            NavigationTrafficLevel.Slow -> "缓行"
            NavigationTrafficLevel.Congested -> "拥堵"
            NavigationTrafficLevel.SeverelyCongested -> "严重拥堵"
            NavigationTrafficLevel.Smooth -> "畅通"
            NavigationTrafficLevel.Unknown -> "路况未知"
        }
        val color = when (currentAlert.level) {
            NavigationTrafficLevel.Slow -> Color(0xFFF2B134)
            NavigationTrafficLevel.Congested -> Color(0xFFF07B32)
            NavigationTrafficLevel.SeverelyCongested -> Color(0xFFD83A3A)
            NavigationTrafficLevel.Smooth -> Color(0xFF24A866)
            NavigationTrafficLevel.Unknown -> NavigationSecondaryText
        }
        val position = if (currentAlert.distanceMeters == 0) {
            "当前路段"
        } else {
            "前方 ${formatNavigationDistance(currentAlert.distanceMeters)}"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16273B))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "$position $label 影响 ${formatNavigationDistance(currentAlert.affectedLengthMeters)}"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text("$position $label", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "影响 ${formatNavigationDistance(currentAlert.affectedLengthMeters)}",
                color = NavigationSecondaryText,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun NavigationRouteNoticeBanner(notice: NavigationRouteNotice?) {
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
        val accent = if (currentNotice.important) Color(0xFFFFB4AB) else NavigationAccentText
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (currentNotice.important) Color(0xFF56343B) else Color(0xFF203B5E))
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
private fun NavigationInstructionContent(
    state: NavigationUiState,
    destinationName: String,
    endPadding: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, end = endPadding, bottom = 12.dp),
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
                    color = NavigationAccentText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (state.instruction.isNotBlank() && state.instruction != state.nextRoad) {
                    Text(
                        text = state.instruction,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
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
    }
}

@Composable
private fun NavigationLandscapeMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = NavigationSecondaryText, fontSize = 9.sp)
    }
}

@Composable
private fun NavigationLandscapeDivider() {
    Box(Modifier.size(width = 1.dp, height = 28.dp).background(NavigationPanelDivider))
}

@Composable
private fun NavigationGpsStatus(
    state: NavigationUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnostic = state.locationDiagnostic
    Surface(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "GPS 卫星状态" },
        color = if (state.gpsAvailable && diagnostic == null) Color(0xF527405F) else Color(0xF56A3138),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
    ) {
        Text(
            text = when {
                !state.gpsAvailable -> "GPS 弱"
                diagnostic?.issue == NavigationLocationIssue.LowAccuracy -> "GPS 漂移"
                diagnostic?.issue == NavigationLocationIssue.OffRoute -> "待校准"
                else -> "GPS ${state.satelliteStatus.usedInFixCount}"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = if (state.gpsAvailable && diagnostic == null) Color(0xFF8EC7FF) else Color(0xFFFFB7B7),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NavigationSatellitePanel(
    state: NavigationUiState,
    dismissSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val satellite = state.satelliteStatus
    Surface(
        modifier = modifier.semantics { contentDescription = "GPS 定位详情面板" },
        color = NavigationPanelColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("GPS 定位详情", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${dismissSeconds.coerceAtLeast(0)} 秒后自动关闭",
                        color = NavigationAccentText,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = NavigationAccentText) }
            }
            androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.locationDiagnostic?.let { diagnostic ->
                    val title = if (diagnostic.issue == NavigationLocationIssue.LowAccuracy) {
                        "GPS 信号漂移"
                    } else {
                        "可能偏离导航路线"
                    }
                    val detail = if (diagnostic.issue == NavigationLocationIssue.LowAccuracy) {
                        "定位精度较低，暂不判断为真实偏航"
                    } else {
                        "连续定位未匹配路线，等待导航重新校准"
                    }
                    Surface(color = Color(0xFF56343B), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(detail, color = Color(0xFFFFDAD6), fontSize = 10.sp)
                        }
                    }
                    NavigationSatelliteMetric("当前定位精度", "约 ${diagnostic.accuracyMeters} 米")
                }
                NavigationSatelliteMetric("可见卫星", "${satellite.visibleCount} 颗")
                NavigationSatelliteMetric("参与定位", "${satellite.usedInFixCount} 颗")
                NavigationSatelliteMetric("平均信号", "%.1f dB-Hz".format(satellite.averageCn0DbHz))
                if (satellite.systems.isEmpty()) {
                    Text("正在等待卫星数据", color = NavigationSecondaryText, fontSize = 12.sp)
                } else {
                    satellite.systems.forEach { (system, count) ->
                        NavigationSatelliteMetric(system, "$count 颗")
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationSatelliteMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = NavigationSecondaryText, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NavigationIntervalSpeed(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    val averageSpeed = state.intervalAverageSpeedKmh
    val cameraDistance = state.cameraDistanceMeters
    if (averageSpeed == null && cameraDistance == null) return
    val remainingDistance = state.intervalRemainingMeters
    val recommendedSpeed = state.intervalRecommendedSpeedKmh
    val description = if (averageSpeed != null) {
        buildString {
            append("区间测速 平均 $averageSpeed 公里每小时")
            remainingDistance?.let { append(" 剩余 ${formatNavigationDistance(it)}") }
            recommendedSpeed?.let { append(" 建议 $it 公里每小时") }
        }
    } else {
        "前方电子眼 距离 ${formatNavigationDistance(cameraDistance ?: 0)}"
    }
    Surface(
        modifier = modifier
            .width(92.dp)
            .heightIn(min = 66.dp)
            .semantics { contentDescription = description },
        color = NavigationPanelColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, SimpleMapBlue),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (averageSpeed != null) "区间测速" else "前方电子眼", color = NavigationAccentText, fontSize = 9.sp)
            Text(
                if (averageSpeed != null) "$averageSpeed km/h" else formatNavigationDistance(cameraDistance ?: 0),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            remainingDistance?.let {
                Text("剩余 ${formatNavigationDistance(it)}", color = NavigationSecondaryText, fontSize = 8.sp, maxLines = 1)
            }
            recommendedSpeed?.let {
                Text("建议 $it km/h", color = NavigationSecondaryText, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavigationFacilitiesPreview(
    facilities: List<NavigationRouteFacility>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (facilities.isEmpty()) return
    Column(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看全部沿途设施" },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        facilities.take(2).forEach { facility ->
            Surface(
                color = NavigationPanelColor,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "${facility.kind.label} · ${facility.name}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = facility.distanceAndTimeLabel,
                        color = NavigationAccentText,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = "查看全路线 ${facilities.size} 处设施",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(SimpleMapBlue, RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun NavigationFacilitiesPanel(
    facilities: List<NavigationRouteFacility>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "全路线沿途设施" },
        color = NavigationPanelColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("沿途设施", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("全路线 ${facilities.size} 处", color = NavigationAccentText, fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = NavigationAccentText) }
            }
            androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { contentDescription = "沿途设施列表" },
            ) {
                items(facilities, key = { "${it.kind}-${it.name}" }) { facility ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = SimpleMapBlue,
                            shape = RoundedCornerShape(7.dp),
                        ) {
                            Text(
                                facility.kind.shortLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(facility.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(facility.distanceAndTimeLabel, color = NavigationSecondaryText, fontSize = 11.sp)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = Color(0x305F8FC4))
                }
            }
        }
    }
}

private val NavigationFacilityKind.label: String
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> "服务区"
        NavigationFacilityKind.TollGate -> "收费站"
    }

private val NavigationFacilityKind.shortLabel: String
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> "服务"
        NavigationFacilityKind.TollGate -> "收费"
    }

private val NavigationRouteFacility.distanceAndTimeLabel: String
    get() = buildString {
        append(formatNavigationDistance(distanceMeters))
        if (remainingTimeSeconds > 0) {
            append(" · 约 ")
            append(formatNavigationTime(remainingTimeSeconds))
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
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .semantics { contentDescription = "竖屏导航状态卡" },
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
                        NavigationAction("跟随", Color(0xFFEDF3FC), Color(0xFF243B5A), onRecoverFollowing, Modifier.weight(1f))
                        NavigationAction("设置", Color(0xFFEDF3FC), Color(0xFF243B5A), onSettings, Modifier.weight(1f))
                        NavigationAction("结束", Color(0xFFF7E7E5), Color(0xFFB43E36), onExit, Modifier.weight(1f))
                    }
                }
            }
            if (state.phase == NavigationPhase.Arrived) {
                NavigationArrivalActions(
                    onFindParking = onFindParking,
                    onSaveParkingLocation = onSaveParkingLocation,
                    parkingLocationAvailable = parkingLocationAvailable,
                    onExit = onExit,
                )
            } else if (state.phase == NavigationPhase.Failed) {
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17211F)),
                ) {
                    Text("返回路线规划")
                }
            }
        }
    }
}

@Composable
private fun NavigationArrivalActions(
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
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("附近停车场", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onSaveParkingLocation,
            enabled = parkingLocationAvailable,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("保存停车位置", fontSize = 12.sp)
        }
    }
    Button(
        onClick = onExit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17211F)),
    ) {
        Text("完成行程")
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