package com.simplemap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simplemap.amap.AmapMapController
import com.simplemap.amap.AmapPerspectiveMode
import com.simplemap.amap.calculateMapScale
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.search.Place
import com.simplemap.settings.currentMinuteOfDay

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
