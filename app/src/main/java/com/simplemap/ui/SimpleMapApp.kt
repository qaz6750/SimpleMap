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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.simplemap.amap.AmapMapView
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationSessionService
import com.simplemap.navigation.NavigationSessionCoordinator
import com.simplemap.navigation.NavigationSessionSpec
import com.simplemap.amap.AmapMapController
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.MapScale
import com.simplemap.amap.calculateMapScale
import com.simplemap.offline.AmapOfflineMapRepository
import com.simplemap.offline.OfflineMapRepository
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
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.settings.SharedPreferencesNavigationSettingsStore
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState
import com.simplemap.trips.SharedPreferencesTripHistoryStore
import com.simplemap.trips.ParkingLocationStore
import com.simplemap.trips.SharedPreferencesParkingLocationStore
import com.simplemap.trips.TripHistoryStore
import com.simplemap.trips.createTripRecord
import com.simplemap.ui.theme.SimpleMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime

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

internal val FloatingNavigationClearance = 82.dp

private sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Loading : PlaceSearchState
    data class Results(val places: List<Place>) : PlaceSearchState
    data class Failed(val message: String) : PlaceSearchState
}

private data class NavigationRequest(
    val routeRequest: RouteRequest,
    val plan: RoutePlan,
    val simulated: Boolean,
)

private data class ActiveTripSession(
    val startedAtMillis: Long? = null,
    val recorded: Boolean = false,
)

internal fun canShowNavigation(simulated: Boolean, sessionReady: Boolean): Boolean = simulated || sessionReady

private val SearchIcon = ImageVector.Builder(
    name = "Search",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color(0xFF1E2927)), strokeLineWidth = 2f) {
        moveTo(10.5f, 4f)
        curveTo(6.9f, 4f, 4f, 6.9f, 4f, 10.5f)
        curveTo(4f, 14.1f, 6.9f, 17f, 10.5f, 17f)
        curveTo(14.1f, 17f, 17f, 14.1f, 17f, 10.5f)
        curveTo(17f, 6.9f, 14.1f, 4f, 10.5f, 4f)
        close()
        moveTo(15.2f, 15.2f)
        lineTo(21f, 21f)
    }
}.build()

private val CompassIcon = ImageVector.Builder(
    name = "Compass",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF1466D8))) {
        moveTo(18.8f, 5.2f)
        lineTo(14.8f, 14.8f)
        lineTo(5.2f, 18.8f)
        lineTo(9.2f, 9.2f)
        close()
    }
}.build()

@Composable
fun SimpleMapRoot(
    controller: MapAccessController,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    navigationSettingsStore: NavigationSettingsStore? = null,
    onThemeModeChanged: (NavigationThemeMode) -> Unit = {},
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
            onThemeModeChanged = onThemeModeChanged,
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
    onThemeModeChanged: (NavigationThemeMode) -> Unit = {},
    offlineMapRepository: OfflineMapRepository? = null,
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
    val coroutineScope = rememberCoroutineScope()
    val settingsSaveMutex = remember(settingsStore) { Mutex() }
    var mapController by remember { mutableStateOf<AmapMapController?>(null) }
    var selectedDestination by remember { mutableStateOf(HomeDestination.Map) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<PlaceSearchState>(PlaceSearchState.Idle) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var nearbySearchCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
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
    var navigationSettings by remember { mutableStateOf(NavigationSettings()) }
    var trafficEnabled by remember { mutableStateOf(false) }
    var satelliteEnabled by remember { mutableStateOf(false) }
    var mapPerspectiveMode by remember { mutableStateOf(AmapPerspectiveMode.TwoDimensional) }
    var mapScale by remember {
        mutableStateOf(calculateMapScale(zoom = 16f, latitude = 30.0, targetWidthPixels = 96f))
    }
    var locationEnabled by remember { mutableStateOf(false) }
    var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
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
        enabled = searchActive || selectedPlace != null || selectedDestination == HomeDestination.Routes,
    ) {
        when {
            searchActive -> {
                searchJob?.cancel()
                searchActive = false
                nearbySearchCenter = null
                searchQuery = ""
                searchState = PlaceSearchState.Idle
            }
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
        NavigationSessionCoordinator.prepare(
            NavigationSessionSpec(request.routeRequest, request.plan, navigationSettings),
        )
        val started = NavigationSessionService.start(context, request.routeRequest.destination.name)
        if (!started) {
            NavigationSessionCoordinator.cancelPending()
            Toast.makeText(context, "无法启动后台导航服务", Toast.LENGTH_LONG).show()
        }
        return started
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val locationGranted = fineGranted ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationEnabled = locationGranted
        mapController?.setMyLocationEnabled(locationGranted)
        if (locationGranted) {
            mapController?.centerOnCurrentLocationAndFollow()
        }
        if (fineGranted) {
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
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
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

    LaunchedEffect(settingsStore) {
        navigationSettings = withContext(Dispatchers.IO) { settingsStore.load() }
        onThemeModeChanged(navigationSettings.themeMode)
    }

    LaunchedEffect(navigationSettings.themeMode) {
        while (true) {
            minuteOfDay = currentMinuteOfDay()
            delay(60_000L)
        }
    }

    LaunchedEffect(mapController, nightModeEnabled) {
        mapController?.setNightMode(nightModeEnabled)
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
            navigationSettings = session.spec.settings
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
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
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
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        val nearbyCenter = nearbySearchCenter
        searchJob?.cancel()
        searchState = PlaceSearchState.Loading
        searchJob = coroutineScope.launch {
            val reference = currentLocation?.let { it.latitude to it.longitude }
                ?: mapController?.cameraCenter()?.let { it.latitude to it.longitude }
            val city = listOfNotNull(selectedPlace?.district, routeDestination?.district)
                .firstOrNull(String::isNotBlank)
                ?.substringBefore(" · ")
                .orEmpty()
            val result = withContext(Dispatchers.IO) {
                val placeResult = if (nearbyCenter != null) {
                    repository.searchNearby(
                        query = query,
                        latitude = nearbyCenter.first,
                        longitude = nearbyCenter.second,
                        radiusMeters = 3_000,
                    )
                } else {
                    repository.search(query, city)
                }
                placeResult.map { places ->
                    reference?.let { (latitude, longitude) ->
                        places.map { place ->
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                latitude,
                                longitude,
                                place.latitude,
                                place.longitude,
                                distance,
                            )
                            place.copy(distanceMeters = distance.first().toInt())
                        }.sortedBy { it.distanceMeters }
                    } ?: places
                }
            }
            searchState = result.fold(
                onSuccess = { places -> PlaceSearchState.Results(places) },
                onFailure = {
                    PlaceSearchState.Failed(it.localizedMessage ?: "搜索服务暂不可用")
                },
            )
        }
    }

    LaunchedEffect(searchActive, searchQuery) {
        if (!searchActive) return@LaunchedEffect
        searchJob?.cancel()
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchState = PlaceSearchState.Idle
            return@LaunchedEffect
        }
        delay(250L)
        submitSearch()
    }

    fun selectPlace(place: Place) {
        selectedPlace = place
        searchActive = false
        nearbySearchCenter = null
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
        activeTripSession = ActiveTripSession()
        if (simulated) {
            activeNavigation = request
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
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
        NavigationScreen(
            origin = routeRequest.origin,
            destination = routeRequest.destination,
            plan = plan,
            routeRequest = routeRequest,
            settings = navigationSettings,
            onSettingsChanged = { updatedSettings ->
                navigationSettings = updatedSettings
                onThemeModeChanged(updatedSettings.themeMode)
                coroutineScope.launch {
                    settingsSaveMutex.withLock {
                        withContext(Dispatchers.IO) { settingsStore.save(updatedSettings) }
                    }
                }
            },
            showLiveNavigation = liveMapReady,
            simulated = simulated,
            sessionController = sessionController,
            onExit = {
                if (simulated) {
                    NavigationSessionService.stop(context)
                } else {
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
                    if (simulated) NavigationSessionService.stop(context)
                }
            },
            onFindParking = {
                if (simulated) {
                    NavigationSessionService.stop(context)
                } else {
                    NavigationSessionCoordinator.finish(context)
                }
                activeNavigation = null
                activeTripSession = null
                selectedDestination = HomeDestination.Map
                nearbySearchCenter = routeRequest.destination.latitude to routeRequest.destination.longitude
                searchActive = true
                searchQuery = "停车场"
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
        if (liveMapReady && selectedDestination in setOf(HomeDestination.Map, HomeDestination.Routes)) {
            AmapMapView(
                modifier = Modifier.fillMaxSize(),
                onScaleChanged = { mapScale = it },
                onControllerReady = { controller ->
                    mapController = controller
                    controller.setTrafficEnabled(trafficEnabled)
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
        } else if (selectedDestination == HomeDestination.Profile) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            MapBackdrop()
        }
        if (selectedDestination == HomeDestination.Map) {
            AnimatedContent(
                targetState = searchActive,
                modifier = Modifier.align(Alignment.TopCenter),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "搜索面板",
            ) { active ->
                if (active) {
                    SearchPanel(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        state = searchState,
                        onSearch = ::submitSearch,
                        onPlaceSelected = ::selectPlace,
                        onClose = {
                            searchJob?.cancel()
                            searchActive = false
                            nearbySearchCenter = null
                            searchQuery = ""
                            searchState = PlaceSearchState.Idle
                        },
                    )
                } else {
                    SearchBar(
                        onClick = {
                            nearbySearchCenter = null
                            searchActive = true
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = selectedPlace == null && !searchActive,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                MapLayerControls(
                    trafficEnabled = trafficEnabled,
                    satelliteEnabled = satelliteEnabled,
                    expanded = mapToolsExpanded,
                    onExpandedChange = { mapToolsExpanded = it },
                    onTrafficClick = {
                        trafficEnabled = !trafficEnabled
                        mapController?.setTrafficEnabled(trafficEnabled)
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
                visible = selectedPlace == null && !searchActive,
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
                visible = selectedPlace == null && !searchActive,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                MapLocationControl(
                    locationEnabled = locationEnabled,
                    onLocationClick = ::requestLocation,
                )
            }
            AnimatedVisibility(
                visible = selectedPlace == null,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                MapZoomControls(
                    scale = mapScale,
                    onZoomIn = { mapController?.zoomIn() },
                    onZoomOut = { mapController?.zoomOut() },
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
                    val updatedSettings = navigationSettings.copy(driveRouteOptions = driveRouteOptions)
                    navigationSettings = updatedSettings
                    coroutineScope.launch {
                        settingsSaveMutex.withLock {
                            withContext(Dispatchers.IO) { settingsStore.save(updatedSettings) }
                        }
                    }
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
            val resolvedOfflineRepository = remember(context, offlineMapRepository) {
                offlineMapRepository?.let { Result.success(it) }
                    ?: runCatching { AmapOfflineMapRepository(context) }
            }
            ProfilePanel(
                favoriteStore = favoriteStore,
                settingsStore = settingsStore,
                offlineRepository = resolvedOfflineRepository.getOrNull(),
                offlineUnavailableMessage = resolvedOfflineRepository.exceptionOrNull()?.localizedMessage,
                destroyOfflineRepositoryOnDispose = offlineMapRepository == null,
                onNavigateTo = { place ->
                    routeDestination = place
                    routeInitialMode = RouteMode.Drive
                    selectedDestination = HomeDestination.Routes
                },
                onFavoritesChanged = { favorites ->
                    favoritePlaceIds = favorites.mapTo(mutableSetOf(), Place::id)
                },
                onClearLocalData = {
                    val favoritesCleared = favoriteStore.clear()
                    val tripsCleared = tripStore.clear()
                    val parkingCleared = parkingStore.clear()
                    val settingsCleared = settingsSaveMutex.withLock {
                        settingsStore.save(NavigationSettings())
                    }
                    favoritesCleared && tripsCleared && parkingCleared && settingsCleared
                },
                onLocalDataCleared = {
                    favoritePlaceIds = emptySet()
                    parkingLocation = null
                    navigationSettings = NavigationSettings()
                    onThemeModeChanged(NavigationSettings().themeMode)
                },
                onRevokePrivacyConsent = onRevokePrivacyConsent,
                onPrivacyRevoked = onPrivacyRevoked,
                onSettingsChanged = {
                    navigationSettings = it
                    onThemeModeChanged(it.themeMode)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        if (selectedDestination != HomeDestination.Routes &&
            !(selectedDestination == HomeDestination.Map && searchActive) &&
            selectedPlace == null
        ) {
            FloatingNavigation(
                selected = selectedDestination,
                onSelected = { destination ->
                    searchActive = false
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
            .background(Color(0xFFE8EEE9))
            .semantics { contentDescription = "地图区域" },
    ) {
        val road = Path().apply {
            moveTo(-40f, size.height * 0.78f)
            cubicTo(size.width * 0.2f, size.height * 0.6f, size.width * 0.45f, size.height * 0.84f, size.width + 40f, size.height * 0.46f)
        }
        drawPath(road, color = Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round))
        drawPath(road, color = Color(0xFFC8D1CB), style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFA8D8C2), radius = 92f, center = Offset(size.width * 0.18f, size.height * 0.28f))
        drawCircle(Color(0xFFDAE6D9), radius = 135f, center = Offset(size.width * 0.82f, size.height * 0.22f))
    }
}

@Composable
private fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "搜索地点或路线" },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SearchIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "搜索地点或路线",
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    state: PlaceSearchState,
    onSearch: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .widthIn(max = 680.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 12.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入地点或路线") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = SearchIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(SearchIcon, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    shape = RoundedCornerShape(8.dp),
                )
                TextButton(onClick = onClose) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
            AnimatedContent(
                targetState = state,
                modifier = Modifier.heightIn(max = 360.dp),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "搜索结果",
            ) { animatedState ->
                when (animatedState) {
                PlaceSearchState.Idle -> Unit
                PlaceSearchState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }
                is PlaceSearchState.Failed -> SearchMessage(animatedState.message)
                is PlaceSearchState.Results -> {
                    if (animatedState.places.isEmpty()) {
                        SearchMessage("没有找到相关地点，试试名称中的关键词")
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(animatedState.places, key = { it.id }) { place ->
                                SearchResultItem(place = place, onClick = { onPlaceSelected(place) })
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 21.sp,
    )
}

@Composable
private fun SearchResultItem(
    place: Place,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看地点 ${place.name}" }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place.name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            place.distanceMeters?.let {
                Text(formatDistance(it), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOf(place.district, place.address).filter { it.isNotBlank() }.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PlaceDetailPanel(
    place: Place,
    isFavorite: Boolean,
    interactionEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onDirectionsClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (place.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                TextButton(onClick = onClose, enabled = interactionEnabled) { Text("关闭") }
            }
            Spacer(Modifier.height(12.dp))
            PlaceMetadata(place)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onFavoriteClick,
                    enabled = interactionEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
                Button(
                    onClick = onDirectionsClick,
                    enabled = interactionEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("去这里")
                }
            }
        }
    }
}

@Composable
private fun PlaceMetadata(place: Place) {
    val details = buildList {
        if (place.district.isNotBlank()) add("区域" to place.district)
        if (place.category.isNotBlank()) add("分类" to place.category)
        if (place.phone.isNotBlank()) add("电话" to place.phone)
        place.distanceMeters?.let { add("距离" to formatDistance(it)) }
    }
    details.forEach { (label, value) ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(label, modifier = Modifier.widthIn(min = 52.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}

private fun formatDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "${distanceMeters} 米"
} else {
    "%.1f 公里".format(distanceMeters / 1_000f)
}

@Composable
private fun MapLayerControls(
    trafficEnabled: Boolean,
    satelliteEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTrafficClick: () -> Unit,
    onSatelliteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 78.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapToolButton("路况", trafficEnabled, onTrafficClick)
                MapToolButton("卫星", satelliteEnabled, onSatelliteClick)
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFAFFFFFF),
            shadowElevation = 7.dp,
        ) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.size(46.dp),
            ) {
                MapToolsIcon(
                    expanded = expanded,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = if (expanded) "收起图层" else "展开图层" },
                )
            }
        }
    }
}

@Composable
private fun MapLocationControl(
    locationEnabled: Boolean,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 116.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (locationEnabled) Color(0xFF1466D8) else Color(0xFAFFFFFF),
        shadowElevation = 7.dp,
    ) {
        IconButton(onClick = onLocationClick, modifier = Modifier.size(46.dp)) {
            CurrentLocationIcon(
                active = locationEnabled,
                modifier = Modifier
                    .size(24.dp)
                    .semantics {
                        contentDescription = if (locationEnabled) {
                            "当前位置，定位已开启"
                        } else {
                            "定位到当前位置"
                        }
                    },
            )
        }
    }
}

@Composable
private fun MapViewControls(
    perspectiveMode: AmapPerspectiveMode,
    onPerspectiveModeChange: (AmapPerspectiveMode) -> Unit,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 132.dp, end = 18.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = Color(0xFAFFFFFF),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
        ) {
            Row {
                MapPerspectiveButton(
                    label = "2D",
                    selected = perspectiveMode == AmapPerspectiveMode.TwoDimensional,
                    onClick = { onPerspectiveModeChange(AmapPerspectiveMode.TwoDimensional) },
                )
                MapPerspectiveButton(
                    label = "3D",
                    selected = perspectiveMode == AmapPerspectiveMode.ThreeDimensional,
                    onClick = { onPerspectiveModeChange(AmapPerspectiveMode.ThreeDimensional) },
                )
            }
        }
        Surface(
            color = Color(0xFAFFFFFF),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
        ) {
            IconButton(
                onClick = onResetNorth,
                modifier = Modifier
                    .size(42.dp)
                    .semantics { contentDescription = "地图正北" },
            ) {
                Icon(
                    imageVector = CompassIcon,
                    contentDescription = null,
                    tint = Color(0xFF1466D8),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MapPerspectiveButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 42.dp, height = 38.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "地图视角 $label" },
        color = if (selected) Color(0xFF1466D8) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF1466D8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MapZoomControls(
    scale: MapScale,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, bottom = 116.dp),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 7.dp,
    ) {
        Column {
            MapScaleIndicator(scale)
            HorizontalDivider(color = Color(0xFFD9E4F2), thickness = 1.dp)
            MapZoomButton(symbol = "+", description = "放大地图", onClick = onZoomIn)
            HorizontalDivider(color = Color(0xFFD9E4F2), thickness = 1.dp)
            MapZoomButton(symbol = "−", description = "缩小地图", onClick = onZoomOut)
        }
    }
}

@Composable
private fun MapScaleIndicator(scale: MapScale) {
    val label = if (scale.distanceMeters < 1_000) {
        "${scale.distanceMeters} 米"
    } else {
        "${scale.distanceMeters / 1_000} 公里"
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .semantics { contentDescription = "地图比例尺 $label" },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(label, color = Color(0xFF263B5A), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(width = 46.dp, height = 7.dp),
        ) {
            val lineWidth = scale.widthPixels.coerceIn(18f, size.width)
            val stroke = 1.5.dp.toPx()
            drawLine(Color(0xFF1466D8), Offset(0f, size.height), Offset(lineWidth, size.height), stroke)
            drawLine(Color(0xFF1466D8), Offset(0f, size.height * 0.35f), Offset(0f, size.height), stroke)
            drawLine(Color(0xFF1466D8), Offset(lineWidth, size.height * 0.35f), Offset(lineWidth, size.height), stroke)
        }
    }
}

@Composable
private fun MapZoomButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = symbol,
            color = Color(0xFF1466D8),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MapToolsIcon(expanded: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color(0xFF263B5A)
        val stroke = 2.dp.toPx()
        repeat(3) { index ->
            val y = size.height * (0.25f + index * 0.25f)
            drawLine(color, Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), stroke, StrokeCap.Round)
            val x = if ((index + if (expanded) 1 else 0) % 2 == 0) size.width * 0.35f else size.width * 0.66f
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun CurrentLocationIcon(active: Boolean, modifier: Modifier = Modifier) {
    val iconColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.27f
        drawCircle(iconColor, radius, center, style = Stroke(2.dp.toPx()))
        drawCircle(iconColor, radius * 0.32f, center)
        val gap = radius * 1.25f
        drawLine(iconColor, Offset(center.x, 0f), Offset(center.x, center.y - gap), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(center.x, center.y + gap), Offset(center.x, size.height), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(0f, center.y), Offset(center.x - gap, center.y), 2.dp.toPx(), StrokeCap.Round)
        drawLine(iconColor, Offset(center.x + gap, center.y), Offset(size.width, center.y), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun MapToolButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String = label,
) {
    val interactionModifier = if (description == label) {
        Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Surface(
        modifier = Modifier
            .size(52.dp)
            .then(interactionModifier)
            .semantics { contentDescription = description },
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = if (label.length == 1) 24.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FloatingNavigation(
    selected: HomeDestination,
    onSelected: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "沉浸式底部导航" },
        color = Color(0xFAFFFFFF),
        shape = RectangleShape,
        border = BorderStroke(1.dp, Color(0xFFDCE7F5)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 4.dp),
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
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        onClick = onClick,
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeDestinationIcon(
                label = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 3.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(50),
                    ),
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

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
