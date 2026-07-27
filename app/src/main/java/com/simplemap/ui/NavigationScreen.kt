package com.simplemap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.simplemap.navigation.AmapNavigationController
import com.simplemap.navigation.AmapNavigationView
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteNotice
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

    fun showRouteOverview() {
        controller?.overview() ?: run {
            mapInteracting = true
            mapInteractionGeneration += 1
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
        val layoutDirection = LocalLayoutDirection.current
        val safeDrawingInsets = WindowInsets.safeDrawing
        val navigationBarInsets = WindowInsets.navigationBars
        val statusBarInsets = WindowInsets.statusBars
        val horizontalSafeAreaLeftPx = safeDrawingInsets.getLeft(density, layoutDirection)
        val horizontalSafeAreaRightPx = safeDrawingInsets.getRight(density, layoutDirection)
        val horizontalSafeAreaLeft = with(density) { horizontalSafeAreaLeftPx.toDp() }
        val horizontalSafeAreaRight = with(density) { horizontalSafeAreaRightPx.toDp() }
        val horizontalSafeAreaAfterNavigationBarLeft = with(density) {
            (
                horizontalSafeAreaLeftPx - navigationBarInsets.getLeft(density, layoutDirection)
            ).coerceAtLeast(0).toDp()
        }
        val horizontalSafeAreaAfterNavigationBarRight = with(density) {
            (
                horizontalSafeAreaRightPx - navigationBarInsets.getRight(density, layoutDirection)
            ).coerceAtLeast(0).toDp()
        }
        val horizontalSafeAreaAfterStatusBarLeft = with(density) {
            (
                horizontalSafeAreaLeftPx - statusBarInsets.getLeft(density, layoutDirection)
            ).coerceAtLeast(0).toDp()
        }
        val horizontalSafeAreaAfterStatusBarRight = with(density) {
            (
                horizontalSafeAreaRightPx - statusBarInsets.getRight(density, layoutDirection)
            ).coerceAtLeast(0).toDp()
        }
        val horizontalSafeAreaAfterSystemBarsLeft = with(density) {
            (
                horizontalSafeAreaLeftPx - maxOf(
                    navigationBarInsets.getLeft(density, layoutDirection),
                    statusBarInsets.getLeft(density, layoutDirection),
                )
            ).coerceAtLeast(0).toDp()
        }
        val horizontalSafeAreaAfterSystemBarsRight = with(density) {
            (
                horizontalSafeAreaRightPx - maxOf(
                    navigationBarInsets.getRight(density, layoutDirection),
                    statusBarInsets.getRight(density, layoutDirection),
                )
            ).coerceAtLeast(0).toDp()
        }
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
        val safeContentWidth = (
            maxWidth - horizontalSafeAreaLeft - horizontalSafeAreaRight
        ).coerceAtLeast(0.dp)
        val landscapeInformationWidth = minOf(safeContentWidth * 0.34f, 360.dp)
        val landscapeMapWidth = (safeContentWidth - landscapeInformationWidth).coerceAtLeast(0.dp)
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
                (safeContentWidth - 28.dp).coerceAtLeast(0.dp) *
                    bitmap.height / bitmap.width.coerceAtLeast(1),
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
                    if (sessionController == null) {
                        navigationController.start(routeRequest, simulated, plan)
                    }
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
                overlaySafeAreaLeftPx = horizontalSafeAreaLeftPx,
                overlaySafeAreaRightPx = horizontalSafeAreaRightPx,
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
                    .padding(start = horizontalSafeAreaLeft)
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
                    onOverview = ::showRouteOverview,
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
                    .padding(
                        start = horizontalSafeAreaAfterStatusBarLeft,
                        end = horizontalSafeAreaAfterStatusBarRight,
                    )
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
                    .padding(
                        top = 14.dp,
                        end = horizontalSafeAreaAfterStatusBarRight + 16.dp,
                    )
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
                    .padding(
                        top = 10.dp,
                        end = horizontalSafeAreaAfterStatusBarRight + landscapeGpsSlotWidth,
                    )
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
                        start = if (isLandscape) {
                            horizontalSafeAreaLeft + landscapeInformationWidth + 20.dp
                        } else {
                            horizontalSafeAreaLeft + 14.dp
                        },
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
                        start = horizontalSafeAreaLeft + landscapeInformationWidth + 16.dp,
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
                    .padding(
                        start = if (overlayVisible) {
                            horizontalSafeAreaAfterNavigationBarLeft
                        } else {
                            horizontalSafeAreaLeft
                        },
                        end = if (overlayVisible) {
                            horizontalSafeAreaAfterNavigationBarRight
                        } else {
                            horizontalSafeAreaRight
                        },
                    )
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
                    start = horizontalSafeAreaLeft + 14.dp,
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
                onOverview = ::showRouteOverview,
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
                    .padding(
                        start = horizontalSafeAreaAfterNavigationBarLeft,
                        end = horizontalSafeAreaAfterNavigationBarRight,
                    )
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
                        .padding(
                            start = horizontalSafeAreaAfterSystemBarsLeft + 14.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        )
                        .width(landscapeInformationWidth)
                        .heightIn(max = maxHeight * 0.9f)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = horizontalSafeAreaAfterNavigationBarLeft,
                            end = horizontalSafeAreaAfterNavigationBarRight,
                        )
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
                            showRouteOverview()
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
                        .padding(
                            end = horizontalSafeAreaAfterSystemBarsRight + 14.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        )
                        .widthIn(max = 360.dp)
                        .fillMaxHeight(0.94f)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = horizontalSafeAreaAfterNavigationBarLeft + 8.dp,
                            end = horizontalSafeAreaAfterNavigationBarRight + 8.dp,
                        )
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
                        .padding(
                            start = horizontalSafeAreaAfterSystemBarsLeft + 14.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        )
                        .width(landscapeInformationWidth)
                        .fillMaxHeight()
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = horizontalSafeAreaAfterNavigationBarLeft,
                            end = horizontalSafeAreaAfterNavigationBarRight,
                        )
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
