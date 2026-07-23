package com.simplemap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.simplemap.navigation.AmapNavigationController
import com.simplemap.navigation.AmapNavigationView
import com.simplemap.navigation.NavigationAlternativeRoute
import com.simplemap.navigation.NavigationFacilityKind
import com.simplemap.navigation.NavigationGpsMode
import com.simplemap.navigation.NavigationLocationIssue
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteFacility
import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.navigation.NavigationTrafficAlert
import com.simplemap.navigation.NavigationTrafficLevel
import com.simplemap.navigation.NavigationUiState
import com.simplemap.navigation.determineNavigationGpsMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.settings.withVoiceGuidanceLevel
import com.simplemap.ui.theme.SimpleMapBlue
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

private val NavigationPanelColor = Color(0xF21A2B42)
private val NavigationPanelDivider = Color(0x405F8FC4)
private val NavigationSecondaryText = Color(0xFFB9CBE4)
private val NavigationAccentText = Color(0xFF8EC7FF)
private val PortraitNavigationPanelColor = Color(0xF20B1525)
private val GpsPanelBackground = Color(0xFFF5F9FF)
private val GpsPanelSurface = Color(0xFFE3F2FD)
private val GpsPanelDivider = Color(0xFFBBDDFF)
private val GpsPanelText = Color(0xFF1A2B42)
private val GpsPanelSecondaryText = Color(0xFF4C6079)
private val GpsPanelAccent = Color(0xFF1769E0)
private const val MAP_FOLLOW_RECOVERY_DELAY_MILLIS = 10_000L

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
    sessionController: AmapNavigationController? = null,
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
    var mapInteractionGeneration by remember { mutableIntStateOf(if (previewMapInteracting) 1 else 0) }
    var settingsPanelVisible by remember { mutableStateOf(false) }
    var facilitiesPanelVisible by remember { mutableStateOf(false) }
    var voiceGuidanceLevel by remember(settings.resolvedVoiceGuidanceLevel) {
        mutableStateOf(settings.resolvedVoiceGuidanceLevel)
    }
    var quietHoursEnabled by remember(settings.quietHoursEnabled) {
        mutableStateOf(settings.quietHoursEnabled)
    }
    var importantAlertsEnabled by remember(settings.importantAlertsEnabled) {
        mutableStateOf(settings.importantAlertsEnabled)
    }
    var trafficLayerEnabled by remember(settings.trafficLayer) { mutableStateOf(settings.trafficLayer) }
    var routeAlertsEnabled by remember(settings.routeAlerts) { mutableStateOf(settings.routeAlerts) }
    var trafficBarEnabled by remember(settings.trafficBar) { mutableStateOf(settings.trafficBar) }
    var eagleMapEnabled by remember(settings.eagleMap) { mutableStateOf(settings.eagleMap) }
    var autoZoomEnabled by remember(settings.autoZoom) { mutableStateOf(settings.autoZoom) }
    var perspectiveMode by remember(settings.perspectiveMode) { mutableStateOf(settings.perspectiveMode) }
    var themeMode by remember(settings.themeMode) { mutableStateOf(settings.themeMode) }
    var satelliteDialogVisible by remember { mutableStateOf(false) }
    var satelliteDismissSeconds by remember { mutableIntStateOf(5) }
    var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
    var visibleRouteNotice by remember { mutableStateOf<NavigationRouteNotice?>(null) }
    val voiceGuidanceEnabled = voiceGuidanceLevel != VoiceGuidanceLevel.Muted
    val systemInDarkTheme = isSystemInDarkTheme()
    val nightModeEnabled = shouldUseNightTheme(
        mode = themeMode,
        systemInDarkTheme = systemInDarkTheme,
        minuteOfDay = minuteOfDay,
        inTunnel = state.inTunnel,
    )

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
            delay(10_000L)
            visibleRouteNotice = null
        }
    }

    LaunchedEffect(mapInteracting, mapInteractionGeneration) {
        if (!mapInteracting) return@LaunchedEffect
        delay(MAP_FOLLOW_RECOVERY_DELAY_MILLIS)
        mapInteracting = false
        controller?.recoverFollowing()
    }

    LaunchedEffect(themeMode) {
        while (true) {
            minuteOfDay = currentMinuteOfDay()
            delay(60_000L)
        }
    }

    LaunchedEffect(controller, nightModeEnabled) {
        controller?.setNightMode(nightModeEnabled)
    }

    fun persistCurrentSettings(
        selectedVoiceGuidanceLevel: VoiceGuidanceLevel = voiceGuidanceLevel,
        selectedThemeMode: NavigationThemeMode = themeMode,
        selectedPerspectiveMode: NavigationPerspectiveMode = perspectiveMode,
        selectedOrientationMode: AppOrientationMode = settings.orientationMode,
    ) {
        val selectedNightMode = shouldUseNightTheme(
            mode = selectedThemeMode,
            systemInDarkTheme = systemInDarkTheme,
            minuteOfDay = minuteOfDay,
            inTunnel = state.inTunnel,
        )
        onSettingsChanged(
            settings.copy(
                quietHoursEnabled = quietHoursEnabled,
                importantAlertsEnabled = importantAlertsEnabled,
                trafficLayer = trafficLayerEnabled,
                routeAlerts = routeAlertsEnabled,
                trafficBar = trafficBarEnabled,
                eagleMap = eagleMapEnabled,
                autoZoom = autoZoomEnabled,
                perspectiveMode = selectedPerspectiveMode,
                nightMode = selectedNightMode,
                themeMode = selectedThemeMode,
                orientationMode = selectedOrientationMode,
            ).withVoiceGuidanceLevel(selectedVoiceGuidanceLevel),
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
    DisposableEffect(controller) {
        val navigationController = controller
        val token = navigationController?.addStateListener { state = it }
        onDispose {
            if (token != null) navigationController.removeStateListener(token)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        var portraitGuidanceBottomPx by remember { mutableIntStateOf(0) }
        var portraitSpeedClusterBottomPx by remember { mutableIntStateOf(0) }
        var portraitStatusCardTopPx by remember { mutableIntStateOf(0) }
        var landscapeGpsStatusBottomPx by remember { mutableIntStateOf(0) }
        var landscapeLaneGuidanceBottomPx by remember { mutableIntStateOf(0) }
        val safetyNotice = selectNavigationSafetyNotice(state, visibleRouteNotice)
        val landscapeInformationWidth = minOf(maxWidth * 0.34f, 360.dp)
        val landscapeMapWidth = (maxWidth - landscapeInformationWidth).coerceAtLeast(0.dp)
        val landscapeSpeedSlotWidth = 96.dp
        val landscapeGpsSlotWidth = 68.dp
        val landscapeLaneAvailableWidth = (
            landscapeMapWidth - landscapeSpeedSlotWidth - landscapeGpsSlotWidth
        ).coerceAtLeast(0.dp)
        val landscapeLaneWidth = minOf(
            (state.lanes.size * 52 + 20).dp,
            landscapeLaneAvailableWidth,
        )
        val landscapeLaneHeight = (maxHeight * 0.18f).coerceIn(52.dp, 72.dp)
        val landscapeJunctionHeight = state.junctionViewBitmap?.let { bitmap ->
            minOf(
                (landscapeInformationWidth - 14.dp) * bitmap.height / bitmap.width.coerceAtLeast(1),
                maxHeight * 0.62f,
            )
        } ?: 0.dp
        val portraitJunctionHeight = state.junctionViewBitmap?.let { bitmap ->
            minOf(
                (maxWidth - 28.dp) * bitmap.height / bitmap.width.coerceAtLeast(1),
                maxHeight * 0.25f,
            )
        } ?: 0.dp
        val compactGuidance = if (isLandscape) maxHeight < 360.dp else maxHeight < 600.dp
        val overlayVisible = satelliteDialogVisible || settingsPanelVisible || facilitiesPanelVisible
        val portraitSpeedAnchor = if (portraitGuidanceBottomPx > 0) {
            (with(density) { portraitGuidanceBottomPx.toDp() } - 6.dp).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val portraitBottomOverlayPadding = if (portraitStatusCardTopPx > 0) {
            with(density) { (viewportHeightPx - portraitStatusCardTopPx).coerceAtLeast(0).toDp() } + 8.dp
        } else {
            0.dp
        }
        val mapSafeAreaTopPx = if (isLandscape) {
            maxOf(landscapeGpsStatusBottomPx, landscapeLaneGuidanceBottomPx)
        } else {
            portraitSpeedClusterBottomPx
        }
        val mapSafeAreaBottomPx = if (!isLandscape && portraitStatusCardTopPx > 0) {
            (viewportHeightPx - portraitStatusCardTopPx).coerceAtLeast(0)
        } else {
            0
        }
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
        if (showLiveNavigation) {
            AmapNavigationView(
                onControllerReady = { navigationController ->
                    controller = navigationController
                    navigationController.setOnNavigationStarted {
                        if (!navigationRecorded) {
                            navigationRecorded = true
                            onNavigationStarted()
                        }
                    }
                    navigationController.setOnMapInteractionChanged { interacting ->
                        mapInteracting = interacting
                        if (interacting) mapInteractionGeneration += 1
                    }
                    navigationController.start(routeRequest, simulated, plan)
                },
                settings = settings.copy(
                    voiceGuidance = voiceGuidanceEnabled,
                    voiceGuidanceLevel = voiceGuidanceLevel,
                    quietHoursEnabled = quietHoursEnabled,
                    importantAlertsEnabled = importantAlertsEnabled,
                    perspectiveMode = perspectiveMode,
                ),
                trafficLayer = trafficLayerEnabled,
                routeAlerts = routeAlertsEnabled,
                trafficBar = trafficBarEnabled,
                eagleMap = eagleMapEnabled,
                autoZoom = autoZoomEnabled,
                nightMode = nightModeEnabled,
                isLandscape = isLandscape,
                overlaySafeAreaTopPx = mapSafeAreaTopPx,
                overlaySafeAreaBottomPx = mapSafeAreaBottomPx,
                simulated = simulated,
                sessionController = sessionController,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationPreviewMap(nightMode = nightModeEnabled)
        }
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(landscapeInformationWidth),
            ) {
                NavigationLandscapeInformation(
                    state = state,
                    routeNotice = safetyNotice,
                    compactGuidance = compactGuidance,
                    destinationName = destination.name,
                    junctionViewBitmap = state.junctionViewBitmap,
                    junctionViewHeight = landscapeJunctionHeight,
                    mapInteracting = mapInteracting,
                    onRecoverFollowing = {
                        mapInteracting = false
                        controller?.recoverFollowing()
                    },
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
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!overlayVisible && state.junctionViewBitmap == null) {
                    NavigationLandscapeFacilityBands(
                        facilities = visibleNavigationFacilities(state),
                        onClick = { facilitiesPanelVisible = true },
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                    )
                }
            }
        } else {
            NavigationInstructionCard(
                state = state,
                routeNotice = safetyNotice,
                compactGuidance = compactGuidance,
                compactInstruction = state.junctionViewBitmap != null,
                destinationName = destination.name,
                reserveGpsSpace = true,
                junctionViewBitmap = state.junctionViewBitmap,
                junctionViewHeight = portraitJunctionHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned {
                        portraitGuidanceBottomPx = it.boundsInParent().bottom.roundToInt()
                    },
            )
        }
        if (!satelliteDialogVisible && !settingsPanelVisible && !facilitiesPanelVisible) {
            NavigationGpsStatus(
                state = state,
                isLandscape = isLandscape,
                onClick = {
                    satelliteDismissSeconds = 5
                    satelliteDialogVisible = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 14.dp, end = 16.dp)
                    .onGloballyPositioned {
                        if (isLandscape) {
                            landscapeGpsStatusBottomPx = it.boundsInParent().bottom.roundToInt()
                        }
                    },
            )
        }
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = landscapeGpsSlotWidth)
                    .width(landscapeLaneAvailableWidth)
                    .onGloballyPositioned {
                        landscapeLaneGuidanceBottomPx = if (
                            state.lanes.isNotEmpty() && state.junctionViewBitmap == null
                        ) {
                            it.boundsInParent().bottom.roundToInt()
                        } else {
                            0
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.lanes.isNotEmpty() && state.junctionViewBitmap == null,
                ) {
                    NavigationLaneGuidancePanel(
                        lanes = state.lanes,
                        modifier = Modifier
                            .width(landscapeLaneWidth)
                            .height(landscapeLaneHeight)
                            .semantics { contentDescription = "横屏车道引导" },
                    )
                }
            }
        }
        if (isLandscape || portraitGuidanceBottomPx > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (isLandscape) landscapeInformationWidth + 20.dp else 14.dp,
                        top = if (isLandscape) 6.dp else portraitSpeedAnchor,
                    )
                    .onGloballyPositioned {
                        if (!isLandscape) {
                            portraitSpeedClusterBottomPx = it.boundsInParent().bottom.roundToInt()
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NavigationSpeedBubble(state = state, nightMode = nightModeEnabled)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.trafficAlert != null && !overlayVisible,
            modifier = Modifier
                .align(if (isLandscape) Alignment.Center else Alignment.CenterEnd)
                .padding(
                    start = if (isLandscape) landscapeInformationWidth else 0.dp,
                    end = 14.dp,
                ),
        ) {
            state.trafficAlert?.let { alert ->
                NavigationTrafficMapNotice(
                    alert = alert,
                    nightMode = nightModeEnabled,
                )
            }
        }
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = landscapeInformationWidth + 16.dp,
                        end = 16.dp,
                        bottom = maxHeight * 0.18f,
                    )
                    .width((landscapeMapWidth - 32.dp).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                NavigationCurrentRoad(
                    road = state.currentRoad,
                    nightMode = nightModeEnabled,
                )
            }
        } else if (overlayVisible || portraitStatusCardTopPx > 0) {
            NavigationCurrentRoad(
                road = state.currentRoad,
                nightMode = nightModeEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (overlayVisible) {
                            Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                        } else {
                            Modifier.padding(bottom = portraitBottomOverlayPadding)
                        },
                    ),
            )
        }
        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            satelliteDialogVisible = false
                            settingsPanelVisible = false
                            facilitiesPanelVisible = false
                        },
                    )
                    .semantics { contentDescription = "关闭导航面板" },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !isLandscape &&
                (state.highwayExit.isNotBlank() || visibleNavigationFacilities(state).isNotEmpty()) &&
                !overlayVisible && portraitStatusCardTopPx > 0,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 14.dp,
                    bottom = portraitBottomOverlayPadding,
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
                    facilities = visibleNavigationFacilities(state),
                    onClick = {
                        facilitiesPanelVisible = true
                    },
                )
            }
        }
        if (!isLandscape && !overlayVisible) {
            NavigationStatusCard(
                state = state,
                nightMode = nightModeEnabled,
                mapInteracting = mapInteracting,
                onRecoverFollowing = {
                    mapInteracting = false
                    controller?.recoverFollowing()
                },
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned {
                        portraitStatusCardTopPx = it.boundsInParent().top.roundToInt()
                    },
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
                voiceGuidanceLevel = voiceGuidanceLevel,
                quietHoursEnabled = quietHoursEnabled,
                importantAlertsEnabled = importantAlertsEnabled,
                quietHoursStartMinutes = settings.quietHoursStartMinutes,
                quietHoursEndMinutes = settings.quietHoursEndMinutes,
                trafficLayerEnabled = trafficLayerEnabled,
                routeAlertsEnabled = routeAlertsEnabled,
                trafficBarEnabled = trafficBarEnabled,
                eagleMapEnabled = eagleMapEnabled,
                autoZoomEnabled = autoZoomEnabled,
                perspectiveMode = perspectiveMode,
                themeMode = themeMode,
                orientationMode = settings.orientationMode,
                nightMode = nightModeEnabled,
                isLandscape = isLandscape,
                alternativeRoutes = state.alternativeRoutes,
                onVoiceGuidanceChange = { enabled ->
                    val level = if (enabled) VoiceGuidanceLevel.Detailed else VoiceGuidanceLevel.Muted
                    voiceGuidanceLevel = level
                    persistCurrentSettings(level)
                },
                onVoiceGuidanceLevelChange = { level ->
                    voiceGuidanceLevel = level
                    persistCurrentSettings(level)
                },
                onQuietHoursChange = { enabled ->
                    quietHoursEnabled = enabled
                    persistCurrentSettings()
                },
                onImportantAlertsChange = { enabled ->
                    importantAlertsEnabled = enabled
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
                onPerspectiveModeChange = { mode ->
                    perspectiveMode = mode
                    controller?.setPerspectiveMode(mode)
                    persistCurrentSettings(selectedPerspectiveMode = mode)
                },
                onThemeModeChange = { mode ->
                    themeMode = mode
                    persistCurrentSettings(selectedThemeMode = mode)
                },
                onOrientationModeChange = { mode ->
                    persistCurrentSettings(selectedOrientationMode = mode)
                },
                onOverview = { controller?.overview() },
                onAlternativeRouteSelected = { controller?.selectAlternativeRoute(it) },
                onDismiss = { settingsPanelVisible = false },
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(end = 14.dp, top = 10.dp, bottom = 10.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxHeight(0.94f)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.76f)
                },
            )
        }
        if (facilitiesPanelVisible) {
            NavigationFacilitiesPanel(
                facilities = visibleNavigationFacilities(state),
                nightMode = nightModeEnabled,
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

internal fun selectNavigationSafetyNotice(
    state: NavigationUiState,
    routeNotice: NavigationRouteNotice?,
): NavigationRouteNotice? {
    if (routeNotice?.important == true) return routeNotice
    return routeNotice
}

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

@Composable
private fun NavigationCurrentRoad(
    road: String,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 360.dp),
        color = if (nightMode) Color(0xF2181818) else Color(0xF7FFFFFF),
        shape = RoundedCornerShape(50),
        shadowElevation = 10.dp,
    ) {
        Text(
            text = road.ifBlank { "正在定位当前道路" },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (nightMode) Color.White else Color(0xFF17211F),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NavigationTrafficMapNotice(
    alert: NavigationTrafficAlert,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when (alert.level) {
        NavigationTrafficLevel.Slow -> Color(0xFFF2B134)
        NavigationTrafficLevel.Congested -> Color(0xFFF07B32)
        NavigationTrafficLevel.SeverelyCongested -> Color(0xFFD83A3A)
        NavigationTrafficLevel.Smooth -> Color(0xFF24A866)
        NavigationTrafficLevel.Unknown -> Color(0xFF64748B)
    }
    val label = when (alert.level) {
        NavigationTrafficLevel.Slow -> "前方缓行"
        NavigationTrafficLevel.Congested -> "前方拥堵"
        NavigationTrafficLevel.SeverelyCongested -> "前方严重拥堵"
        NavigationTrafficLevel.Smooth -> "前方畅通"
        NavigationTrafficLevel.Unknown -> "前方路况变化"
    }
    Surface(
        modifier = modifier
            .widthIn(max = 220.dp)
            .semantics { contentDescription = "$label，${formatNavigationDistance(alert.distanceMeters)}后" },
        color = if (nightMode) Color(0xF21A1F27) else Color(0xF7FFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(54.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier.padding(start = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = if (nightMode) Color.White else Color(0xFF172033),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " · ${formatNavigationDistance(alert.distanceMeters)}后",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "影响约 ${formatNavigationDistance(alert.affectedLengthMeters)}",
                    color = if (nightMode) Color(0xFFB8C2D1) else Color(0xFF5F6B7A),
                    fontSize = 10.sp,
                )
            }
        }
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
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun NavigationSettingsPanel(
    voiceGuidanceEnabled: Boolean,
    voiceGuidanceLevel: VoiceGuidanceLevel,
    quietHoursEnabled: Boolean,
    importantAlertsEnabled: Boolean,
    quietHoursStartMinutes: Int,
    quietHoursEndMinutes: Int,
    trafficLayerEnabled: Boolean,
    routeAlertsEnabled: Boolean,
    trafficBarEnabled: Boolean,
    eagleMapEnabled: Boolean,
    autoZoomEnabled: Boolean,
    perspectiveMode: NavigationPerspectiveMode,
    themeMode: NavigationThemeMode,
    orientationMode: AppOrientationMode,
    nightMode: Boolean,
    isLandscape: Boolean,
    alternativeRoutes: List<NavigationAlternativeRoute>,
    onVoiceGuidanceChange: (Boolean) -> Unit,
    onVoiceGuidanceLevelChange: (VoiceGuidanceLevel) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onImportantAlertsChange: (Boolean) -> Unit,
    onTrafficLayerChange: (Boolean) -> Unit,
    onRouteAlertsChange: (Boolean) -> Unit,
    onTrafficBarChange: (Boolean) -> Unit,
    onEagleMapChange: (Boolean) -> Unit,
    onAutoZoomChange: (Boolean) -> Unit,
    onPerspectiveModeChange: (NavigationPerspectiveMode) -> Unit,
    onThemeModeChange: (NavigationThemeMode) -> Unit,
    onOrientationModeChange: (AppOrientationMode) -> Unit,
    onOverview: () -> Unit,
    onAlternativeRouteSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = if (isLandscape) "横屏导航设置面板" else "竖屏导航设置面板"
        },
        color = if (nightMode) NavigationPanelColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
        shape = if (isLandscape) {
            MaterialTheme.shapes.extraLarge
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        shadowElevation = 20.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                if (nightMode) NavigationPanelDivider else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = if (isLandscape) 16.dp else 8.dp, end = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "导航设置",
                        color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    )
                    Text(
                        if (isLandscape) "车机侧边面板" else "设置会应用到后续行程",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("完成") }
            }
            androidx.compose.material3.HorizontalDivider(
                color = if (nightMode) NavigationPanelDivider else MaterialTheme.colorScheme.outlineVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NavigationSettingsSection("常用控制", "高频操作优先显示", nightMode) {
                    NavigationSettingToggle("语音播报", voiceGuidanceEnabled, nightMode, { onVoiceGuidanceChange(!voiceGuidanceEnabled) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceGuidanceLevel.entries.forEach { level ->
                            NavigationChoiceChip(
                                label = level.label,
                                visualLabel = level.label.removeSuffix("播报"),
                                selected = voiceGuidanceLevel == level,
                                nightMode = nightMode,
                                onClick = { onVoiceGuidanceLevelChange(level) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    NavigationSettingToggle("实时路况", trafficLayerEnabled, nightMode, { onTrafficLayerChange(!trafficLayerEnabled) })
                    NavigationSettingToggle("自动缩放", autoZoomEnabled, nightMode, { onAutoZoomChange(!autoZoomEnabled) })
                    NavigationSettingToggle("偏航与拥堵提醒", routeAlertsEnabled, nightMode, { onRouteAlertsChange(!routeAlertsEnabled) })
                }
                NavigationSettingsSection("语音与提醒", "保留所有播报偏好", nightMode) {
                    NavigationSettingToggle(
                        "静音时段 ${formatMinutesOfDay(quietHoursStartMinutes)}-${formatMinutesOfDay(quietHoursEndMinutes)}",
                        quietHoursEnabled,
                        nightMode,
                        { onQuietHoursChange(!quietHoursEnabled) },
                    )
                    NavigationSettingToggle(
                        "重要提示语音",
                        importantAlertsEnabled,
                        nightMode,
                        { onImportantAlertsChange(!importantAlertsEnabled) },
                    )
                }
                NavigationSettingsSection("地图显示", "主题与辅助图层", nightMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavigationPerspectiveMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = "导航视角 ${mode.label}",
                                visualLabel = mode.label,
                                selected = perspectiveMode == mode,
                                nightMode = nightMode,
                                onClick = { onPerspectiveModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    NavigationSettingToggle("路况柱", trafficBarEnabled, nightMode, { onTrafficBarChange(!trafficBarEnabled) })
                    NavigationSettingToggle("鹰眼总览", eagleMapEnabled, nightMode, { onEagleMapChange(!eagleMapEnabled) })
                    Text(
                        "地图主题",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavigationThemeMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = mode.label,
                                visualLabel = mode.label.removePrefix("始终"),
                                selected = themeMode == mode,
                                nightMode = nightMode,
                                onClick = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                NavigationSettingsSection("路线与布局", null, nightMode) {
                    NavigationSettingCommand("路线总览", "查看完整路线与剩余路段", nightMode) {
                        onOverview()
                        onDismiss()
                    }
                    if (alternativeRoutes.size > 1) {
                        alternativeRoutes.forEach { route ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(
                                        enabled = !route.selected,
                                        role = Role.Button,
                                        onClick = {
                                            onAlternativeRouteSelected(route.pathId)
                                            onDismiss()
                                        },
                                    )
                                    .semantics { contentDescription = "选择备选路线 ${route.label}" },
                                color = if (route.selected) Color(0xFF244E78) else if (nightMode) Color(0xFF25364D) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        if (route.selected) "${route.label} · 当前路线" else route.label,
                                        color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "${formatNavigationTime(route.durationSeconds)} · ${formatNavigationDistance(route.distanceMeters)} · 过路费 ${route.tollCostYuan} 元",
                                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "应用显示方向",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppOrientationMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = mode.label,
                                visualLabel = mode.label,
                                selected = orientationMode == mode,
                                nightMode = nightMode,
                                onClick = { onOrientationModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Text(
                    "语音语言跟随高德内置语音资源与系统地区设置，当前 SDK 未提供运行时语言包切换接口。",
                    color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun NavigationSettingsSection(
    title: String,
    subtitle: String?,
    nightMode: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        color = if (nightMode) Color(0xFF1E3148) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                subtitle?.let {
                    Text(
                        it,
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun NavigationChoiceChip(
    label: String,
    visualLabel: String,
    selected: Boolean,
    nightMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        color = if (selected) Color(0xFF1769E0) else if (nightMode) Color(0xFF25364D) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                visualLabel,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                color = if (selected) Color.White else if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NavigationSettingCommand(
    label: String,
    description: String,
    nightMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label 导航设置" },
        color = if (nightMode) Color(0xFF25364D) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
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
                state = state,
                destinationName = destinationName,
                endPadding = if (reserveGpsSpace) 52.dp else 16.dp,
                compact = compactGuidance || compactInstruction,
            )
            NavigationRouteNoticeBanner(routeNotice)
            if (state.lanes.isNotEmpty() && junctionViewBitmap == null) {
                NavigationPortraitLaneGuidance(lanes = state.lanes)
            }
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
private fun NavigationPortraitInstructionContent(
    state: NavigationUiState,
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
        state.maneuverIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导航转向指示 ${state.maneuverIconType}",
                modifier = Modifier.size(iconSize),
            )
        } ?: ManeuverIcon(
            iconType = state.maneuverIconType,
            modifier = Modifier.size(iconSize),
            backgroundColor = Color.Transparent,
            arrowColor = Color.White,
        )
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.maneuverDistanceMeters > 0) {
                Text(
                    text = formatNavigationDistance(state.maneuverDistanceMeters),
                    color = Color.White,
                    fontSize = if (compact) 27.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = state.nextRoad.ifBlank { state.instruction },
                color = Color.White,
                fontSize = if (compact) 17.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.maneuverDistanceMeters <= 0) {
                Text(
                    text = if (state.phase == NavigationPhase.Arrived) "已到达目的地附近" else "前往 $destinationName",
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
private fun NavigationPortraitLaneGuidance(lanes: List<NavigationLane>) {
    androidx.compose.material3.HorizontalDivider(color = NavigationPanelDivider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "竖屏车道引导" },
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
                    color = if (lane.recommended) Color.White else Color(0xFF77869A),
                    fontSize = if (lane.direction.symbol.length > 1) 14.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
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
            .padding(start = 14.dp, top = 6.dp)
            .semantics { contentDescription = "横屏导航信息卡" },
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 16.dp,
    ) {
        Column {
            Column(modifier = Modifier.background(PortraitNavigationPanelColor)) {
                NavigationLandscapeInstructionContent(
                    state = state,
                    destinationName = destinationName,
                    compact = compactGuidance || junctionViewBitmap != null,
                )
                NavigationRouteNoticeBanner(routeNotice)
            }
            if (junctionViewBitmap != null) {
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
            if (junctionViewBitmap == null) {
                if (!mapInteracting) {
                    NavigationLandscapeTripSummary(state)
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (state.phase == NavigationPhase.Arrived) {
                NavigationArrivalActions(
                    onFindParking = onFindParking,
                    onSaveParkingLocation = onSaveParkingLocation,
                    parkingLocationAvailable = parkingLocationAvailable,
                    onExit = onExit,
                )
            }
            if (mapInteracting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PortraitNavigationPanelColor)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction("继续导航", Color(0xFF263F62), Color.White, onRecoverFollowing, Modifier.weight(1f))
                    NavigationAction("设置", Color(0xFF263F62), Color.White, onSettings, Modifier.weight(1f))
                    NavigationAction("结束", Color(0xFF5B3535), Color(0xFFFFD4D0), onExit, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavigationLandscapeInstructionContent(
    state: NavigationUiState,
    destinationName: String,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 52.dp else 70.dp
        state.maneuverIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导航转向指示 ${state.maneuverIconType}",
                modifier = Modifier.size(iconSize),
            )
        } ?: ManeuverIcon(
            iconType = state.maneuverIconType,
            modifier = Modifier.size(iconSize),
            backgroundColor = Color.Transparent,
            arrowColor = Color.White,
        )
        Column(
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (state.maneuverDistanceMeters > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNavigationDistance(state.maneuverDistanceMeters),
                        color = Color.White,
                        fontSize = if (compact) 25.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = " 后",
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = NavigationSecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = state.nextRoad.ifBlank {
                    if (state.phase == NavigationPhase.Arrived) "已到达目的地附近" else destinationName
                },
                color = Color.White,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavigationLandscapeTripSummary(state: NavigationUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFAFFFFFF))
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "横屏行程信息条" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatNavigationDistance(state.remainingDistanceMeters),
            color = Color(0xFF111827),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text("·", color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(
            text = formatNavigationTime(state.remainingTimeSeconds),
            color = Color(0xFF111827),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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
private fun NavigationLaneGuidancePanel(lanes: List<NavigationLane>, modifier: Modifier = Modifier) {
    if (lanes.isEmpty()) return
    Surface(
        modifier = modifier,
        color = Color(0xFF1473F3),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 12.dp,
    ) {
        NavigationLaneGuidance(lanes)
    }
}

@Composable
private fun NavigationLaneGuidance(lanes: List<NavigationLane>) {
    if (lanes.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
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
                        .size(width = 1.dp, height = 42.dp)
                        .background(Color(0x66FFFFFF)),
                )
            }
            Box(
                modifier = Modifier.size(width = 52.dp, height = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lane.direction.symbol,
                    color = if (lane.recommended) Color.White else Color(0xFF0B429B),
                    fontSize = if (lane.direction.symbol.length > 1) 16.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NavigationGpsStatus(
    state: NavigationUiState,
    isLandscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnostic = state.locationDiagnostic
    val gpsMode = determineNavigationGpsMode(
        gpsEnabled = state.gpsEnabled,
        gpsSignalWeak = state.gpsSignalWeak,
        satelliteStatus = state.satelliteStatus,
        locationDiagnostic = diagnostic,
    )
    val isNormal = gpsMode == NavigationGpsMode.Normal && diagnostic == null
    val backgroundColor = if (isLandscape) Color(0xF7FFFFFF) else Color(0xD9141C2B)
    val iconColor = when {
        !isNormal -> Color(0xFFE53935)
        isLandscape -> Color(0xFF182033)
        else -> Color.White
    }
    val statusLabel = when {
        gpsMode == NavigationGpsMode.Unavailable -> "GPS 未开启"
        gpsMode == NavigationGpsMode.Weak -> "GPS 信号弱"
        diagnostic?.issue == NavigationLocationIssue.LowAccuracy -> "GPS 漂移"
        diagnostic?.issue == NavigationLocationIssue.OffRoute -> "待校准"
        else -> "GPS ${state.satelliteStatus.usedInFixCount}"
    }
    Surface(
        modifier = modifier
            .size(width = if (isLandscape) 48.dp else 38.dp, height = 38.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "GPS 卫星状态"
                stateDescription = statusLabel
            },
        color = backgroundColor,
        shape = if (isLandscape) RoundedCornerShape(12.dp) else CircleShape,
        shadowElevation = 6.dp,
    ) {
        Canvas(Modifier.padding(horizontal = if (isLandscape) 13.dp else 8.dp, vertical = 8.dp)) {
            val signalCenter = Offset(size.width * 0.28f, size.height * 0.72f)
            drawCircle(iconColor, radius = size.minDimension * 0.09f, center = signalCenter)
            listOf(0.25f, 0.43f).forEach { radiusFraction ->
                val radius = size.minDimension * radiusFraction
                drawArc(
                    color = iconColor,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(signalCenter.x - radius, signalCenter.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round),
                )
            }
        }
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
    val gpsMode = determineNavigationGpsMode(
        gpsEnabled = state.gpsEnabled,
        gpsSignalWeak = state.gpsSignalWeak,
        satelliteStatus = satellite,
        locationDiagnostic = state.locationDiagnostic,
    )
    Surface(
        modifier = modifier.semantics { contentDescription = "GPS 定位详情面板" },
        color = GpsPanelBackground,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("GPS 定位详情", color = GpsPanelText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${dismissSeconds.coerceAtLeast(0)} 秒后自动关闭",
                        color = GpsPanelAccent,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = GpsPanelAccent) }
            }
            androidx.compose.material3.HorizontalDivider(color = GpsPanelDivider)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (gpsMode != NavigationGpsMode.Normal) {
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                if (gpsMode == NavigationGpsMode.Unavailable) "GPS 定位未开启" else "弱 GPS 模式",
                                color = Color(0xFFB71C1C),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (gpsMode == NavigationGpsMode.Unavailable) {
                                    "请开启系统定位后继续导航"
                                } else {
                                    "定位可能延迟，请沿当前道路行驶并等待信号恢复"
                                },
                                color = Color(0xFFB71C1C),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
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
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(title, color = Color(0xFFB71C1C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(detail, color = Color(0xFFB71C1C), fontSize = 10.sp)
                        }
                    }
                    NavigationSatelliteMetric("当前定位精度", "约 ${diagnostic.accuracyMeters} 米")
                }
                Surface(color = GpsPanelSurface, shape = RoundedCornerShape(8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationSatelliteMetric("可见卫星", "${satellite.visibleCount} 颗")
                        NavigationSatelliteMetric("参与定位", "${satellite.usedInFixCount} 颗")
                        NavigationSatelliteMetric("平均信号", "%.1f dB-Hz".format(satellite.averageCn0DbHz))
                        if (satellite.systems.isEmpty()) {
                            Text("正在等待卫星数据", color = GpsPanelSecondaryText, fontSize = 12.sp)
                        } else {
                            satellite.systems.forEach { (system, count) ->
                                NavigationSatelliteMetric(system, "$count 颗")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationSatelliteMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = GpsPanelSecondaryText, fontSize = 12.sp)
        Text(value, color = GpsPanelText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                color = facility.kind.cardColor,
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
    }
}

@Composable
private fun NavigationLandscapeFacilityBands(
    facilities: List<NavigationRouteFacility>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (facilities.isEmpty()) return
    val visibleFacilities = facilities.sortedBy(NavigationRouteFacility::distanceMeters).take(2)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 14.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看全部沿途设施" },
    ) {
        visibleFacilities.forEachIndexed { index, facility ->
            val shape = when {
            visibleFacilities.size == 1 -> RoundedCornerShape(8.dp)
                index == 0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                else -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .semantics {
                        contentDescription = "横屏沿途信息条 ${facility.kind.label} ${facility.name}"
                    },
                color = when (facility.kind) {
                    NavigationFacilityKind.TollGate -> Color(0xFF1268E8)
                    NavigationFacilityKind.ServiceArea -> Color(0xFF087A55)
                },
                shape = shape,
                shadowElevation = if (index == 0) 8.dp else 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = facility.name,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatNavigationDistance(facility.distanceMeters),
                        modifier = Modifier.padding(start = 10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationFacilitiesPanel(
    facilities: List<NavigationRouteFacility>,
    nightMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = if (nightMode) NavigationPanelColor else Color(0xFFF6F8FB)
    val titleColor = if (nightMode) Color.White else Color(0xFF172033)
    val secondaryColor = if (nightMode) NavigationSecondaryText else Color(0xFF647184)
    val dividerColor = if (nightMode) NavigationPanelDivider else Color(0xFFD9E1EC)
    Surface(
        modifier = modifier.semantics { contentDescription = "全路线沿途设施" },
        color = panelColor,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("沿途设施", color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("全路线 ${facilities.size} 处", color = GpsPanelAccent, fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = GpsPanelAccent) }
            }
            androidx.compose.material3.HorizontalDivider(color = dividerColor)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { contentDescription = "沿途设施列表" },
            ) {
                items(facilities, key = { "${it.kind}-${it.name}" }) { facility ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .background(
                                color = facility.kind.cardColor.copy(alpha = if (nightMode) 0.18f else 0.09f),
                                shape = RoundedCornerShape(9.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = facility.kind.cardColor,
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
                            Text(facility.name, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(facility.distanceAndTimeLabel, color = secondaryColor, fontSize = 11.sp)
                        }
                    }
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

private val NavigationFacilityKind.cardColor: Color
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> Color(0xFF2E7D32)
        NavigationFacilityKind.TollGate -> Color(0xFF1565C0)
    }

private const val TOLL_GATE_VISIBLE_DISTANCE_METERS = 10_000

/**
 * Service areas only exist on highways, so they are only surfaced once a highway exit is known.
 * Toll stations are only surfaced once within [TOLL_GATE_VISIBLE_DISTANCE_METERS] to avoid clutter.
 */
private fun visibleNavigationFacilities(state: NavigationUiState): List<NavigationRouteFacility> {
    val onHighway = state.highwayExit.isNotBlank()
    return state.routeFacilities.filter { facility ->
        when (facility.kind) {
            NavigationFacilityKind.ServiceArea -> onHighway
            NavigationFacilityKind.TollGate -> facility.distanceMeters <= TOLL_GATE_VISIBLE_DISTANCE_METERS
        }
    }
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
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(70.dp)
            .semantics { contentDescription = "当前车速 ${state.currentSpeedKmh}" },
    ) {
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).size(58.dp),
            color = if (nightMode) Color(0xF227405F) else Color.White,
            shape = CircleShape,
            shadowElevation = 10.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${state.currentSpeedKmh}",
                    color = if (nightMode) Color.White else Color(0xFF172033),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text("km/h", color = if (nightMode) NavigationSecondaryText else Color(0xFF66758B), fontSize = 9.sp)
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
    backgroundColor: Color = Color(0xFF263650),
    arrowColor: Color = Color(0xFF75B8FF),
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

@Composable
private fun NavigationStatusCard(
    state: NavigationUiState,
    nightMode: Boolean,
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .semantics { contentDescription = "竖屏导航状态卡" },
        color = if (nightMode) NavigationPanelColor else Color(0xFAFFFFFF),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 16.dp,
    ) {
        Column {
            if (!mapInteracting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .semantics { contentDescription = "竖屏底部行程信息" },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = formatNavigationTime(state.remainingTimeSeconds),
                            color = if (nightMode) Color.White else Color(0xFF111827),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = "剩余 ${formatNavigationDistance(state.remainingDistanceMeters)}",
                            color = if (nightMode) NavigationSecondaryText else Color(0xFF5D6878),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
                state.message?.takeIf(String::isNotBlank)?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavigationStatusBadge(
                            text = message,
                            nightMode = nightMode,
                            emphasized = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (mapInteracting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction(
                        "继续导航",
                        if (nightMode) Color(0xFF263F62) else MaterialTheme.colorScheme.primaryContainer,
                        if (nightMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        onRecoverFollowing,
                        Modifier.weight(1f),
                    )
                    NavigationAction(
                        "设置",
                        if (nightMode) Color(0xFF263F62) else MaterialTheme.colorScheme.primaryContainer,
                        if (nightMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        onSettings,
                        Modifier.weight(1f),
                    )
                    NavigationAction(
                        "结束",
                        if (nightMode) Color(0xFF5B3535) else MaterialTheme.colorScheme.errorContainer,
                        if (nightMode) Color(0xFFFFD4D0) else MaterialTheme.colorScheme.onErrorContainer,
                        onExit,
                        Modifier.weight(1f),
                    )
                }
            }
            if (state.phase == NavigationPhase.Arrived) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    NavigationArrivalActions(
                        onFindParking = onFindParking,
                        onSaveParkingLocation = onSaveParkingLocation,
                        parkingLocationAvailable = parkingLocationAvailable,
                        onExit = onExit,
                    )
                }
            } else if (state.phase == NavigationPhase.Failed) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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
}

@Composable
private fun NavigationBottomCommand(
    label: String,
    nightMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (nightMode) Color.White else Color(0xFF111827)
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label 导航" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationActionIcon(label = label, color = foreground, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            modifier = Modifier.padding(start = 7.dp),
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NavigationBottomDivider(nightMode: Boolean) {
    Box(
        Modifier
            .size(width = 1.dp, height = 38.dp)
            .background(if (nightMode) NavigationPanelDivider else Color(0xFFD8DDE5)),
    )
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
    nightMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
            .semantics { contentDescription = "$label 导航设置" },
        color = if (nightMode) Color(0xFF25364D) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { onClick() },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1769E0),
                    checkedBorderColor = Color(0xFF1769E0),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF9AA6B4),
                    uncheckedBorderColor = Color(0xFF9AA6B4),
                ),
            )
        }
    }
}

@Composable
private fun NavigationStatusBadge(
    text: String,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        color = if (emphasized) {
            if (nightMode) Color(0xFF203B5E) else MaterialTheme.colorScheme.primaryContainer
        } else if (nightMode) {
            Color(0xFF25364D)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
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
private fun NavigationAction(
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
private fun NavigationActionIcon(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (label) {
            "退出" -> {
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
