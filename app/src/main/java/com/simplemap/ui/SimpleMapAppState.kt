package com.simplemap.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simplemap.amap.AmapMapController
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.calculateMapScale
import com.simplemap.offline.AmapOfflineMapRepository
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.route.AmapRoutePlanRepository
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.search.AmapPlaceRepository
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.search.SharedPreferencesFavoritePlaceStore
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.SharedPreferencesNavigationSettingsStore
import com.simplemap.settings.currentMinuteOfDay
import com.simplemap.trips.ParkingLocationStore
import com.simplemap.trips.SharedPreferencesParkingLocationStore
import com.simplemap.trips.SharedPreferencesTripHistoryStore
import com.simplemap.trips.TripHistoryStore
import com.simplemap.update.AppUpdateRepository
import com.simplemap.update.GitHubReleaseUpdateRepository

/**
 * Holds the top-level UI state of [SimpleMapApp] so the composable body stays
 * focused on wiring instead of state declarations.
 */
internal class SimpleMapAppState {
    var mapController by mutableStateOf<AmapMapController?>(null)
    var selectedDestination by mutableStateOf(HomeDestination.Map)
    var selectedPlace by mutableStateOf<Place?>(null)
    var routeDestination by mutableStateOf<Place?>(null)
    var routeInitialMode by mutableStateOf(RouteMode.Drive)
    var selectedRoutePlan by mutableStateOf<RoutePlan?>(null)
    var routePlans by mutableStateOf<List<RoutePlan>>(emptyList())
    var parkingLocation by mutableStateOf<Place?>(null)
    var favoritePlaceIds by mutableStateOf<Set<String>>(emptySet())
    var satelliteEnabled by mutableStateOf(false)
    var mapPerspectiveMode by mutableStateOf(AmapPerspectiveMode.TwoDimensional)
    var mapScale by mutableStateOf(calculateMapScale(zoom = 16f, latitude = 30.0, targetWidthPixels = 96f))
    var locationEnabled by mutableStateOf(false)
    var minuteOfDay by mutableIntStateOf(currentMinuteOfDay())
    var currentLocation by mutableStateOf<Place?>(null)
    var mapToolsExpanded by mutableStateOf(false)
}

/** Resolved repositories and stores used by [SimpleMapApp]. */
internal class SimpleMapDependencies(
    val repository: PlaceRepository,
    val favoriteStore: FavoritePlaceStore,
    val routeRepository: RoutePlanRepository,
    val tripStore: TripHistoryStore,
    val parkingStore: ParkingLocationStore,
    val settingsStore: NavigationSettingsStore,
    val updateRepository: AppUpdateRepository,
    val resolvedOfflineRepository: Result<OfflineMapRepository>,
)

@Composable
internal fun rememberSimpleMapDependencies(
    placeRepository: PlaceRepository?,
    favoritePlaceStore: FavoritePlaceStore?,
    routePlanRepository: RoutePlanRepository?,
    tripHistoryStore: TripHistoryStore?,
    parkingLocationStore: ParkingLocationStore?,
    navigationSettingsStore: NavigationSettingsStore?,
    appUpdateRepository: AppUpdateRepository?,
    offlineMapRepository: OfflineMapRepository?,
): SimpleMapDependencies {
    val context: Context = LocalContext.current
    return remember(
        context,
        placeRepository,
        favoritePlaceStore,
        routePlanRepository,
        tripHistoryStore,
        parkingLocationStore,
        navigationSettingsStore,
        appUpdateRepository,
        offlineMapRepository,
    ) {
        SimpleMapDependencies(
            repository = placeRepository ?: AmapPlaceRepository(context),
            favoriteStore = favoritePlaceStore ?: SharedPreferencesFavoritePlaceStore(context),
            routeRepository = routePlanRepository ?: AmapRoutePlanRepository(context),
            tripStore = tripHistoryStore ?: SharedPreferencesTripHistoryStore(context),
            parkingStore = parkingLocationStore ?: SharedPreferencesParkingLocationStore(context),
            settingsStore = navigationSettingsStore ?: SharedPreferencesNavigationSettingsStore(context),
            updateRepository = appUpdateRepository ?: GitHubReleaseUpdateRepository(),
            resolvedOfflineRepository = offlineMapRepository?.let { Result.success(it) }
                ?: runCatching { AmapOfflineMapRepository(context) },
        )
    }
}
