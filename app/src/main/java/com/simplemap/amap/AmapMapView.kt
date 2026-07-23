package com.simplemap.amap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.content.Context
import android.location.Location
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.navi.enums.TrafficStatus
import com.amap.api.navi.model.AMapNaviLink
import com.amap.api.navi.model.AMapNaviPath
import com.amap.api.navi.model.AMapNaviStep
import com.amap.api.navi.model.AMapTrafficStatus
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.model.NaviPath
import com.amap.api.navi.view.RouteOverLay
import com.simplemap.route.RoutePoint
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteTrafficSegment
import com.simplemap.route.RouteTrafficStatus
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private const val CURRENT_LOCATION_ZOOM = 19f

enum class AmapCameraMode {
    FollowMyLocation,
    FreeBrowse,
    RouteOverview,
}

enum class AmapPerspectiveMode {
    TwoDimensional,
    ThreeDimensional,
}

internal data class AmapCameraOrientation(
    val tilt: Float,
    val bearing: Float,
)

internal fun applyPerspectiveMode(
    orientation: AmapCameraOrientation,
    mode: AmapPerspectiveMode,
): AmapCameraOrientation = orientation.copy(
    tilt = when (mode) {
        AmapPerspectiveMode.TwoDimensional -> 0f
        AmapPerspectiveMode.ThreeDimensional -> 45f
    },
)

internal fun resetCameraNorth(orientation: AmapCameraOrientation): AmapCameraOrientation =
    orientation.copy(bearing = 0f)

data class MapScale(
    val distanceMeters: Int,
    val widthPixels: Float,
)

internal fun calculateMapScale(
    zoom: Float,
    latitude: Double,
    targetWidthPixels: Float,
): MapScale {
    val metersPerPixel = 156_543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom.toDouble())
    val targetDistance = (metersPerPixel * targetWidthPixels.coerceAtLeast(1f)).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(targetDistance)))
    val normalized = targetDistance / magnitude
    val friendly = when {
        normalized >= 5 -> 5.0
        normalized >= 2 -> 2.0
        else -> 1.0
    }
    val distance = (friendly * magnitude).toInt().coerceAtLeast(1)
    return MapScale(distance, (distance / metersPerPixel).toFloat().coerceAtLeast(1f))
}

internal data class AmapCameraPolicyState(
    val showsMyLocationMarker: Boolean = false,
    val mode: AmapCameraMode = AmapCameraMode.FollowMyLocation,
) {
    val automaticallyFollowsMyLocation: Boolean
        get() = showsMyLocationMarker && mode == AmapCameraMode.FollowMyLocation
}

internal object AmapCameraPolicy {
    fun setMyLocationMarkerVisible(
        state: AmapCameraPolicyState,
        visible: Boolean,
    ): AmapCameraPolicyState = state.copy(showsMyLocationMarker = visible)

    fun enterFreeBrowse(state: AmapCameraPolicyState): AmapCameraPolicyState =
        state.copy(mode = AmapCameraMode.FreeBrowse)

    fun showRouteOverview(state: AmapCameraPolicyState): AmapCameraPolicyState =
        state.copy(mode = AmapCameraMode.RouteOverview)

    fun clearRouteOverview(state: AmapCameraPolicyState): AmapCameraPolicyState =
        if (state.mode == AmapCameraMode.RouteOverview) {
            state.copy(mode = AmapCameraMode.FreeBrowse)
        } else {
            state
        }

    fun restoreFollow(state: AmapCameraPolicyState): AmapCameraPolicyState =
        state.copy(mode = AmapCameraMode.FollowMyLocation)
}

class AmapMapController internal constructor(
    private val context: Context,
    private val map: AMap,
) {
    private var selectedPlaceMarker: Marker? = null
    private val routeOverlays = mutableListOf<RouteOverLay>()
    private val routePlanOverlays = linkedMapOf<String, RouteOverLay>()
    private var displayedRoutePlans: List<RoutePlan> = emptyList()
    private var displayedRouteSelectionId: String? = null
    private var displayedRouteInsets: List<Int> = emptyList()
    private val routeMarkers = mutableListOf<Marker>()
    private var cameraPolicyState = AmapCameraPolicyState()
    private var satelliteEnabled = false
    private var nightModeEnabled = false
    private val endpointIcons = mutableMapOf<Pair<String, Int>, BitmapDescriptor>()
    private var currentLocationIcon: BitmapDescriptor? = null
    private var pendingLocationCenterZoom: Float? = null

    val cameraMode: AmapCameraMode
        get() = cameraPolicyState.mode

    val showsMyLocationMarker: Boolean
        get() = cameraPolicyState.showsMyLocationMarker

    fun setTrafficEnabled(enabled: Boolean) {
        map.isTrafficEnabled = enabled
    }

    fun setSatelliteEnabled(enabled: Boolean) {
        satelliteEnabled = enabled
        applyMapType()
    }

    fun setNightMode(enabled: Boolean) {
        nightModeEnabled = enabled
        applyMapType()
    }

    private fun applyMapType() {
        map.mapType = when {
            satelliteEnabled -> AMap.MAP_TYPE_SATELLITE
            nightModeEnabled -> AMap.MAP_TYPE_NIGHT
            else -> AMap.MAP_TYPE_NORMAL
        }
    }

    fun setMyLocationEnabled(enabled: Boolean) = setMyLocationMarkerVisible(enabled)

    fun setMyLocationMarkerVisible(visible: Boolean) {
        updateCameraPolicy(AmapCameraPolicy.setMyLocationMarkerVisible(cameraPolicyState, visible))
    }

    private fun applyMyLocationStyle() {
        if (!cameraPolicyState.showsMyLocationMarker) return
        map.myLocationStyle = MyLocationStyle()
            .myLocationType(
                if (cameraPolicyState.automaticallyFollowsMyLocation) {
                    MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER
                } else {
                    MyLocationStyle.LOCATION_TYPE_SHOW
                },
            )
            .interval(2_000L)
            .apply {
                (currentLocationIcon ?: loadAmapNavigationLocationIcon(context))?.let { icon ->
                    myLocationIcon(icon.also { currentLocationIcon = it })
                }
            }
            .anchor(0.5f, 0.58f)
            .strokeWidth(1f)
            .strokeColor(0xFF1466D8.toInt())
            .radiusFillColor(0x221466D8)
    }

    fun zoomIn() = map.animateCamera(CameraUpdateFactory.zoomIn())

    fun zoomOut() = map.animateCamera(CameraUpdateFactory.zoomOut())

    fun setPerspectiveMode(mode: AmapPerspectiveMode) {
        updateCameraOrientation { orientation -> applyPerspectiveMode(orientation, mode) }
    }

    fun resetNorth() {
        updateCameraOrientation(::resetCameraNorth)
    }

    fun cameraCenter(): LatLng = map.cameraPosition.target

    private fun updateCameraOrientation(
        transform: (AmapCameraOrientation) -> AmapCameraOrientation,
    ) {
        val current = map.cameraPosition
        val next = transform(AmapCameraOrientation(current.tilt, current.bearing))
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition(current.target, current.zoom, next.tilt, next.bearing),
            ),
            300L,
            null,
        )
    }

    fun moveToCurrentLocation() {
        if (!cameraPolicyState.automaticallyFollowsMyLocation) return
        centerCameraOnCurrentLocation()
    }

    fun enterFreeBrowseMode() {
        updateCameraPolicy(AmapCameraPolicy.enterFreeBrowse(cameraPolicyState))
    }

    fun restoreCameraFollow() {
        updateCameraPolicy(AmapCameraPolicy.restoreFollow(cameraPolicyState))
    }

    fun centerOnCurrentLocationAndFollow(zoom: Float = CURRENT_LOCATION_ZOOM) {
        restoreCameraFollow()
        centerCameraOnCurrentLocation(zoom)
    }

    internal fun onMyLocationChanged(location: Location) {
        if (!cameraPolicyState.automaticallyFollowsMyLocation) return
        val zoom = pendingLocationCenterZoom
        if (zoom != null) {
            pendingLocationCenterZoom = null
            animateCameraToLocation(location, zoom)
        } else {
            map.moveCamera(
                CameraUpdateFactory.changeLatLng(LatLng(location.latitude, location.longitude)),
            )
        }
    }

    fun showPlace(
        latitude: Double,
        longitude: Double,
        title: String,
        snippet: String,
    ) {
        val position = LatLng(latitude, longitude)
        selectedPlaceMarker?.remove()
        selectedPlaceMarker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .snippet(snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
        ).apply { showInfoWindow() }
        enterFreeBrowseMode()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f), 450L, null)
    }

    fun clearSelectedPlace() {
        selectedPlaceMarker?.remove()
        selectedPlaceMarker = null
    }

    fun showRoute(
        points: List<RoutePoint>,
        topInsetPx: Int = 120,
        bottomInsetPx: Int = 120,
        trafficSegments: List<RouteTrafficSegment> = emptyList(),
    ) {
        clearRoute()
        if (points.size < 2) return
        updateCameraPolicy(AmapCameraPolicy.showRouteOverview(cameraPolicyState))
        val positions = points.map { LatLng(it.latitude, it.longitude) }
        addNavigationRouteOverlay(
            points = points,
            trafficSegments = trafficSegments,
            selected = true,
            zIndex = 20,
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.first())
                .title("起点")
                .anchor(0.5f, 0.5f)
                .icon(routeEndpointIcon("起", 0xFF1466D8.toInt())),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.last())
                .title("终点")
                .anchor(0.5f, 0.5f)
                .icon(routeEndpointIcon("终", 0xFFE84F3D.toInt())),
        )
        val bounds = LatLngBounds.builder().apply {
            positions.forEach(::include)
        }.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBoundsRect(
                bounds,
                72,
                72,
                topInsetPx,
                bottomInsetPx,
            ),
            450L,
            null,
        )
    }

    fun showRoutes(
        plans: List<RoutePlan>,
        selectedPlanId: String?,
        topInsetPx: Int = 120,
        bottomInsetPx: Int = 120,
        leftInsetPx: Int = 72,
    ) {
        val visiblePlans = plans.filter { it.polyline.size >= 2 }.take(3)
        if (visiblePlans.isEmpty()) {
            clearRoute()
            return
        }
        val resolvedSelectionId = selectedPlanId
            ?.takeIf { selectedId -> visiblePlans.any { it.id == selectedId } }
            ?: visiblePlans.first().id
        if (hasSameDisplayedRoutePlans(visiblePlans)) {
            if (displayedRouteSelectionId != resolvedSelectionId) {
                updateDisplayedRouteSelection(visiblePlans, resolvedSelectionId)
            }
            val insets = listOf(leftInsetPx, topInsetPx, bottomInsetPx)
            if (displayedRouteInsets != insets) {
                displayedRouteInsets = insets
                fitRoutePlans(visiblePlans, leftInsetPx, topInsetPx, bottomInsetPx)
            }
            return
        }

        clearRoute()
        updateCameraPolicy(AmapCameraPolicy.showRouteOverview(cameraPolicyState))

        visiblePlans.sortedBy { it.id == resolvedSelectionId }.forEachIndexed { index, plan ->
            val selected = plan.id == resolvedSelectionId
            val overlay = addNavigationRouteOverlay(
                points = plan.polyline,
                trafficSegments = plan.trafficSegments,
                selected = selected,
                zIndex = if (selected) 20 else 8 + index,
            )
            if (overlay != null) routePlanOverlays[plan.id] = overlay
        }
        displayedRoutePlans = visiblePlans
        displayedRouteSelectionId = resolvedSelectionId
        displayedRouteInsets = listOf(leftInsetPx, topInsetPx, bottomInsetPx)

        val selectedPlan = visiblePlans.first { it.id == resolvedSelectionId }
        val selectedPositions = selectedPlan.polyline.map { LatLng(it.latitude, it.longitude) }
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(selectedPositions.first())
                .title("起点")
                .anchor(0.5f, 0.5f)
                .icon(routeEndpointIcon("起", 0xFF1466D8.toInt())),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(selectedPositions.last())
                .title("终点")
                .anchor(0.5f, 0.5f)
                .icon(routeEndpointIcon("终", 0xFFE84F3D.toInt())),
        )
        fitRoutePlans(visiblePlans, leftInsetPx, topInsetPx, bottomInsetPx)
    }

    fun clearRoute() {
        routeOverlays.forEach { overlay ->
            overlay.removeFromMap()
            overlay.destroy()
        }
        routeOverlays.clear()
        routePlanOverlays.clear()
        displayedRoutePlans = emptyList()
        displayedRouteSelectionId = null
        displayedRouteInsets = emptyList()
        routeMarkers.forEach(Marker::remove)
        routeMarkers.clear()
        updateCameraPolicy(AmapCameraPolicy.clearRouteOverview(cameraPolicyState))
    }

    private fun addNavigationRouteOverlay(
        points: List<RoutePoint>,
        trafficSegments: List<RouteTrafficSegment>,
        selected: Boolean,
        zIndex: Int,
    ): RouteOverLay? {
        val path = createAmapNavigationPath(points, trafficSegments) ?: return null
        val overlay = RouteOverLay(map, path, context).apply {
            setRouteOverlayOptions(amapNavigationRouteOverlayOptions())
            showStartMarker(false)
            showEndMarker(false)
            showViaMarker(false)
            showRouteStart(false)
            showRouteEnd(false)
            setArrowOnRoute(true)
            setTransparency(if (selected) 1f else 0.52f)
            setZindex(zIndex)
        }
        val showTraffic = trafficSegments.isNotEmpty()
        if (overlay.isTrafficLine == showTraffic) {
            overlay.addToMap()
        } else {
            overlay.setTrafficLine(showTraffic)
        }
        routeOverlays += overlay
        return overlay
    }

    private fun hasSameDisplayedRoutePlans(plans: List<RoutePlan>): Boolean =
        displayedRoutePlans.size == plans.size &&
            displayedRoutePlans.zip(plans).all { (displayed, current) -> displayed === current } &&
            routePlanOverlays.size == plans.size

    private fun updateDisplayedRouteSelection(
        plans: List<RoutePlan>,
        selectedPlanId: String,
    ) {
        plans.forEachIndexed { index, plan ->
            val selected = plan.id == selectedPlanId
            routePlanOverlays[plan.id]?.apply {
                setTransparency(if (selected) 1f else 0.52f)
                setZindex(if (selected) 20 else 8 + index)
            }
        }
        displayedRouteSelectionId = selectedPlanId
    }

    private fun fitRoutePlans(
        plans: List<RoutePlan>,
        leftInsetPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
    ) {
        val bounds = LatLngBounds.builder().apply {
            plans.forEach { plan ->
                plan.polyline.forEach { point ->
                    include(LatLng(point.latitude, point.longitude))
                }
            }
        }.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBoundsRect(bounds, leftInsetPx, 72, topInsetPx, bottomInsetPx),
            450L,
            null,
        )
    }

    private fun routeEndpointIcon(label: String, color: Int) =
        endpointIcons.getOrPut(label to color) { createRouteEndpointIcon(label, color) }

    private fun updateCameraPolicy(nextState: AmapCameraPolicyState) {
        if (cameraPolicyState == nextState) return
        cameraPolicyState = nextState
        applyCameraPolicy()
    }

    private fun applyCameraPolicy() {
        map.isMyLocationEnabled = cameraPolicyState.showsMyLocationMarker
        if (cameraPolicyState.showsMyLocationMarker) {
            applyMyLocationStyle()
        }
    }

    private fun centerCameraOnCurrentLocation(zoom: Float = CURRENT_LOCATION_ZOOM) {
        val location = map.myLocation
        if (location == null) {
            pendingLocationCenterZoom = zoom
            return
        }
        pendingLocationCenterZoom = null
        animateCameraToLocation(location, zoom)
    }

    private fun animateCameraToLocation(location: Location, zoom: Float) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                zoom,
            ),
            420L,
            null,
        )
    }
}

private fun createAmapNavigationPath(
    points: List<RoutePoint>,
    trafficSegments: List<RouteTrafficSegment>,
): AMapNaviPath? {
    if (points.size < 2) return null
    val coordinates = points.map { NaviLatLng(it.latitude, it.longitude) }
    val lengthMeters = routeLengthMeters(points)
    val link = AMapNaviLink().apply {
        setCoords(coordinates)
        setLength(lengthMeters)
        setTrafficStatus(TrafficStatus.UNKNOWN)
    }
    val step = AMapNaviStep().apply {
        setStartIndex(0)
        setEndIndex(coordinates.lastIndex)
        setCoords(coordinates)
        setLinks(listOf(link))
        setLength(lengthMeters)
    }
    return NaviPath().apply {
        setList(coordinates)
        setListStep(listOf(step))
        setStartPoint(coordinates.first())
        setEndPoint(coordinates.last())
        setAllLength(lengthMeters)
        setStepsCount(1)
        if (trafficSegments.isNotEmpty()) {
            setTrafficStatus(createAmapTrafficStatuses(trafficSegments, lengthMeters))
        }
    }.amapNaviPath
}

private fun createAmapTrafficStatuses(
    segments: List<RouteTrafficSegment>,
    routeLengthMeters: Int,
): List<AMapTrafficStatus> {
    var remainingMeters = routeLengthMeters
    return buildList {
        segments.forEach { segment ->
            if (remainingMeters <= 0) return@forEach
            val segmentLength = routeLengthMeters(segment.polyline)
                .coerceAtLeast(1)
                .coerceAtMost(remainingMeters)
            add(
                AMapTrafficStatus().apply {
                    setLinkIndex(0)
                    setStatus(segment.status.toAmapTrafficStatus())
                    setLength(segmentLength)
                },
            )
            remainingMeters -= segmentLength
        }
        if (remainingMeters > 0) {
            add(
                AMapTrafficStatus().apply {
                    setLinkIndex(0)
                    setStatus(TrafficStatus.UNKNOWN)
                    setLength(remainingMeters)
                },
            )
        }
    }
}

private fun routeLengthMeters(points: List<RoutePoint>): Int {
    var distanceMeters = 0f
    val result = FloatArray(1)
    points.zipWithNext().forEach { (start, end) ->
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            result,
        )
        distanceMeters += result[0]
    }
    return distanceMeters.toInt().coerceAtLeast(1)
}

private fun RouteTrafficStatus.toAmapTrafficStatus(): Int = when (this) {
    RouteTrafficStatus.Smooth -> TrafficStatus.SMOOTH
    RouteTrafficStatus.Slow -> TrafficStatus.SLOW
    RouteTrafficStatus.Congested -> TrafficStatus.JAM
    RouteTrafficStatus.SeverelyCongested -> TrafficStatus.VERY_JAM
    RouteTrafficStatus.Unknown -> TrafficStatus.UNKNOWN
}

private fun loadAmapNavigationLocationIcon(context: Context): BitmapDescriptor? =
    runCatching {
        context.assets.open("location_map_gps_3d.png").use(BitmapFactory::decodeStream)
    }.getOrNull()?.let(BitmapDescriptorFactory::fromBitmap)

private fun createRouteEndpointIcon(label: String, color: Int) = BitmapDescriptorFactory.fromBitmap(
    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        canvas.drawCircle(33f, 35f, 25f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(32f, 32f, 25f, paint)
        paint.color = color
        canvas.drawCircle(32f, 32f, 20f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText(label, 32f, 40f, paint)
    },
)

@Composable
fun AmapMapView(
    modifier: Modifier = Modifier,
    onControllerReady: (AmapMapController) -> Unit = {},
    onControllerReleased: (AmapMapController) -> Unit = {},
    onLocationChanged: (Location) -> Unit = {},
    onScaleChanged: (MapScale) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
                isScaleControlsEnabled = false
            }
        }
    }
    val controller = remember(mapView) { AmapMapController(context.applicationContext, mapView.map) }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)
    val currentOnControllerReleased by rememberUpdatedState(onControllerReleased)
    val currentOnLocationChanged by rememberUpdatedState(onLocationChanged)
    val currentOnScaleChanged by rememberUpdatedState(onScaleChanged)

    LaunchedEffect(controller) {
        currentOnControllerReady(controller)
    }

    DisposableEffect(controller) {
        onDispose { currentOnControllerReleased(controller) }
    }

    DisposableEffect(mapView) {
        mapView.map.setOnMyLocationChangeListener { location ->
            controller.onMyLocationChanged(location)
            currentOnLocationChanged(location)
        }
        mapView.map.setOnMapTouchListener { motionEvent ->
            if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE) {
                controller.enterFreeBrowseMode()
            }
        }
        mapView.map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition) = Unit

            override fun onCameraChangeFinish(position: CameraPosition) {
                currentOnScaleChanged(
                    calculateMapScale(
                        zoom = position.zoom,
                        latitude = position.target.latitude,
                        targetWidthPixels = 96f,
                    ),
                )
            }
        })
        val initialPosition = mapView.map.cameraPosition
        currentOnScaleChanged(
            calculateMapScale(initialPosition.zoom, initialPosition.target.latitude, 96f),
        )
        onDispose {
            mapView.map.setOnMyLocationChangeListener(null)
            mapView.map.setOnMapTouchListener(null)
            mapView.map.setOnCameraChangeListener(null)
        }
    }

    DisposableEffect(mapView, lifecycle) {
        var resumed = false
        var destroyed = false

        fun resume() {
            if (!resumed && !destroyed) {
                mapView.onResume()
                resumed = true
            }
        }

        fun pause() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_DESTROY -> {
                    pause()
                    if (!destroyed) {
                        mapView.onDestroy()
                        destroyed = true
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            resume()
        }

        onDispose {
            lifecycle.removeObserver(observer)
            pause()
            if (!destroyed) {
                mapView.onDestroy()
                destroyed = true
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
