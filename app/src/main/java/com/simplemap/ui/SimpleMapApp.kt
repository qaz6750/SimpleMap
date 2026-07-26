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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simplemap.amap.AmapMapView
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationSessionService
import com.simplemap.navigation.NavigationSessionCoordinator
import com.simplemap.navigation.NavigationSessionSpec
import com.simplemap.amap.AmapMapController
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.calculateMapScale
import com.simplemap.offline.AmapOfflineMapRepository
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.permission.locationPermissionAccess
import com.simplemap.route.AmapRoutePlanRepository
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.route.RouteRequest
import com.simplemap.search.AmapPlaceRepository
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.search.SharedPreferencesFavoritePlaceStore
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.currentMinuteOfDay
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.settings.SharedPreferencesNavigationSettingsStore
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState
import com.simplemap.trips.SharedPreferencesTripHistoryStore
import com.simplemap.trips.ParkingLocationStore
import com.simplemap.trips.SharedPreferencesParkingLocationStore
import com.simplemap.trips.TripHistoryStore
import com.simplemap.update.AppUpdateRepository
import com.simplemap.update.GitHubReleaseUpdateRepository
import com.simplemap.trips.createTripRecord
import com.simplemap.ui.theme.SimpleMapTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HomeDestination(val label: String) {
    Map("地图"),
    Routes("路线"),
    Trips("行程"),
    Profile("我的"),
}

private val BottomDestinations = listOf(
    HomeDestination.Map,
    HomeDestination.Trips,
    HomeDestination.Profile,
)

internal val FloatingNavigationClearance = 94.dp

private data class NavigationRequest(
    val routeRequest: RouteRequest,
    val plan: RoutePlan,
    val simulated: Boolean,
)

private data class ActiveTripSession(
    val startedAtMillis: Long? = null,
    val recorded: Boolean = false,
)

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
    val repository = remember(context, placeRepository) {
        placeRepository ?: AmapPlaceRepository(context)
    }
    val favoriteStore = remember(context, favoritePlaceStore) {
        favoritePlaceStore ?: SharedPreferencesFavoritePlaceStore(context)
    }
    val routeRepository = remember(context, routePlanRepository) {
        routePlanRepository ?: AmapRoutePlanRepository(context)
    }
    val tripStore = remember(context, tripHistoryStore) {
        tripHistoryStore ?: SharedPreferencesTripHistoryStore(context)
    }
    val parkingStore = remember(context, parkingLocationStore) {
        parkingLocationStore ?: SharedPreferencesParkingLocationStore(context)
    }
    val settingsStore = remember(context, navigationSettingsStore) {
        navigationSettingsStore ?: SharedPreferencesNavigationSettingsStore(context)
    }
    val updateRepository = remember(appUpdateRepository) {
        appUpdateRepository ?: GitHubReleaseUpdateRepository()
    }
    val resolvedOfflineRepository = remember(context, offlineMapRepository) {
        offlineMapRepository?.let { Result.success(it) }
            ?: runCatching { AmapOfflineMapRepository(context) }
    }
    DisposableEffect(resolvedOfflineRepository, offlineMapRepository) {
        onDispose {
            if (offlineMapRepository == null) {
                resolvedOfflineRepository.getOrNull()?.destroy()
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var mapController by remember { mutableStateOf<AmapMapController?>(null) }
    var selectedDestination by remember { mutableStateOf(HomeDestination.Map) }
    val placeSearch = remember(repository, coroutineScope) {
        PlaceSearchStateHolder(repository, coroutineScope)
    }
    val placeSearchUiState = placeSearch.uiState
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var routeDestination by remember { mutableStateOf<Place?>(null) }
    var routeInitialMode by remember { mutableStateOf(RouteMode.Drive) }
    var selectedRoutePlan by remember { mutableStateOf<RoutePlan?>(null) }
    var routePlans by remember { mutableStateOf<List<RoutePlan>>(emptyList()) }
    var pendingNavigation by remember { mutableStateOf<NavigationRequest?>(null) }
    var activeNavigation by remember { mutableStateOf<NavigationRequest?>(null) }
    var activeTripSession by remember { mutableStateOf<ActiveTripSession?>(null) }
    var parkingLocation by remember { mutableStateOf<Place?>(null) }
    var favoritePlaceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val navigationSettingsStateHolder = remember(settingsStore, initialNavigationSettings, coroutineScope) {
        NavigationSettingsStateHolder(
            initialSettings = initialNavigationSettings ?: settingsStore.load(),
            store = settingsStore,
            coroutineScope = coroutineScope,
        )
    }
    val navigationSettings = navigationSettingsStateHolder.settings
    var satelliteEnabled by remember { mutableStateOf(false) }
    var mapPerspectiveMode by remember { mutableStateOf(AmapPerspectiveMode.TwoDimensional) }
    var mapScale by remember {
        mutableStateOf(calculateMapScale(zoom = 16f, latitude = 30.0, targetWidthPixels = 96f))
    }
    var locationEnabled by remember { mutableStateOf(false) }
    var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
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
            pendingNavigation?.let { request ->
                if (request.simulated || startLiveNavigationSession(request)) {
                    activeNavigation = request
                } else {
                    activeTripSession = null
                }
            }
            pendingNavigation = null
        } else {
            if (pendingNavigation != null) {
                Toast.makeText(context, "实时导航需要精确位置权限", Toast.LENGTH_LONG).show()
            }
            pendingNavigation = null
            activeTripSession = null
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
            if (activeNavigation?.simulated == false) {
                activeNavigation = null
                activeTripSession = null
                selectedDestination = HomeDestination.Routes
            }
        } else if (activeNavigation == null) {
            activeNavigation = NavigationRequest(session.spec.routeRequest, session.spec.plan, simulated = false)
            activeTripSession = ActiveTripSession(startedAtMillis = session.startedAtMillis)
        }
    }

    LaunchedEffect(navigationSessionFailure) {
        val message = navigationSessionFailure ?: return@LaunchedEffect
        if (activeNavigation?.simulated == false) {
            activeNavigation = null
            activeTripSession = null
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
        activeTripSession = ActiveTripSession()
        if (simulated) {
            activeNavigation = request
            return
        }
        if (context.locationPermissionAccess().canNavigate) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (startLiveNavigationSession(request)) {
                activeNavigation = request
            } else {
                activeTripSession = null
            }
        } else {
            pendingNavigation = request
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    activeNavigation?.let { (routeRequest, plan, simulated) ->
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
                activeNavigation = null
                activeTripSession = null
                selectedDestination = HomeDestination.Routes
            },
            onNavigationStarted = {
                val session = activeTripSession
                if (session != null && session.startedAtMillis == null) {
                    activeTripSession = session.copy(
                        startedAtMillis = navigationSession?.startedAtMillis ?: System.currentTimeMillis(),
                    )
                }
            },
            onNavigationFinished = { phase, finalState ->
                val session = activeTripSession
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
                    activeTripSession = session.copy(recorded = true)
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
                activeNavigation = null
                activeTripSession = null
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

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在准备地图", color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .widthIn(max = 560.dp),
            ) {
                Text(
                    text = "欢迎使用 SimpleMap",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "为提供地图展示、地点搜索、路线规划和实时导航，应用会在你同意后使用高德地图服务，并在获得系统授权后处理位置信息。不同意时不会初始化地图服务，也不会访问位置。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 25.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "你可以稍后在设置中管理定位权限和数据选项。继续即表示你已阅读并同意隐私说明。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("同意并继续", modifier = Modifier.padding(vertical = 5.dp))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("暂不同意", modifier = Modifier.padding(vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun MissingApiKeyScreen(modifier: Modifier = Modifier) {
    StatusScreen(
        title = "地图服务尚未配置",
        message = "请在 local.properties 中添加与 com.simplemap 绑定的 AMAP_API_KEY，然后重新构建应用。",
        modifier = modifier,
    )
}

@Composable
private fun FailureScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusScreen(
        title = "地图服务暂不可用",
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
                Text("重试")
            }
        },
    )
}

@Composable
private fun StatusScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

@Composable
private fun MapBackdrop() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F6FC))
            .semantics { contentDescription = "地图区域" },
    ) {
        val road = Path().apply {
            moveTo(-40f, size.height * 0.78f)
            cubicTo(size.width * 0.2f, size.height * 0.6f, size.width * 0.45f, size.height * 0.84f, size.width + 40f, size.height * 0.46f)
        }
        drawPath(road, color = Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round))
        drawPath(road, color = Color(0xFFC7D5E6), style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFDCEAFF), radius = 92f, center = Offset(size.width * 0.18f, size.height * 0.28f))
        drawCircle(Color(0xFFE7F1FD), radius = 135f, center = Offset(size.width * 0.82f, size.height * 0.22f))
    }
}

@Composable
private fun FloatingNavigation(
    selected: HomeDestination,
    isLandscape: Boolean,
    backdrop: LayerBackdrop,
    onSelected: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscapeWidth = (LocalConfiguration.current.screenWidthDp.dp - 176.dp).coerceIn(154.dp, 240.dp)
    val shape = RoundedCornerShape(if (isLandscape) 18.dp else 30.dp)
    val glassSurface = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
    }
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .then(
                if (isLandscape) {
                    Modifier.width(landscapeWidth)
                } else {
                    Modifier.fillMaxWidth().widthIn(max = 440.dp)
                },
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(6.dp.toPx())
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                    )
                },
                onDrawSurface = { drawRect(glassSurface) },
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                shape,
            )
            .semantics { contentDescription = "沉浸式底部导航" },
    ) {
        Row(
            modifier = Modifier
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomDestinations.forEach { destination ->
                NavigationItem(
                    label = destination.label,
                    selected = selected == destination,
                    onClick = { onSelected(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "导航项背景",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "导航项前景",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(180),
        label = "导航项图标缩放",
    )
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(25.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeDestinationIcon(
                label = label,
                color = contentColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun HomeDestinationIcon(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
        when (label) {
            "地图" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.18f)
                    lineTo(size.width * 0.36f, size.height * 0.08f)
                    lineTo(size.width * 0.64f, size.height * 0.2f)
                    lineTo(size.width * 0.92f, size.height * 0.1f)
                    lineTo(size.width * 0.92f, size.height * 0.82f)
                    lineTo(size.width * 0.64f, size.height * 0.92f)
                    lineTo(size.width * 0.36f, size.height * 0.8f)
                    lineTo(size.width * 0.08f, size.height * 0.9f)
                    close()
                    moveTo(size.width * 0.36f, size.height * 0.08f)
                    lineTo(size.width * 0.36f, size.height * 0.8f)
                    moveTo(size.width * 0.64f, size.height * 0.2f)
                    lineTo(size.width * 0.64f, size.height * 0.92f)
                }
                drawPath(path, color, style = stroke)
            }
            "路线" -> {
                drawCircle(color, size.minDimension * 0.1f, Offset(size.width * 0.23f, size.height * 0.76f), style = stroke)
                drawCircle(color, size.minDimension * 0.1f, Offset(size.width * 0.77f, size.height * 0.24f), style = stroke)
                val path = Path().apply {
                    moveTo(size.width * 0.31f, size.height * 0.7f)
                    cubicTo(size.width * 0.7f, size.height * 0.66f, size.width * 0.3f, size.height * 0.32f, size.width * 0.69f, size.height * 0.29f)
                }
                drawPath(path, color, style = stroke)
            }
            "行程" -> {
                drawCircle(color, size.minDimension * 0.4f, center, style = stroke)
                drawLine(color, center, Offset(size.width * 0.5f, size.height * 0.25f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * 0.7f, size.height * 0.58f), stroke.width, StrokeCap.Round)
            }
            else -> {
                drawCircle(color, size.minDimension * 0.18f, Offset(size.width * 0.5f, size.height * 0.32f), style = stroke)
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.88f)
                    cubicTo(size.width * 0.2f, size.height * 0.58f, size.width * 0.8f, size.height * 0.58f, size.width * 0.82f, size.height * 0.88f)
                }
                drawPath(path, color, style = stroke)
            }
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
