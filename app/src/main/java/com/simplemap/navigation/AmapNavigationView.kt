package com.simplemap.navigation

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
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
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewOptions
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.NaviLatLng
import com.simplemap.route.RouteMode
import com.simplemap.search.Place
import androidx.core.content.ContextCompat
import java.lang.reflect.Proxy
import java.util.LinkedHashMap

class AmapNavigationController internal constructor(
    context: Context,
    private val naviView: AMapNaviView,
    voiceGuidance: Boolean,
    private var routeAlerts: Boolean,
) {
    private val navi = AMapNavi.getInstance(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = NavigationUiState()
    private var onStateChanged: (NavigationUiState) -> Unit = {}
    private var onNavigationStarted: () -> Unit = {}
    private var onMapInteractionChanged: (Boolean) -> Unit = {}
    private var pendingRequest: NavigationRequest? = null
    private var started = false
    private var routeRequestAccepted = false
    private var navigationStarted = false
    private var navigationType = NaviType.GPS
    private var destroyed = false
    private val maneuverIconCache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean = size > 32
    }
    private val listener = Proxy.newProxyInstance(
        AMapNaviListener::class.java.classLoader,
        arrayOf(AMapNaviListener::class.java),
    ) { proxy, method, arguments ->
        when (method.name) {
            "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
            "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
            "toString" -> return@newProxyInstance "SimpleMapAmapNaviListener"
            "onInitNaviSuccess" -> if (!routeRequestAccepted) calculatePendingRoute(failIfRejected = true)
            "onInitNaviFailure" -> fail("导航引擎初始化失败")
            "onCalculateRouteSuccess" -> {
                if (!navigationStarted) {
                    navigationStarted = true
                    update(state.copy(phase = NavigationPhase.Navigating, instruction = "路线已就绪"))
                    if (!navi.startNavi(navigationType)) {
                        navigationStarted = false
                        fail("无法启动 GPS 导航")
                    }
                }
            }
            "onCalculateRouteFailure" -> fail("路线计算失败，请检查网络后重试")
            "onStartNavi" -> {
                update(state.copy(phase = NavigationPhase.Navigating))
                mainHandler.post { if (!destroyed) onNavigationStarted() }
            }
            "onNaviInfoUpdate" -> (arguments?.firstOrNull() as? NaviInfo)?.let(::onNaviInfo)
            "onGetNavigationText" -> {
                val text = arguments?.lastOrNull() as? String
                if (!text.isNullOrBlank()) update(state.copy(instruction = text))
            }
            "onReCalculateRouteForYaw" -> if (routeAlerts) {
                update(state.copy(phase = NavigationPhase.Calculating, message = "已偏离路线，正在重新规划"))
            }
            "onReCalculateRouteForTrafficJam" -> if (routeAlerts) {
                update(state.copy(phase = NavigationPhase.Calculating, message = "前方拥堵，正在寻找更优路线"))
            }
            "onGpsOpenStatus" -> update(state.copy(gpsAvailable = arguments?.firstOrNull() == true))
            "onGpsSignalWeak" -> update(state.copy(gpsAvailable = arguments?.firstOrNull() != true))
            "updateCameraInfo" -> {
                val camera = (arguments?.firstOrNull() as? Array<*>)
                    ?.filterIsInstance<AMapNaviCameraInfo>()
                    ?.minByOrNull(AMapNaviCameraInfo::getCameraDistance)
                update(
                    state.copy(
                        speedLimitKmh = camera?.cameraSpeed?.takeIf { it > 0 },
                        cameraDistanceMeters = camera?.cameraDistance?.takeIf { it >= 0 },
                        intervalAverageSpeedKmh = null,
                        intervalRemainingMeters = null,
                    ),
                )
            }
            "updateIntervalCameraInfo" -> {
                val start = arguments?.getOrNull(0) as? AMapNaviCameraInfo
                val remaining = arguments?.getOrNull(2) as? Int
                update(
                    state.copy(
                        speedLimitKmh = start?.cameraSpeed?.takeIf { it > 0 },
                        cameraDistanceMeters = null,
                        intervalAverageSpeedKmh = start?.averageSpeed?.takeIf { it >= 0 },
                        intervalRemainingMeters = remaining?.takeIf { it >= 0 },
                    ),
                )
            }
            "onServiceAreaUpdate" -> {
                val serviceAreas = (arguments?.firstOrNull() as? Array<*>)
                    ?.filterIsInstance<AMapServiceAreaInfo>()
                    ?.asSequence()
                    ?.filter { it.remainDist >= 0 && it.name.isNotBlank() }
                    ?.sortedBy(AMapServiceAreaInfo::getRemainDist)
                    ?.take(2)
                    ?.map {
                        NavigationServiceArea(
                            name = it.name,
                            distanceMeters = it.remainDist,
                            remainingTimeSeconds = it.remainTime.coerceAtLeast(0),
                        )
                    }
                    ?.toList()
                    .orEmpty()
                update(
                    state.copy(
                        serviceAreas = serviceAreas,
                    ),
                )
            }
            "onArriveDestination", "onEndEmulatorNavi" -> update(
                state.copy(
                    phase = NavigationPhase.Arrived,
                    instruction = "已到达目的地",
                    remainingDistanceMeters = 0,
                    remainingTimeSeconds = 0,
                    cameraDistanceMeters = null,
                    intervalAverageSpeedKmh = null,
                    intervalRemainingMeters = null,
                    serviceAreas = emptyList(),
                ),
            )
        }
        null
    } as AMapNaviListener

    init {
        navi.addAMapNaviListener(listener)
        navi.setUseInnerVoice(voiceGuidance, true)
        naviView.setOnMapTouchListener { event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                onMapInteractionChanged(true)
            }
        }
    }

    fun setOnStateChanged(callback: (NavigationUiState) -> Unit) {
        onStateChanged = callback
        callback(state)
    }

    fun setOnNavigationStarted(callback: () -> Unit) {
        onNavigationStarted = callback
    }

    fun setOnMapInteractionChanged(callback: (Boolean) -> Unit) {
        onMapInteractionChanged = callback
    }

    fun start(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        simulated: Boolean = false,
    ) {
        if (started) return
        started = true
        navigationType = if (simulated) NaviType.EMULATOR else NaviType.GPS
        pendingRequest = NavigationRequest(origin, destination, mode)
        update(state.copy(phase = NavigationPhase.Calculating, instruction = "正在计算导航路线"))
        calculatePendingRoute(failIfRejected = false)
    }

    fun overview() = naviView.displayOverview()

    fun setVoiceGuidance(enabled: Boolean) {
        navi.setUseInnerVoice(enabled, true)
    }

    fun setTrafficLayer(enabled: Boolean) {
        naviView.viewOptions = naviView.viewOptions.apply {
            isTrafficLayerEnabled = enabled
            isTrafficLine = enabled
        }
    }

    fun setRouteAlerts(enabled: Boolean) {
        routeAlerts = enabled
    }

    fun updateSatelliteStatus(status: NavigationSatelliteStatus) {
        update(state.copy(satelliteStatus = status))
    }

    fun recoverFollowing() {
        naviView.recoverLockMode()
        onMapInteractionChanged(false)
    }

    fun stop() {
        navi.stopNavi()
        started = false
        routeRequestAccepted = false
        navigationStarted = false
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        onStateChanged = {}
        onNavigationStarted = {}
        onMapInteractionChanged = {}
        maneuverIconCache.clear()
        navi.removeAMapNaviListener(listener)
        navi.stopNavi()
        AMapNavi.destroy()
    }

    private fun calculatePendingRoute(failIfRejected: Boolean) {
        val request = pendingRequest ?: return
        val origin = NaviLatLng(request.origin.latitude, request.origin.longitude)
        val destination = NaviLatLng(request.destination.latitude, request.destination.longitude)
        val accepted = when (request.mode) {
            RouteMode.Drive -> navi.calculateDriveRoute(
                listOf(origin),
                listOf(destination),
                navi.strategyConvert(false, false, false, false, true),
            )
            RouteMode.Ride -> navi.calculateRideRoute(origin, destination)
            RouteMode.Walk -> navi.calculateWalkRoute(origin, destination)
            RouteMode.Transit -> false
        }
        routeRequestAccepted = accepted
        if (!accepted && (failIfRejected || request.mode == RouteMode.Transit)) {
            fail(
                if (request.mode == RouteMode.Transit) {
                    "公交方案暂不支持实时 GPS 导航"
                } else {
                    "导航路线请求未被接受，请稍后重试"
                },
            )
        }
    }

    private fun onNaviInfo(info: NaviInfo) {
        val maneuverIconBitmap = info.iconBitmap
            ?.takeUnless(Bitmap::isRecycled)
            ?.copy(Bitmap.Config.ARGB_8888, false)
            ?.also { maneuverIconCache[info.iconType] = it }
            ?: maneuverIconCache[info.iconType]
        update(
            state.copy(
                phase = NavigationPhase.Navigating,
                currentRoad = info.currentRoadName.orEmpty(),
                nextRoad = info.nextRoadName.orEmpty(),
                maneuverIconType = info.iconType,
                maneuverIconBitmap = maneuverIconBitmap,
                maneuverDistanceMeters = info.curStepRetainDistance.coerceAtLeast(0),
                remainingDistanceMeters = info.pathRetainDistance.coerceAtLeast(0),
                remainingTimeSeconds = info.pathRetainTime.coerceAtLeast(0),
                currentSpeedKmh = info.currentSpeed.coerceAtLeast(0),
                remainingTrafficLights = info.routeRemainLightCount.coerceAtLeast(0),
                message = null,
            ),
        )
    }

    private fun fail(message: String) {
        update(
            state.copy(
                phase = NavigationPhase.Failed,
                message = message,
                instruction = message,
                cameraDistanceMeters = null,
                intervalAverageSpeedKmh = null,
                intervalRemainingMeters = null,
                serviceAreas = emptyList(),
            ),
        )
    }

    private fun update(newState: NavigationUiState) {
        if (destroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state = newState
            onStateChanged(newState)
        } else {
            mainHandler.post {
                if (destroyed) return@post
                state = newState
                onStateChanged(newState)
            }
        }
    }

    private data class NavigationRequest(
        val origin: Place,
        val destination: Place,
        val mode: RouteMode,
    )
}

@Composable
fun AmapNavigationView(
    onControllerReady: (AmapNavigationController) -> Unit,
    voiceGuidance: Boolean,
    trafficLayer: Boolean,
    routeAlerts: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    simulated: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val naviView = remember(trafficLayer) {
        val options = AMapNaviViewOptions().apply {
            isLayoutVisible = false
            isAutoDrawRoute = true
            isTrafficLayerEnabled = trafficLayer
            isTrafficLine = trafficLayer
            isAutoLockCar = true
            isCompassEnabled = false
            isTrafficBarEnabled = false
            isRouteListButtonShow = false
            isSettingMenuEnabled = false
            isAutoChangeZoom = true
            setPointToCenter(if (isLandscape) 0.64 else 0.5, if (isLandscape) 0.58 else 0.66)
        }
        AMapNaviView(context, options).apply { onCreate(null) }
    }
    val controller = remember(naviView, voiceGuidance, routeAlerts) {
        AmapNavigationController(context, naviView, voiceGuidance, routeAlerts)
    }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)

    LaunchedEffect(controller) { currentOnControllerReady(controller) }
    LaunchedEffect(naviView, isLandscape) {
        naviView.viewOptions = naviView.viewOptions.apply {
            setPointToCenter(if (isLandscape) 0.64 else 0.5, if (isLandscape) 0.58 else 0.66)
        }
    }


    DisposableEffect(context, controller, simulated) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = if (
            !simulated && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var usedInFix = 0
                    var cn0Total = 0f
                    val systems = linkedMapOf<String, Int>()
                    repeat(status.satelliteCount) { index ->
                        if (status.usedInFix(index)) usedInFix++
                        cn0Total += status.getCn0DbHz(index)
                        val system = satelliteSystemLabel(status.getConstellationType(index))
                        systems[system] = (systems[system] ?: 0) + 1
                    }
                    controller.updateSatelliteStatus(
                        NavigationSatelliteStatus(
                            visibleCount = status.satelliteCount,
                            usedInFixCount = usedInFix,
                            averageCn0DbHz = if (status.satelliteCount == 0) {
                                0f
                            } else {
                                cn0Total / status.satelliteCount
                            },
                            systems = systems,
                        ),
                    )
                }
            }.also(locationManager::registerGnssStatusCallback)
        } else {
            null
        }
        onDispose {
            callback?.let(locationManager::unregisterGnssStatusCallback)
        }
    }

    DisposableEffect(naviView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> naviView.onResume()
                Lifecycle.Event.ON_PAUSE -> naviView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) naviView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            naviView.onPause()
            controller.destroy()
            naviView.onDestroy()
        }
    }

    AndroidView(factory = { naviView }, modifier = modifier)
}

private fun satelliteSystemLabel(constellationType: Int): String = when (constellationType) {
    GnssStatus.CONSTELLATION_GPS -> "GPS（美国）"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS（俄罗斯）"
    GnssStatus.CONSTELLATION_BEIDOU -> "北斗（中国）"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo（欧盟）"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS（日本）"
    GnssStatus.CONSTELLATION_IRNSS -> "NavIC（印度）"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS（增强系统）"
    else -> "其他卫星系统"
}