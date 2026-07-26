package com.simplemap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.navigation.NavigationTrafficLevel
import com.simplemap.navigation.NavigationUiState
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.settings.currentMinuteOfDay
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.settings.withVoiceGuidanceLevel
import com.simplemap.ui.theme.SimpleMapBlue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MAP_FOLLOW_RECOVERY_DELAY_MILLIS = 10_000L

private enum class NavigationOverlay {
    SatelliteStatus,
    Settings,
    Facilities,
}

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
    val lifecycleReporter = remember(routeRequest, simulated) { NavigationLifecycleReporter() }
    val currentOnNavigationStarted by rememberUpdatedState(onNavigationStarted)
    val currentOnNavigationFinished by rememberUpdatedState(onNavigationFinished)
    var exitConfirmationVisible by remember { mutableStateOf(false) }
    var mapInteracting by remember(previewMapInteracting) { mutableStateOf(previewMapInteracting) }
    var mapInteractionGeneration by remember { mutableIntStateOf(if (previewMapInteracting) 1 else 0) }
    var activeOverlay by remember { mutableStateOf<NavigationOverlay?>(null) }
    var sessionSettings by remember(settings) { mutableStateOf(settings) }
    var satelliteDismissSeconds by remember { mutableIntStateOf(5) }
    var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
    var visibleRouteNotice by remember { mutableStateOf<NavigationRouteNotice?>(null) }
    val voiceGuidanceLevel = sessionSettings.resolvedVoiceGuidanceLevel
    val systemInDarkTheme = isSystemInDarkTheme()
    val nightModeEnabled = shouldUseNightTheme(
        mode = sessionSettings.themeMode,
        systemInDarkTheme = systemInDarkTheme,
        minuteOfDay = minuteOfDay,
        inTunnel = state.inTunnel,
    )

    LaunchedEffect(lifecycleReporter, state.phase) {
        if (state.phase == NavigationPhase.Arrived || state.phase == NavigationPhase.Failed) {
            lifecycleReporter.reportFinished(
                phase = state.phase,
                finalState = state,
                onNavigationFinished = currentOnNavigationFinished,
            )
        }
    }

    LaunchedEffect(activeOverlay) {
        if (activeOverlay != NavigationOverlay.SatelliteStatus) return@LaunchedEffect
        satelliteDismissSeconds = 5
        while (satelliteDismissSeconds > 0) {
            delay(1_000L)
            satelliteDismissSeconds -= 1
        }
        activeOverlay = null
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

    LaunchedEffect(sessionSettings.themeMode) {
        if (sessionSettings.themeMode != NavigationThemeMode.Automatic) return@LaunchedEffect
        while (true) {
            minuteOfDay = currentMinuteOfDay()
            delay(60_000L)
        }
    }

    LaunchedEffect(controller, nightModeEnabled) {
        controller?.setNightMode(nightModeEnabled)
    }

    fun persistCurrentSettings(
        updatedSettings: NavigationSettings,
    ) {
        val selectedMinuteOfDay = if (updatedSettings.themeMode == NavigationThemeMode.Automatic) {
            currentMinuteOfDay()
        } else {
            minuteOfDay
        }
        val selectedNightMode = shouldUseNightTheme(
            mode = updatedSettings.themeMode,
            systemInDarkTheme = systemInDarkTheme,
            minuteOfDay = selectedMinuteOfDay,
            inTunnel = state.inTunnel,
        )
        onSettingsChanged(
            updatedSettings.copy(
                nightMode = selectedNightMode,
            ).withVoiceGuidanceLevel(updatedSettings.resolvedVoiceGuidanceLevel),
        )
    }

    fun updateSessionSettings(
        updatedSettings: NavigationSettings,
        applyToController: (AmapNavigationController) -> Unit = {},
    ) {
        sessionSettings = updatedSettings
        controller?.let(applyToController)
        persistCurrentSettings(updatedSettings)
    }

    fun exitNavigation() {
        exitConfirmationVisible = false
        lifecycleReporter.reportFinished(
            phase = state.phase,
            finalState = state,
            onNavigationFinished = currentOnNavigationFinished,
        )
        controller?.stop()
        onExit()
    }

    fun requestExit() {
        if (state.phase == NavigationPhase.Arrived || state.phase == NavigationPhase.Failed) {
            exitNavigation()
        } else {
            exitConfirmationVisible = true
        }
    }

    BackHandler {
        if (activeOverlay != null) {
            activeOverlay = null
            return@BackHandler
        }
        requestExit()
    }
    DisposableEffect(controller, lifecycleReporter) {
        val navigationController = controller
        val stateToken = navigationController?.addStateListener { state = it }
        val startedToken = navigationController?.addNavigationStartedListener {
            lifecycleReporter.reportStarted(currentOnNavigationStarted)
        }
        val interactionToken = navigationController?.addMapInteractionListener { interacting ->
            mapInteracting = interacting
            if (interacting) mapInteractionGeneration += 1
        }
        onDispose {
            if (stateToken != null) navigationController.removeStateListener(stateToken)
            if (startedToken != null) navigationController.removeNavigationStartedListener(startedToken)
            if (interactionToken != null) navigationController.removeMapInteractionListener(interactionToken)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val guidanceState = remember(
            state.phase,
            state.instruction,
            state.nextRoad,
            state.maneuverIconType,
            state.maneuverDistanceMeters,
        ) {
            state.toGuidanceState()
        }
        val tripSummaryState = remember(
            state.remainingDistanceMeters,
            state.remainingTimeSeconds,
            state.remainingTrafficLights,
        ) {
            state.toTripSummaryState()
        }
        var portraitGuidanceBottomPx by remember { mutableIntStateOf(0) }
        var portraitSpeedClusterBottomPx by remember { mutableIntStateOf(0) }
        var portraitStatusCardTopPx by remember { mutableIntStateOf(0) }
        var landscapeGpsStatusBottomPx by remember { mutableIntStateOf(0) }
        var landscapeLaneGuidanceBottomPx by remember { mutableIntStateOf(0) }
        val safetyNotice = selectNavigationSafetyNotice(state, visibleRouteNotice)
        val highwayFacilities = remember(
            state.highwayExit,
            state.currentRoad,
            state.nextRoad,
            state.routeFacilities,
        ) {
            highwayNavigationFacilities(state)
        }
        val displayedFacilities = remember(highwayFacilities) {
            visibleNavigationFacilities(highwayFacilities)
        }
        val landscapeInformationWidth = minOf(maxWidth * 0.34f, 360.dp)
        val landscapeMapWidth = (maxWidth - landscapeInformationWidth).coerceAtLeast(0.dp)
        val landscapeSpeedSlotWidth = 96.dp
        val landscapeGpsSlotWidth = 68.dp
        val landscapeLaneAvailableWidth = (
            landscapeMapWidth - landscapeSpeedSlotWidth - landscapeGpsSlotWidth
        ).coerceAtLeast(0.dp)
        val landscapeLaneWidth = minOf(
            (state.lanes.size * 40 + 12).dp,
            landscapeLaneAvailableWidth,
        )
        val landscapeLaneHeight = (maxHeight * 0.15f).coerceIn(42.dp, 56.dp)
        val landscapeJunctionHeight = state.junctionViewBitmap?.let { bitmap ->
            val facilityBandsHeight = when (displayedFacilities.size) {
                0 -> 0.dp
                1 -> 54.dp
                else -> 103.dp
            }
            val fixedContentHeight = 6.dp + 78.dp + 58.dp + facilityBandsHeight +
                if (safetyNotice != null) 58.dp else 0.dp
            minOf(
                (landscapeInformationWidth - 14.dp) * bitmap.height / bitmap.width.coerceAtLeast(1),
                (maxHeight - fixedContentHeight).coerceAtLeast(0.dp),
            )
        } ?: 0.dp
        val portraitJunctionHeight = state.junctionViewBitmap?.let { bitmap ->
            minOf(
                (maxWidth - 28.dp) * bitmap.height / bitmap.width.coerceAtLeast(1),
                maxHeight * 0.25f,
            )
        } ?: 0.dp
        val compactGuidance = if (isLandscape) maxHeight < 360.dp else maxHeight < 600.dp
        val overlayVisible = activeOverlay != null
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
                    navigationController.start(routeRequest, simulated, plan)
                },
                settings = sessionSettings.withVoiceGuidanceLevel(voiceGuidanceLevel),
                trafficLayer = sessionSettings.trafficLayer,
                routeAlerts = sessionSettings.routeAlerts,
                trafficBar = sessionSettings.trafficBar,
                eagleMap = sessionSettings.eagleMap,
                autoZoom = sessionSettings.autoZoom,
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
                    guidanceState = guidanceState,
                    maneuverIconBitmap = state.maneuverIconBitmap,
                    lanes = state.lanes,
                    tripSummaryState = tripSummaryState,
                    message = state.message,
                    routeNotice = safetyNotice,
                    compactGuidance = compactGuidance,
                    destinationName = destination.name,
                    junctionViewBitmap = state.junctionViewBitmap,
                    junctionViewHeight = landscapeJunctionHeight,
                    mapInteracting = mapInteracting,
                    actionsEnabled = !overlayVisible,
                    onRecoverFollowing = {
                        mapInteracting = false
                        controller?.recoverFollowing()
                    },
                    onSettings = {
                        activeOverlay = NavigationOverlay.Settings
                    },
                    onExit = ::requestExit,
                    onFindParking = onFindParking,
                    onSaveParkingLocation = {
                        val latitude = state.latitude
                        val longitude = state.longitude
                        if (latitude != null && longitude != null) onSaveParkingLocation(latitude, longitude)
                    },
                    parkingLocationAvailable = state.latitude != null && state.longitude != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!overlayVisible && displayedFacilities.isNotEmpty()) {
                    NavigationFacilityBands(
                        facilities = displayedFacilities,
                        onClick = { activeOverlay = NavigationOverlay.Facilities },
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                    )
                }
            }
        } else {
            NavigationInstructionCard(
                guidanceState = guidanceState,
                maneuverIconBitmap = state.maneuverIconBitmap,
                lanes = state.lanes,
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
        if (activeOverlay == null) {
            NavigationGpsStatus(
                gpsEnabled = state.gpsEnabled,
                gpsSignalWeak = state.gpsSignalWeak,
                satelliteStatus = state.satelliteStatus,
                locationDiagnostic = state.locationDiagnostic,
                isLandscape = isLandscape,
                onClick = {
                    satelliteDismissSeconds = 5
                    activeOverlay = NavigationOverlay.SatelliteStatus
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
        if ((isLandscape || portraitGuidanceBottomPx > 0) &&
            state.phase == NavigationPhase.Navigating &&
            !overlayVisible
        ) {
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
                NavigationSpeedBubble(
                    currentSpeedKmh = state.currentSpeedKmh,
                    speedLimitKmh = state.speedLimitKmh,
                    nightMode = nightModeEnabled,
                )
                state.intervalAverageSpeedKmh?.let { averageSpeed ->
                    NavigationIntervalSpeedCard(
                        averageSpeedKmh = averageSpeed,
                        remainingMeters = state.intervalRemainingMeters,
                        recommendedSpeedKmh = state.intervalRecommendedSpeedKmh,
                        nightMode = nightModeEnabled,
                    )
                }
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
                    compact = true,
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
                        onClick = { activeOverlay = null },
                    )
                    .semantics { contentDescription = "关闭导航面板" },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !isLandscape &&
                (state.highwayExit.isNotBlank() || displayedFacilities.isNotEmpty()) &&
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
                NavigationFacilityBands(
                    facilities = displayedFacilities,
                    onClick = { activeOverlay = NavigationOverlay.Facilities },
                )
            }
        }
        if (!isLandscape && !overlayVisible) {
            NavigationStatusCard(
                phase = guidanceState.phase,
                tripSummaryState = tripSummaryState,
                message = state.message,
                nightMode = nightModeEnabled,
                mapInteracting = mapInteracting,
                onRecoverFollowing = {
                    mapInteracting = false
                    controller?.recoverFollowing()
                },
                onSettings = {
                    activeOverlay = NavigationOverlay.Settings
                },
                onExit = ::requestExit,
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
        if (activeOverlay == NavigationOverlay.SatelliteStatus) {
            NavigationSatellitePanel(
                gpsEnabled = state.gpsEnabled,
                gpsSignalWeak = state.gpsSignalWeak,
                satelliteStatus = state.satelliteStatus,
                locationDiagnostic = state.locationDiagnostic,
                dismissSeconds = satelliteDismissSeconds,
                nightMode = nightModeEnabled,
                onDismiss = { activeOverlay = null },
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
        if (activeOverlay == NavigationOverlay.Settings) {
            NavigationSettingsPanel(
                state = NavigationSettingsPanelState(
                    voiceGuidanceLevel = voiceGuidanceLevel,
                    quietHoursEnabled = sessionSettings.quietHoursEnabled,
                    quietHoursStartMinutes = sessionSettings.quietHoursStartMinutes,
                    quietHoursEndMinutes = sessionSettings.quietHoursEndMinutes,
                    trafficLayerEnabled = sessionSettings.trafficLayer,
                    routeAlertsEnabled = sessionSettings.routeAlerts,
                    trafficBarEnabled = sessionSettings.trafficBar,
                    eagleMapEnabled = sessionSettings.eagleMap,
                    autoZoomEnabled = sessionSettings.autoZoom,
                    perspectiveMode = sessionSettings.perspectiveMode,
                    themeMode = sessionSettings.themeMode,
                    orientationMode = sessionSettings.orientationMode,
                    nightMode = nightModeEnabled,
                    isLandscape = isLandscape,
                    alternativeRoutes = state.alternativeRoutes,
                ),
                onEvent = { event ->
                    when (event) {
                        is NavigationSettingsEvent.VoiceGuidanceChanged -> {
                            val level = if (event.enabled) {
                                VoiceGuidanceLevel.Detailed
                            } else {
                                VoiceGuidanceLevel.Muted
                            }
                            updateSessionSettings(sessionSettings.withVoiceGuidanceLevel(level))
                        }
                        is NavigationSettingsEvent.VoiceGuidanceLevelChanged -> {
                            updateSessionSettings(sessionSettings.withVoiceGuidanceLevel(event.level))
                        }
                        is NavigationSettingsEvent.QuietHoursChanged -> {
                            updateSessionSettings(sessionSettings.copy(quietHoursEnabled = event.enabled))
                        }
                        is NavigationSettingsEvent.TrafficLayerChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(trafficLayer = event.enabled),
                            ) { it.setTrafficLayer(event.enabled) }
                        }
                        is NavigationSettingsEvent.RouteAlertsChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(routeAlerts = event.enabled),
                            ) { it.setRouteAlerts(event.enabled) }
                        }
                        is NavigationSettingsEvent.TrafficBarChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(trafficBar = event.enabled),
                            ) { it.setTrafficBar(event.enabled) }
                        }
                        is NavigationSettingsEvent.EagleMapChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(eagleMap = event.enabled),
                            ) { it.setEagleMap(event.enabled) }
                        }
                        is NavigationSettingsEvent.AutoZoomChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(autoZoom = event.enabled),
                            ) { it.setAutoZoom(event.enabled) }
                        }
                        is NavigationSettingsEvent.PerspectiveModeChanged -> {
                            updateSessionSettings(
                                sessionSettings.copy(perspectiveMode = event.mode),
                            ) { it.setPerspectiveMode(event.mode) }
                        }
                        is NavigationSettingsEvent.ThemeModeChanged -> {
                            updateSessionSettings(sessionSettings.copy(themeMode = event.mode))
                        }
                        is NavigationSettingsEvent.OrientationModeChanged -> {
                            updateSessionSettings(sessionSettings.copy(orientationMode = event.mode))
                        }
                        is NavigationSettingsEvent.AlternativeRouteSelected -> {
                            controller?.selectAlternativeRoute(event.pathId)
                            activeOverlay = null
                        }
                        NavigationSettingsEvent.OverviewRequested -> {
                            controller?.overview()
                            activeOverlay = null
                        }
                        NavigationSettingsEvent.Dismissed -> activeOverlay = null
                    }
                },
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
        if (activeOverlay == NavigationOverlay.Facilities) {
            NavigationFacilitiesPanel(
                facilities = highwayFacilities,
                nightMode = nightModeEnabled,
                onDismiss = { activeOverlay = null },
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
    if (exitConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { exitConfirmationVisible = false },
            title = { Text("结束导航？") },
            text = { Text("当前路线仍在导航中，结束后将返回路线规划。") },
            confirmButton = {
                Button(
                    onClick = ::exitNavigation,
                    modifier = Modifier.semantics { contentDescription = "确认结束导航" },
                ) {
                    Text("结束导航")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { exitConfirmationVisible = false },
                    modifier = Modifier.semantics { contentDescription = "取消结束导航" },
                ) {
                    Text("继续导航")
                }
            },
        )
    }
}

internal fun selectNavigationSafetyNotice(
    state: NavigationUiState,
    routeNotice: NavigationRouteNotice?,
): NavigationRouteNotice? {
    if (routeNotice?.important == true) return routeNotice
    return routeNotice
}

@Composable
private fun NavigationCurrentRoad(
    road: String,
    nightMode: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = if (compact) 260.dp else 360.dp),
        color = if (nightMode) Color(0xF2181818) else DayPanelSurface,
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
private fun NavigationPortraitInstructionContent(
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
private fun NavigationPortraitLaneGuidance(lanes: List<NavigationLane>) {
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
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    lanes: List<NavigationLane>,
    tripSummaryState: NavigationTripSummaryState,
    message: String?,
    routeNotice: NavigationRouteNotice?,
    compactGuidance: Boolean,
    destinationName: String,
    junctionViewBitmap: android.graphics.Bitmap?,
    junctionViewHeight: androidx.compose.ui.unit.Dp,
    mapInteracting: Boolean,
    actionsEnabled: Boolean,
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
                    guidanceState = guidanceState,
                    maneuverIconBitmap = maneuverIconBitmap,
                    destinationName = destinationName,
                    compact = compactGuidance || junctionViewBitmap != null,
                )
                NavigationRouteNoticeBanner(routeNotice)
            }
            if (junctionViewBitmap != null) {
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    lanes = lanes,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
            if (junctionViewBitmap == null) {
                if (!mapInteracting) {
                    NavigationLandscapeTripSummary(tripSummaryState)
                }
                message?.let { statusMessage ->
                    Text(
                        text = statusMessage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (actionsEnabled) {
                when (guidanceState.phase) {
                    NavigationPhase.Arrived -> NavigationArrivalActions(
                        onFindParking = onFindParking,
                        onSaveParkingLocation = onSaveParkingLocation,
                        parkingLocationAvailable = parkingLocationAvailable,
                        onExit = onExit,
                    )
                    NavigationPhase.Failed -> Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Button(
                            onClick = onExit,
                            modifier = Modifier.fillMaxWidth(),
                            shape = PanelShapeSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = NightErrorContainer),
                        ) {
                            Text("返回路线规划")
                        }
                    }
                    else -> if (mapInteracting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PortraitNavigationPanelColor)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NavigationAction("继续导航", NightActionContainer, Color.White, onRecoverFollowing, Modifier.weight(1f))
                            NavigationAction("设置", NightActionContainer, Color.White, onSettings, Modifier.weight(1f))
                            NavigationAction("结束", NightErrorContainer, NightOnErrorContainer, onExit, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationLandscapeInstructionContent(
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    destinationName: String,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 52.dp else 70.dp
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
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (guidanceState.maneuverDistanceMeters > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNavigationDistance(guidanceState.maneuverDistanceMeters),
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
                text = guidanceState.nextRoad.ifBlank {
                    if (guidanceState.phase == NavigationPhase.Arrived) {
                        "已到达目的地附近"
                    } else {
                        destinationName
                    }
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
private fun NavigationLandscapeTripSummary(summaryState: NavigationTripSummaryState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PortraitNavigationPanelColor)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "横屏行程信息条" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationTripMetric(
            "剩余",
            formatNavigationTime(summaryState.remainingTimeSeconds),
            true,
            true,
            Modifier.weight(1f),
        )
        NavigationTripMetric(
            "距离",
            formatNavigationDistance(summaryState.remainingDistanceMeters),
            true,
            true,
            Modifier.weight(1f),
        )
        NavigationTripMetric(
            "到达",
            formatNavigationArrivalTime(summaryState.remainingTimeSeconds),
            true,
            true,
            Modifier.weight(1f),
        )
        if (summaryState.remainingTrafficLights > 0) {
            NavigationTripMetric(
                "红绿灯",
                "${summaryState.remainingTrafficLights} 个",
                true,
                true,
                Modifier.weight(1f),
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
        val accent = if (currentNotice.important) NightWarningText else NavigationAccentText
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (currentNotice.important) Color(0xFF56343B) else NightInfoContainer)
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
        shape = PanelShapeSmall,
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
                        .background(Color(0x66FFFFFF)),
                )
            }
            Box(
                modifier = Modifier.size(width = 40.dp, height = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lane.direction.symbol,
                    color = if (lane.recommended) Color.White else Color(0xFF0B429B),
                    fontSize = if (lane.direction.symbol.length > 1) 12.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
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
    phase: NavigationPhase,
    tripSummaryState: NavigationTripSummaryState,
    message: String?,
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
            if (mapInteracting && phase != NavigationPhase.Arrived && phase != NavigationPhase.Failed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationAction(
                        "继续导航",
                        if (nightMode) NightActionContainer else MaterialTheme.colorScheme.primaryContainer,
                        if (nightMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        onRecoverFollowing,
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
private fun NavigationTripMetric(
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
            color = if (nightMode) Color.White else Color(0xFF111827),
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
private fun NavigationStatusBadge(
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
