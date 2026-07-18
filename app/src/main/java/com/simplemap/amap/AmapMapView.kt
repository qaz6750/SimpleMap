package com.simplemap.amap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
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
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.simplemap.route.RoutePoint
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteTrafficSegment
import com.simplemap.route.RouteTrafficStatus

class AmapMapController internal constructor(private val map: AMap) {
    private var selectedPlaceMarker: Marker? = null
    private val routePolylines = mutableListOf<Polyline>()
    private val routeTrafficPolylines = mutableListOf<Polyline>()
    private val routeMarkers = mutableListOf<Marker>()
    private var locationEnabled = false
    private var routeOverviewActive = false
    private var satelliteEnabled = false
    private var nightModeEnabled = false

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

    fun setMyLocationEnabled(enabled: Boolean) {
        locationEnabled = enabled
        if (enabled) {
            applyMyLocationStyle()
        }
        map.isMyLocationEnabled = enabled
    }

    private fun applyMyLocationStyle() {
        map.myLocationStyle = MyLocationStyle()
            .myLocationType(
                if (routeOverviewActive) {
                    MyLocationStyle.LOCATION_TYPE_SHOW
                } else {
                    MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE
                },
            )
            .interval(2_000L)
            .myLocationIcon(createCurrentLocationIcon())
            .anchor(0.5f, 0.58f)
            .strokeWidth(1f)
            .strokeColor(0xFF1769E0.toInt())
            .radiusFillColor(0x221769E0)
    }

    fun zoomIn() = map.animateCamera(CameraUpdateFactory.zoomIn())

    fun zoomOut() = map.animateCamera(CameraUpdateFactory.zoomOut())

    fun cameraCenter(): LatLng = map.cameraPosition.target

    fun moveToCurrentLocation() {
        map.myLocation?.let { location ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    17f,
                ),
                420L,
                null,
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
        routeOverviewActive = true
        if (locationEnabled) applyMyLocationStyle()
        val positions = points.map { LatLng(it.latitude, it.longitude) }
        routePolylines += map.addPolyline(
            PolylineOptions()
            .addAll(positions)
            .width(22f)
            .color(0xE6FFFFFF.toInt())
            .zIndex(9f),
        )
        routePolylines += map.addPolyline(
            PolylineOptions()
                .addAll(positions)
                .width(14f)
                .color(0xFF1769E0.toInt())
                .zIndex(10f),
        )
        trafficSegments.forEach { segment ->
            routeTrafficPolylines += map.addPolyline(
                PolylineOptions()
                    .addAll(segment.polyline.map { LatLng(it.latitude, it.longitude) })
                    .width(9f)
                    .color(segment.status.routeColor())
                    .zIndex(11f),
            )
        }
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.first())
                .title("起点")
                .anchor(0.5f, 0.5f)
                .icon(createRouteEndpointIcon("起", 0xFF1769E0.toInt())),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.last())
                .title("终点")
                .anchor(0.5f, 0.5f)
                .icon(createRouteEndpointIcon("终", 0xFFE84F3D.toInt())),
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
        clearRoute()
        val visiblePlans = plans.filter { it.polyline.size >= 2 }.take(3)
        if (visiblePlans.isEmpty()) return
        routeOverviewActive = true
        if (locationEnabled) applyMyLocationStyle()

        visiblePlans.sortedBy { it.id == selectedPlanId }.forEachIndexed { index, plan ->
            val selected = plan.id == selectedPlanId
            val positions = plan.polyline.map { LatLng(it.latitude, it.longitude) }
            if (selected) {
                routePolylines += map.addPolyline(
                    PolylineOptions()
                        .addAll(positions)
                        .width(22f)
                        .color(0xE6FFFFFF.toInt())
                        .zIndex(19f),
                )
            }
            routePolylines += map.addPolyline(
                PolylineOptions()
                    .addAll(positions)
                    .width(if (selected) 14f else 10f)
                    .color(if (selected) 0xFF1769E0.toInt() else alternativeRouteColor(index))
                    .zIndex(if (selected) 20f else 8f + index),
            )
            if (selected) {
                plan.trafficSegments.forEach { segment ->
                    routeTrafficPolylines += map.addPolyline(
                        PolylineOptions()
                            .addAll(segment.polyline.map { LatLng(it.latitude, it.longitude) })
                            .width(14f)
                            .color(segment.status.routeColor())
                            .zIndex(21f),
                    )
                }
            }
        }

        val selectedPlan = visiblePlans.firstOrNull { it.id == selectedPlanId } ?: visiblePlans.first()
        val selectedPositions = selectedPlan.polyline.map { LatLng(it.latitude, it.longitude) }
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(selectedPositions.first())
                .title("起点")
                .anchor(0.5f, 0.5f)
                .icon(createRouteEndpointIcon("起", 0xFF1769E0.toInt())),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(selectedPositions.last())
                .title("终点")
                .anchor(0.5f, 0.5f)
                .icon(createRouteEndpointIcon("终", 0xFFE84F3D.toInt())),
        )
        val bounds = LatLngBounds.builder().apply {
            visiblePlans.flatMap(RoutePlan::polyline).forEach { point ->
                include(LatLng(point.latitude, point.longitude))
            }
        }.build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBoundsRect(bounds, leftInsetPx, 72, topInsetPx, bottomInsetPx),
            450L,
            null,
        )
    }

    fun clearRoute() {
        routePolylines.forEach(Polyline::remove)
        routePolylines.clear()
        routeTrafficPolylines.forEach(Polyline::remove)
        routeTrafficPolylines.clear()
        routeMarkers.forEach(Marker::remove)
        routeMarkers.clear()
        if (routeOverviewActive) {
            routeOverviewActive = false
            if (locationEnabled) applyMyLocationStyle()
        }
    }
}

private fun alternativeRouteColor(index: Int): Int = when (index % 2) {
    0 -> 0xFF4D7FAE.toInt()
    else -> 0xFF6D8F76.toInt()
}

private fun RouteTrafficStatus.routeColor(): Int = when (this) {
    RouteTrafficStatus.Smooth -> 0xFF24A866.toInt()
    RouteTrafficStatus.Slow -> 0xFFF2B134.toInt()
    RouteTrafficStatus.Congested -> 0xFFF07B32.toInt()
    RouteTrafficStatus.SeverelyCongested -> 0xFFD83A3A.toInt()
    RouteTrafficStatus.Unknown -> 0xFF1769E0.toInt()
}

private fun createCurrentLocationIcon() = BitmapDescriptorFactory.fromBitmap(
    Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x55FFFFFF
        canvas.drawCircle(36f, 36f, 33f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(36f, 36f, 20f, paint)
        paint.color = 0xFF1769E0.toInt()
        canvas.drawCircle(36f, 36f, 14f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(32f, 32f, 4f, paint)
    },
)

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
                isScaleControlsEnabled = true
            }
        }
    }
    val controller = remember(mapView) { AmapMapController(mapView.map) }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)
    val currentOnControllerReleased by rememberUpdatedState(onControllerReleased)
    val currentOnLocationChanged by rememberUpdatedState(onLocationChanged)

    LaunchedEffect(controller) {
        currentOnControllerReady(controller)
    }

    DisposableEffect(controller) {
        onDispose { currentOnControllerReleased(controller) }
    }

    DisposableEffect(mapView) {
        mapView.map.setOnMyLocationChangeListener { location ->
            currentOnLocationChanged(location)
        }
        onDispose {
            mapView.map.setOnMyLocationChangeListener(null)
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