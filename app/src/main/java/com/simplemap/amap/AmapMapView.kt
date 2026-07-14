package com.simplemap.amap

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

class AmapMapController internal constructor(private val map: AMap) {
    private var selectedPlaceMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private val routeMarkers = mutableListOf<Marker>()

    fun setTrafficEnabled(enabled: Boolean) {
        map.isTrafficEnabled = enabled
    }

    fun setSatelliteEnabled(enabled: Boolean) {
        map.mapType = if (enabled) AMap.MAP_TYPE_SATELLITE else AMap.MAP_TYPE_NORMAL
    }

    fun setMyLocationEnabled(enabled: Boolean) {
        if (enabled) {
            map.myLocationStyle = MyLocationStyle()
                .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
                .interval(2_000L)
                .strokeWidth(1f)
                .strokeColor(0xFF1769E0.toInt())
                .radiusFillColor(0x221769E0)
        }
        map.isMyLocationEnabled = enabled
    }

    fun zoomIn() = map.animateCamera(CameraUpdateFactory.zoomIn())

    fun zoomOut() = map.animateCamera(CameraUpdateFactory.zoomOut())

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
    ) {
        clearRoute()
        if (points.size < 2) return
        val positions = points.map { LatLng(it.latitude, it.longitude) }
        routePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(positions)
                .width(14f)
                .color(0xFF1769E0.toInt())
                .zIndex(10f),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.first())
                .title("起点")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
        )
        routeMarkers += map.addMarker(
            MarkerOptions()
                .position(positions.last())
                .title("终点")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
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

    fun clearRoute() {
        routePolyline?.remove()
        routePolyline = null
        routeMarkers.forEach(Marker::remove)
        routeMarkers.clear()
    }
}

@Composable
fun AmapMapView(
    modifier: Modifier = Modifier,
    onControllerReady: (AmapMapController) -> Unit = {},
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

    LaunchedEffect(controller) {
        currentOnControllerReady(controller)
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