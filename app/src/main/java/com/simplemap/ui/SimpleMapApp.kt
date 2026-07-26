package com.simplemap.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.simplemap.amap.AmapMapView
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationSessionService
import com.simplemap.navigation.NavigationSessionCoordinator
import com.simplemap.navigation.NavigationSessionSpec
import com.simplemap.amap.AmapMapController
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.calculateMapScale
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.permission.locationPermissionAccess
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.route.RouteRequest
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.currentMinuteOfDay
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState
import com.simplemap.trips.ParkingLocationStore
import com.simplemap.trips.TripHistoryStore
import com.simplemap.update.AppUpdateRepository
import com.simplemap.trips.createTripRecord
import com.simplemap.ui.theme.SimpleMapTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocalDataClearResult(
    val fullyCleared: Boolean,
    val favoritePlaceIds: Set<String>,
    val parkingLocation: Place?,
)

internal fun canShowNavigation(simulated: Boolean, sessionReady: Boolean): Boolean = simulated || sessionReady

internal fun shouldFinishLiveNavigationSession(simulated: Boolean): Boolean = !simulated

@Composable
fun SimpleMapRoot(
    controller: MapAccessController,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    navigationSettingsStore: NavigationSettingsStore? = null,
    initialNavigationSettings: NavigationSettings? = null,
    onThemeModeChanged: (NavigationThemeMode) -> Unit = {},
    onOrientationModeChanged: (AppOrientationMode) -> Unit = {},
    onNavigationVisibilityChanged: (Boolean) -> Unit = {},
) {
    var state: MapAccessState by remember { mutableStateOf(MapAccessState.Loading) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        state = withContext(Dispatchers.IO) { controller.load() }
    }

    when (val currentState = state) {
        MapAccessState.Loading -> LoadingScreen(modifier)
        MapAccessState.ConsentRequired -> PrivacyConsentScreen(
            onAccept = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.accept() }
                }
            },
            onDecline = onDecline,
            modifier = modifier,
        )
        MapAccessState.MissingApiKey -> MissingApiKeyScreen(modifier)
        MapAccessState.Ready -> SimpleMapApp(
            navigationSettingsStore = navigationSettingsStore,
            initialNavigationSettings = initialNavigationSettings,
            onThemeModeChanged = onThemeModeChanged,
            onOrientationModeChanged = onOrientationModeChanged,
            onNavigationVisibilityChanged = onNavigationVisibilityChanged,
            onRevokePrivacyConsent = controller::revoke,
            onPrivacyRevoked = onDecline,
            modifier = modifier,
        )
        is MapAccessState.Failed -> FailureScreen(
            message = currentState.message,
            onRetry = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.load() }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
fun SimpleMapApp(
    modifier: Modifier = Modifier,
    showLiveMap: Boolean = true,
    placeRepository: PlaceRepository? = null,
    favoritePlaceStore: FavoritePlaceStore? = null,
    routePlanRepository: RoutePlanRepository? = null,
    tripHistoryStore: TripHistoryStore? = null,
    parkingLocationStore: ParkingLocationStore? = null,
    navigationSettingsStore: NavigationSettingsStore? = null,
    initialNavigationSettings: NavigationSettings? = null,
    onThemeModeChanged: (NavigationThemeMode) -> Unit = {},
    onOrientationModeChanged: (AppOrientationMode) -> Unit = {},
    onNavigationVisibilityChanged: (Boolean) -> Unit = {},
    offlineMapRepository: OfflineMapRepository? = null,
    appUpdateRepository: AppUpdateRepository? = null,
    onRevokePrivacyConsent: () -> Boolean = { false },
    onPrivacyRevoked: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val routeLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var routeObstructions by remember(routeLandscape) {
        mutableStateOf<RoutePlannerObstructions?>(null)
    }
    val routeViewportHeightDp = configuration.screenHeightDp.dp
    val fallbackRouteTopInsetDp = if (routeLandscape) 24.dp else minOf(152.dp, routeViewportHeightDp * 0.26f)
    val fallbackRouteBottomInsetDp = if (routeLandscape) 24.dp else minOf(260.dp, routeViewportHeightDp * 0.36f)
    val fallbackRouteLeftInsetDp = if (routeLandscape) {
        minOf(configuration.screenWidthDp * 0.46f, 420f).dp + 24.dp
    } else {
        24.dp
    }
    val routeTopInsetPx = routeObstructions?.topInsetPx
        ?: with(density) { fallbackRouteTopInsetDp.roundToPx() }
    val routeBottomInsetPx = routeObstructions?.bottomInsetPx
        ?: with(density) { fallbackRouteBottomInsetDp.roundToPx() }
    val routeLeftInsetPx = routeObstructions?.leftInsetPx
        ?: with(density) { fallbackRouteLeftInsetDp.roundToPx() }
    val dependencies = rememberSimpleMapDependencies(
        placeRepository = placeRepository,
        favoritePlaceStore = favoritePlaceStore,
        routePlanRepository = routePlanRepository,
        tripHistoryStore = tripHistoryStore,
        parkingLocationStore = parkingLocationStore,
        navigationSettingsStore = navigationSettingsStore,
        appUpdateRepository = appUpdateRepository,
        offlineMapRepository = offlineMapRepository,
    )
    val repository = dependencies.repository
    val favoriteStore = dependencies.favoriteStore
    val routeRepository = dependencies.routeRepository
    val tripStore = dependencies.tripStore
    val parkingStore = dependencies.parkingStore
    val settingsStore = dependencies.settingsStore
    val updateRepository = dependencies.updateRepository
    val resolvedOfflineRepository = dependencies.resolvedOfflineRepository
    DisposableEffect(resolvedOfflineRepository, offlineMapRepository) {
        onDispose {
            if (offlineMapRepository == null) {
                resolvedOfflineRepository.getOrNull()?.destroy()
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val appState = remember { SimpleMapAppState() }
    var mapController by appState::mapController
    var selectedDestination by appState::selectedDestination
    val placeSearch = remember(repository, coroutineScope) {
        PlaceSearchStateHolder(repository, coroutineScope)
    }
    val placeSearchUiState = placeSearch.uiState
    var selectedPlace by appState::selectedPlace
    var routeDestination by appState::routeDestination
    var routeInitialMode by appState::routeInitialMode
    var selectedRoutePlan by appState::selectedRoutePlan
    var routePlans by appState::routePlans
    val navigationFlow = remember { NavigationFlowStateHolder() }
    val navigationFlowState = navigationFlow.state
    var parkingLocation by appState::parkingLocation
    var favoritePlaceIds by appState::favoritePlaceIds
    val navigationSettingsStateHolder = remember(settingsStore, initialNavigationSettings, coroutineScope) {
        NavigationSettingsStateHolder(
            initialSettings = initialNavigationSettings ?: settingsStore.load(),
            store = settingsStore,
            coroutineScope = coroutineScope,
        )
    }
    val navigationSettings = navigationSettingsStateHolder.settings
    var satelliteEnabled by appState::satelliteEnabled
    var mapPerspectiveMode by appState::mapPerspectiveMode
    var mapScale by appState::mapScale
    var locationEnabled by appState::locationEnabled
    var minuteOfDay by appState::minuteOfDay
    fun updateNavigationSettings(updatedSettings: NavigationSettings) {
        navigationSettingsStateHolder.update(
            updatedSettings = updatedSettings,
            onThemeModeChanged = onThemeModeChanged,
            onOrientationModeChanged = onOrientationModeChanged,
            onSaveFailed = {
                Toast.makeText(context, "设置保存失败，已恢复上次设置", Toast.LENGTH_LONG).show()
            },
        )
    }
    fun dismissSelectedPlace(restoreLocationFollow: Boolean) {
        selectedPlace = null
        mapController?.apply {
            clearSelectedPlace()
            if (restoreLocationFollow) {
                if (locationEnabled) {
                    centerOnCurrentLocationAndFollow()
                } else {
                    restoreCameraFollow()
                }
            }
        }
    }
    val nightModeEnabled = shouldUseNightTheme(
        mode = navigationSettings.themeMode,
        systemInDarkTheme = isSystemInDarkTheme(),
        minuteOfDay = minuteOfDay,
        inTunnel = false,
    )
    val navigationSession by NavigationSessionCoordinator.session.collectAsStateWithLifecycle()
    val navigationSessionFailure by NavigationSessionCoordinator.failure.collectAsStateWithLifecycle()

    BackHandler(
        enabled = placeSearchUiState.active || selectedPlace != null || selectedDestination == HomeDestination.Routes,
    ) {
        when {
            placeSearchUiState.active -> placeSearch.close()
            selectedPlace != null -> {
                dismissSelectedPlace(restoreLocationFollow = true)
            }
            selectedDestination == HomeDestination.Routes -> {
                selectedRoutePlan = null
                routePlans = emptyList()
                mapController?.clearRoute()
                selectedDestination = HomeDestination.Map
            }
        }
    }
    var currentLocation by remember { mutableStateOf<Place?>(null) }
    val locationDistanceResult = remember { FloatArray(1) }
    var mapToolsExpanded by remember { mutableStateOf(false) }
    var liveMapReady by remember(showLiveMap) { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    fun startLiveNavigationSession(request: NavigationRequest): Boolean {
        val spec = NavigationSessionSpec(request.routeRequest, request.plan, navigationSettings)
        if (!NavigationSessionCoordinator.prepare(spec)) {
            Toast.makeText(context, "上一段导航正在结束，请稍后重试", Toast.LENGTH_LONG).show()
            return false
        }
        val started = NavigationSessionService.start(context, spec)
        if (!started) {
            NavigationSessionCoordinator.cancelPending()
            Toast.makeText(context, "无法启动后台导航服务", Toast.LENGTH_LONG).show()
        }
        return started
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val permissionAccess = permissions.locationPermissionAccess()
        val locationGranted = permissionAccess.canShowLocation
        locationEnabled = locationGranted
        mapController?.setMyLocationEnabled(locationGranted)
        if (locationGranted) {
            mapController?.centerOnCurrentLocationAndFollow()
        }
        if (permissionAccess.canNavigate) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            navigationFlow.state.pendingRequest?.let { request ->
                if (request.simulated || startLiveNavigationSession(request)) {
                    navigationFlow.activate(request)
                } else {
                    navigationFlow.failStart()
                }
            }
        } else {
            if (navigationFlow.state.pendingRequest != null) {
                Toast.makeText(context, "实时导航需要精确位置权限", Toast.LENGTH_LONG).show()
            }
            navigationFlow.rejectPendingPermission()
        }
    }

    // 隐私同意后立即请求定位权限，而不是等待用户点击定位按钮
    LaunchedEffect(Unit) {
        if (context.locationPermissionAccess().canShowLocation) {
            locationEnabled = true
            mapController?.setMyLocationMarkerVisible(true)
            mapController?.centerOnCurrentLocationAndFollow()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(favoriteStore) {
        favoritePlaceIds = withContext(Dispatchers.IO) {
            favoriteStore.load().map(Place::id).toSet()
        }
    }

    LaunchedEffect(parkingStore) {
        parkingLocation = withContext(Dispatchers.IO) { parkingStore.load() }
    }

    LaunchedEffect(showLiveMap) {
        liveMapReady = false
        if (showLiveMap) {
            withFrameNanos { }
            liveMapReady = true
        }
    }

    LaunchedEffect(navigationSettingsStateHolder) {
        navigationSettingsStateHolder.publishCurrentSettings(
            onThemeModeChanged = onThemeModeChanged,
            onOrientationModeChanged = onOrientationModeChanged,
        )
    }

    LaunchedEffect(navigationSettings.themeMode) {
        if (navigationSettings.themeMode != NavigationThemeMode.Automatic) return@LaunchedEffect
        while (true) {
            minuteOfDay = currentMinuteOfDay()
            delay(60_000L)
        }
    }

    LaunchedEffect(mapController, nightModeEnabled) {
        mapController?.setNightMode(nightModeEnabled)
    }

    LaunchedEffect(mapController, navigationSettings.trafficLayer) {
        mapController?.setTrafficEnabled(navigationSettings.trafficLayer)
    }

    LaunchedEffect(navigationSession) {
        val session = navigationSession
        if (session == null) {
            if (navigationFlow.clearLiveActive()) {
                selectedDestination = HomeDestination.Routes
            }
        } else if (navigationFlow.state.activeRequest == null) {
            navigationFlow.restoreLive(
                request = NavigationRequest(
                    routeRequest = session.spec.routeRequest,
                    plan = session.spec.plan,
                    simulated = false,
                ),
                startedAtMillis = session.startedAtMillis,
            )
        }
    }

    LaunchedEffect(navigationSessionFailure) {
        val message = navigationSessionFailure ?: return@LaunchedEffect
        if (navigationFlow.clearLiveActive()) {
            selectedDestination = HomeDestination.Routes
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        NavigationSessionCoordinator.clearFailure()
    }

    fun requestLocation() {
        if (context.locationPermissionAccess().canShowLocation) {
            locationEnabled = true
            mapController?.setMyLocationMarkerVisible(true)
            mapController?.centerOnCurrentLocationAndFollow()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    fun submitSearch() {
        val reference = currentLocation?.let { SearchCoordinate(it.latitude, it.longitude) }
            ?: mapController?.cameraCenter()?.let { SearchCoordinate(it.latitude, it.longitude) }
        val city = listOfNotNull(selectedPlace?.district, routeDestination?.district)
            .firstOrNull(String::isNotBlank)
            ?.substringBefore(" · ")
            .orEmpty()
        placeSearch.submit(reference, city)
    }

    LaunchedEffect(placeSearchUiState.active, placeSearchUiState.query) {
        if (!placeSearchUiState.active) return@LaunchedEffect
        placeSearch.cancelPendingSearch()
        val query = placeSearchUiState.query.trim()
        if (query.isEmpty()) {
            placeSearch.showIdle()
            return@LaunchedEffect
        }
        delay(250L)
        submitSearch()
    }

    fun selectPlace(place: Place) {
        selectedPlace = place
        placeSearch.hide()
        mapController?.showPlace(
            latitude = place.latitude,
            longitude = place.longitude,
            title = place.name,
            snippet = place.address.ifBlank { place.district },
        )
    }

    fun toggleFavorite(place: Place) {
        coroutineScope.launch {
            val isFavorite = place.id in favoritePlaceIds
            val persisted = withContext(Dispatchers.IO) {
                if (isFavorite) favoriteStore.remove(place.id) else favoriteStore.save(place)
            }
            if (persisted) {
                favoritePlaceIds = favoritePlaceIds.toMutableSet().apply {
                    if (isFavorite) remove(place.id) else add(place.id)
                }.toSet()
            }
        }
    }

    fun startNavigation(
        routeRequest: RouteRequest,
        plan: RoutePlan,
        simulated: Boolean,
    ) {
        val request = NavigationRequest(routeRequest, plan, simulated)
        if (!NavigationSessionCoordinator.canStartNavigation()) {
            Toast.makeText(context, "上一段导航正在结束，请稍后重试", Toast.LENGTH_LONG).show()
            return
        }
        if (simulated) {
            navigationFlow.startSimulated(request)
            return
        }
        if (context.locationPermissionAccess().canNavigate) {
            navigationFlow.beginLiveStart()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (startLiveNavigationSession(request)) {
                navigationFlow.activate(request)
            } else {
                navigationFlow.failStart()
            }
        } else {
            navigationFlow.awaitLocationPermission(request)
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    navigationFlowState.activeRequest?.let { (routeRequest, plan, simulated) ->
        val sessionController = navigationSession
            ?.takeIf { !simulated && it.spec.routeRequest == routeRequest }
            ?.controller
        if (!canShowNavigation(simulated, sessionController != null)) return@let
        DisposableEffect(onNavigationVisibilityChanged) {
            onNavigationVisibilityChanged(true)
            onDispose { onNavigationVisibilityChanged(false) }
        }
        NavigationScreen(
            origin = routeRequest.origin,
            destination = routeRequest.destination,
            plan = plan,
            routeRequest = routeRequest,
            settings = navigationSettings,
            onSettingsChanged = ::updateNavigationSettings,
            showLiveNavigation = liveMapReady,
            simulated = simulated,
            sessionController = sessionController,
            onExit = {
                if (shouldFinishLiveNavigationSession(simulated)) {
                    NavigationSessionCoordinator.finish(context)
                }
                navigationFlow.clearActive()
                selectedDestination = HomeDestination.Routes
            },
            onNavigationStarted = {
                navigationFlow.markStarted(
                    startedAtMillis = navigationSession?.startedAtMillis ?: System.currentTimeMillis(),
                )
            },
            onNavigationFinished = { phase, finalState ->
                val session = navigationFlow.state.tripSession
                val startedAtMillis = session?.startedAtMillis
                if (simulated && session != null && startedAtMillis != null && !session.recorded) {
                    val completedAtMillis = System.currentTimeMillis()
                    val record = createTripRecord(
                        startedAtMillis = startedAtMillis,
                        completedAtMillis = completedAtMillis,
                        request = routeRequest,
                        plan = plan,
                        phase = phase,
                        remainingDistanceMeters = finalState.remainingDistanceMeters,
                        simulated = simulated,
                    )
                    navigationFlow.markRecorded()
                    coroutineScope.launch(Dispatchers.IO) { tripStore.add(record) }
                }
                if (phase == NavigationPhase.Arrived || phase == NavigationPhase.Failed) {
                    if (shouldFinishLiveNavigationSession(simulated)) {
                        NavigationSessionCoordinator.finish(context, phase)
                    }
                }
            },
            onFindParking = {
                if (shouldFinishLiveNavigationSession(simulated)) {
                    NavigationSessionCoordinator.finish(context)
                }
                navigationFlow.clearActive()
                selectedDestination = HomeDestination.Map
                placeSearch.openNearby(
                    query = "停车场",
                    center = SearchCoordinate(
                        routeRequest.destination.latitude,
                        routeRequest.destination.longitude,
                    ),
                )
            },
            onSaveParkingLocation = { latitude, longitude ->
                val parking = Place(
                    id = "saved-parking-location",
                    name = "停车位置",
                    address = "上次手动保存的位置",
                    district = routeRequest.destination.district,
                    category = "停车",
                    phone = "",
                    latitude = latitude,
                    longitude = longitude,
                    distanceMeters = null,
                )
                coroutineScope.launch {
                    if (withContext(Dispatchers.IO) { parkingStore.save(parking) }) {
                        parkingLocation = parking
                        Toast.makeText(context, "已保存停车位置", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        val backdropBackground = MaterialTheme.colorScheme.background
        val navigationBackdrop = rememberLayerBackdrop {
            drawRect(backdropBackground)
            drawContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(navigationBackdrop),
        ) {
            if (liveMapReady) {
                AmapMapView(
                modifier = Modifier.fillMaxSize(),
                onScaleChanged = { mapScale = it },
                onControllerReady = { controller ->
                    mapController = controller
                    controller.setTrafficEnabled(navigationSettings.trafficLayer)
                    controller.setSatelliteEnabled(satelliteEnabled)
                    controller.setNightMode(nightModeEnabled)
                    controller.setPerspectiveMode(mapPerspectiveMode)
                    controller.setMyLocationEnabled(locationEnabled)
                    if (locationEnabled) {
                        controller.moveToCurrentLocation()
                    }
                    selectedPlace?.let(::selectPlace)
                    selectedRoutePlan?.let { selectedPlan ->
                        controller.showRoutes(
                            routePlans.ifEmpty { listOf(selectedPlan) },
                            selectedPlan.id,
                            routeTopInsetPx,
                            routeBottomInsetPx,
                            routeLeftInsetPx,
                        )
                    }
                },
                onControllerReleased = { controller ->
                    if (mapController === controller) mapController = null
                },
                onLocationChanged = { location ->
                    val previousLocation = currentLocation
                    val shouldCenterMap = previousLocation == null
                    val shouldPublishLocation = previousLocation == null || locationDistanceResult.also { result ->
                        Location.distanceBetween(
                            previousLocation.latitude,
                            previousLocation.longitude,
                            location.latitude,
                            location.longitude,
                            result,
                        )
                    }.first() >= 10f
                    if (shouldPublishLocation) {
                        currentLocation = Place(
                            id = "current-location",
                            name = "我的位置",
                            address = "当前位置",
                            district = "",
                            category = "定位",
                            phone = "",
                            latitude = location.latitude,
                            longitude = location.longitude,
                            distanceMeters = 0,
                        )
                        if (shouldCenterMap && selectedDestination == HomeDestination.Map) {
                            mapController?.moveToCurrentLocation()
                        }
                    }
                },
                )
            } else {
                MapBackdrop()
            }
            if (selectedDestination == HomeDestination.Map) {
            AnimatedContent(
                targetState = placeSearchUiState.active,
                modifier = Modifier.align(Alignment.TopCenter),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "搜索面板",
            ) { active ->
                if (active) {
                    SearchPanel(
                        query = placeSearchUiState.query,
                        onQueryChange = placeSearch::updateQuery,
                        state = placeSearchUiState.result,
                        onSearch = ::submitSearch,
                        onPlaceSelected = ::selectPlace,
                        onClose = placeSearch::close,
                    )
                } else {
                    SearchBar(
                        onClick = placeSearch::open,
                    )
                }
            }
            AnimatedVisibility(
                visible = selectedPlace == null && !placeSearchUiState.active,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                MapLayerControls(
                    trafficEnabled = navigationSettings.trafficLayer,
                    satelliteEnabled = satelliteEnabled,
                    expanded = mapToolsExpanded,
                    onExpandedChange = { mapToolsExpanded = it },
                    onTrafficClick = {
                        val enabled = !navigationSettings.trafficLayer
                        updateNavigationSettings(navigationSettings.copy(trafficLayer = enabled))
                        mapController?.setTrafficEnabled(enabled)
                        mapToolsExpanded = false
                    },
                    onSatelliteClick = {
                        satelliteEnabled = !satelliteEnabled
                        mapController?.setSatelliteEnabled(satelliteEnabled)
                        mapToolsExpanded = false
                    },
                )
            }
            AnimatedVisibility(
                visible = selectedPlace == null && !placeSearchUiState.active,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                MapViewControls(
                    perspectiveMode = mapPerspectiveMode,
                    onPerspectiveModeChange = { mode ->
                        mapPerspectiveMode = mode
                        mapController?.setPerspectiveMode(mode)
                    },
                    onResetNorth = { mapController?.resetNorth() },
                )
            }
            AnimatedVisibility(
                visible = selectedPlace == null && !placeSearchUiState.active,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                MapLocationControl(
                    locationEnabled = locationEnabled,
                    onLocationClick = ::requestLocation,
                    isLandscape = routeLandscape,
                )
            }
            AnimatedVisibility(
                visible = selectedPlace == null,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                MapZoomControls(
                    onZoomIn = { mapController?.zoomIn() },
                    onZoomOut = { mapController?.zoomOut() },
                    isLandscape = routeLandscape,
                )
            }
            AnimatedVisibility(
                visible = selectedPlace == null,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                MapScaleIndicator(
                    scale = mapScale,
                    isLandscape = routeLandscape,
                )
            }
            AnimatedContent(
                targetState = selectedPlace,
                modifier = Modifier.align(Alignment.BottomCenter),
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { it / 4 })
                },
                contentKey = { it?.id },
                label = "地点详情",
            ) { place ->
                if (place != null) {
                    PlaceDetailPanel(
                        place = place,
                        isFavorite = place.id in favoritePlaceIds,
                        interactionEnabled = place.id == selectedPlace?.id,
                        onFavoriteClick = { toggleFavorite(place) },
                        onDirectionsClick = {
                            dismissSelectedPlace(restoreLocationFollow = false)
                            routeDestination = place
                            routeInitialMode = RouteMode.Drive
                            selectedDestination = HomeDestination.Routes
                        },
                        onClose = {
                            dismissSelectedPlace(restoreLocationFollow = true)
                        },
                    )
                }
            }
            } else if (selectedDestination == HomeDestination.Routes) {
            RoutePlannerPanel(
                placeRepository = repository,
                routePlanRepository = routeRepository,
                initialOrigin = currentLocation,
                initialDestination = routeDestination,
                initialMode = routeInitialMode,
                autoPlan = routeDestination != null,
                initialDriveOptions = navigationSettings.driveRouteOptions,
                onDriveOptionsChanged = { driveRouteOptions ->
                    updateNavigationSettings(navigationSettings.copy(driveRouteOptions = driveRouteOptions))
                },
                onRouteSelected = {
                    selectedRoutePlan = it
                },
                onRoutesChanged = { plans, selectedPlan ->
                    routePlans = plans
                    selectedRoutePlan = selectedPlan
                    mapController?.showRoutes(
                        plans,
                        selectedPlan?.id,
                        routeTopInsetPx,
                        routeBottomInsetPx,
                        routeLeftInsetPx,
                    )
                },
                onRouteCleared = {
                    selectedRoutePlan = null
                    routePlans = emptyList()
                    mapController?.clearRoute()
                },
                onStartNavigation = { request, plan, simulated ->
                    startNavigation(request, plan, simulated)
                },
                onBack = {
                    selectedRoutePlan = null
                    routePlans = emptyList()
                    mapController?.clearRoute()
                    selectedDestination = HomeDestination.Map
                },
                onObstructionsChanged = { obstructions ->
                    val measured = obstructions.takeIf {
                        it.topInsetPx > 0 || it.bottomInsetPx > 0 || it.leftInsetPx > 0
                    } ?: return@RoutePlannerPanel
                    if (measured != routeObstructions) {
                        routeObstructions = measured
                        selectedRoutePlan?.let { selectedPlan ->
                            mapController?.showRoutes(
                                routePlans.ifEmpty { listOf(selectedPlan) },
                                selectedPlan.id,
                                measured.topInsetPx,
                                measured.bottomInsetPx,
                                measured.leftInsetPx,
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            } else if (selectedDestination == HomeDestination.Trips) {
            TripsPanel(
                tripHistoryStore = tripStore,
                parkingLocation = parkingLocation,
                onReturnToParking = { parking ->
                    routeDestination = parking
                    routeInitialMode = RouteMode.Walk
                    selectedDestination = HomeDestination.Routes
                },
                onPlanAgain = { trip ->
                    routeDestination = trip.destination
                    routeInitialMode = trip.mode
                    selectedDestination = HomeDestination.Routes
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            } else {
                ProfilePanel(
                favoriteStore = favoriteStore,
                settings = navigationSettings,
                updateRepository = updateRepository,
                offlineRepository = resolvedOfflineRepository.getOrNull(),
                offlineUnavailableMessage = resolvedOfflineRepository.exceptionOrNull()?.localizedMessage,
                destroyOfflineRepositoryOnDispose = false,
                onNavigateTo = { place ->
                    routeDestination = place
                    routeInitialMode = RouteMode.Drive
                    selectedDestination = HomeDestination.Routes
                },
                onFavoritesChanged = { favorites ->
                    favoritePlaceIds = favorites.mapTo(mutableSetOf(), Place::id)
                },
                onClearLocalData = {
                    val result = navigationSettingsStateHolder.mutatePersistedSettings(
                        mutation = {
                            val favoritesCleared = favoriteStore.clear()
                            val tripsCleared = tripStore.clear()
                            val parkingCleared = parkingStore.clear()
                            val settingsCleared = settingsStore.save(NavigationSettings())
                            PersistedSettingsMutation(
                                value = LocalDataClearResult(
                                    fullyCleared = favoritesCleared && tripsCleared && parkingCleared &&
                                        settingsCleared,
                                    favoritePlaceIds = favoriteStore.load().mapTo(mutableSetOf(), Place::id),
                                    parkingLocation = parkingStore.load(),
                                ),
                                settings = settingsStore.load(),
                            )
                        },
                        onThemeModeChanged = onThemeModeChanged,
                        onOrientationModeChanged = onOrientationModeChanged,
                    )
                    favoritePlaceIds = result.favoritePlaceIds
                    parkingLocation = result.parkingLocation
                    result.fullyCleared
                },
                onRevokePrivacyConsent = onRevokePrivacyConsent,
                onPrivacyRevoked = onPrivacyRevoked,
                onSettingsChanged = ::updateNavigationSettings,
                modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        if (selectedDestination != HomeDestination.Routes &&
            !(selectedDestination == HomeDestination.Map && placeSearchUiState.active) &&
            selectedPlace == null
        ) {
            FloatingNavigation(
                selected = selectedDestination,
                isLandscape = routeLandscape,
                backdrop = navigationBackdrop,
                onSelected = { destination ->
                    placeSearch.hide()
                    if (selectedDestination == HomeDestination.Routes && destination != HomeDestination.Routes) {
                        selectedRoutePlan = null
                        routePlans = emptyList()
                        mapController?.clearRoute()
                    }
                    selectedDestination = destination
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SimpleMapPreview() {
    SimpleMapTheme {
        SimpleMapApp(showLiveMap = false)
    }
}
