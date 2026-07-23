package com.simplemap.navigation

import android.content.Context
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewOptions
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.NaviIncidentType
import com.amap.api.navi.enums.IconType
import com.amap.api.navi.enums.LaneAction
import com.amap.api.navi.enums.AMapNaviRouteNotifyDataType
import com.amap.api.navi.enums.CarEnterCameraStatus
import com.amap.api.navi.enums.BroadcastMode
import com.amap.api.navi.enums.TrafficStatus
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.view.AMapModeCrossOverlay
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.simplemap.amap.amapNavigationRouteOverlayOptions
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.settings.isQuietHoursActive
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.settings.shouldPlayNavigationAlert
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.GnssStatusCompat
import java.lang.reflect.Proxy
import java.util.LinkedHashMap
import java.time.LocalTime

private const val ROUTE_REQUEST_RETRY_DELAY_MILLIS = 5_000L

internal fun isOngoingAmapRouteIncident(type: Int): Boolean =
    type >= NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_START &&
        type != NaviIncidentType.TYPE_OUT_ROUTE_CLOSED_EVENT &&
        type != NaviIncidentType.TYPE_ROUTE_UNCLOSED_EVENT

internal fun navigationMapType(nightMode: Boolean): Int =
    if (nightMode) AMap.MAP_TYPE_NAVI_NIGHT else AMap.MAP_TYPE_NAVI

class AmapNavigationController internal constructor(
    context: Context,
    internal val naviView: AMapNaviView,
    settings: NavigationSettings,
    private var routeAlerts: Boolean,
) {
    private val appContext = context.applicationContext
    private val navi = AMapNavi.getInstance(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = NavigationUiState()
    private val stateListeners = linkedMapOf<Any, (NavigationUiState) -> Unit>()
    private val navigationStartedListeners = linkedMapOf<Any, () -> Unit>()
    private val mapInteractionListeners = linkedMapOf<Any, (Boolean) -> Unit>()
    private var pendingRequest: RouteRequest? = null
    private var preferredPlan: RoutePlan? = null
    private var started = false
    private var routeRequestAccepted = false
    private var routeRequestRetryPending = false
    private var navigationEngineInitialized = false
    private var routeRecalculationInProgress = false
    private var navigationStarted = false
    private var navigationType = NaviType.GPS
    private var multipleRouteEnabled = false
    private var voiceGuidanceLevel = settings.resolvedVoiceGuidanceLevel
    private var quietHoursEnabled = settings.quietHoursEnabled
    private var quietHoursStartMinutes = settings.quietHoursStartMinutes
    private var quietHoursEndMinutes = settings.quietHoursEndMinutes
    private var importantAlertsEnabled = settings.importantAlertsEnabled
    private var themeMode = settings.themeMode
    private var appliedNightMode: Boolean? = null
    private var appliedRegularGuidanceEnabled: Boolean? = null
    private var appliedBroadcastMode: Int? = null
    @Volatile
    private var destroyed = false
    private var viewResumed = false
    private var viewDestroyed = false
    private var junctionViewGeneration = 0
    private var routeNoticeGeneration = 0L
    private var baselineArrivalSeconds: Long? = null
    private var trafficSegments: List<NavigationTrafficSegment> = emptyList()
    private var routeCoordinates: List<NavigationCoordinate> = emptyList()
    private var trafficIncidentAnchors: List<TrafficIncidentAnchor> = emptyList()
    private var trafficIncidentMarker: Marker? = null
    private var displayedTrafficIncident: NavigationTrafficIncident? = null
    private var consecutiveUnmatchedLocations = 0
    private val modeCrossOverlay = AMapModeCrossOverlay(context.applicationContext, naviView.map)
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
        }
        if (destroyed) return@newProxyInstance null
        when (method.name) {
            "onInitNaviSuccess" -> {
                navigationEngineInitialized = true
                routeRequestRetryPending = false
                if (started && !routeRequestAccepted) calculatePendingRoute(failIfRejected = true)
            }
            "onInitNaviFailure" -> fail("导航引擎初始化失败")
            "onCalculateRouteSuccess" -> {
                if (!started) return@newProxyInstance null
                routeRecalculationInProgress = false
                baselineArrivalSeconds = null
                selectPreferredRoute()
                refreshRouteCoordinates()
                updateRouteFacilitiesFromGuide()
                updateTrafficStatus()
                updateAlternativeRoutes()
                if (started && !navigationStarted) {
                    update { it.copy(phase = NavigationPhase.Navigating, instruction = "路线已就绪") }
                    if (!navi.startNavi(navigationType)) {
                        fail("无法启动 GPS 导航")
                    }
                } else if (navigationStarted) {
                    update { it.copy(phase = NavigationPhase.Navigating, message = null) }
                }
            }
            "onCalculateRouteFailure" -> {
                if (navigationStarted && routeRecalculationInProgress) {
                    routeRecalculationInProgress = false
                    update {
                        it.copy(
                            phase = NavigationPhase.Navigating,
                            message = "路线更新失败，继续沿当前路线导航",
                            routeNotice = NavigationRouteNotice(
                                id = ++routeNoticeGeneration,
                                title = "路线更新失败",
                                detail = "网络恢复后将再次尝试，当前继续沿原路线导航",
                            ),
                        )
                    }
                } else {
                    fail("路线计算失败，请检查网络后重试")
                }
            }
            "onStartNavi" -> {
                navigationStarted = true
                update { it.copy(phase = NavigationPhase.Navigating) }
                mainHandler.post {
                    if (!destroyed) navigationStartedListeners.values.toList().forEach { it() }
                }
            }
            "onTrafficStatusUpdate" -> updateTrafficStatus(announceChange = true)
            "onLocationChange" -> (arguments?.firstOrNull() as? AMapNaviLocation)?.let(::updateLocationDiagnostic)
            "onNaviInfoUpdate" -> (arguments?.firstOrNull() as? NaviInfo)?.let(::onNaviInfo)
            "onGetNavigationText" -> {
                val text = arguments?.lastOrNull() as? String
                if (!text.isNullOrBlank()) update { it.copy(instruction = text) }
            }
            "onReCalculateRouteForYaw" -> {
                routeRecalculationInProgress = true
                if (routeAlerts) {
                    update { it.copy(message = "已偏离路线，正在重新规划") }
                }
            }
            "onReCalculateRouteForTrafficJam" -> {
                routeRecalculationInProgress = true
                if (routeAlerts) {
                    update { it.copy(message = "前方拥堵，正在寻找更优路线") }
                }
            }
            "onGpsOpenStatus" -> update {
                val enabled = arguments?.firstOrNull() == true
                it.copy(gpsEnabled = enabled, gpsSignalWeak = if (enabled) it.gpsSignalWeak else false)
            }
            "onGpsSignalWeak" -> update { it.copy(gpsSignalWeak = arguments?.firstOrNull() == true) }
            "updateCameraInfo" -> {
                val camera = (arguments?.firstOrNull() as? Array<*>)
                    ?.filterIsInstance<AMapNaviCameraInfo>()
                    ?.minByOrNull(AMapNaviCameraInfo::getCameraDistance)
                update {
                    it.copy(
                        speedLimitKmh = camera?.cameraSpeed?.takeIf { it > 0 },
                        cameraType = camera?.cameraType,
                        cameraDistanceMeters = camera?.cameraDistance?.takeIf { it >= 0 },
                        intervalAverageSpeedKmh = null,
                        intervalRemainingMeters = null,
                        intervalRecommendedSpeedKmh = null,
                    )
                }
            }
            "updateIntervalCameraInfo" -> {
                val start = arguments?.getOrNull(0) as? AMapNaviCameraInfo
                val status = arguments?.getOrNull(2) as? Int
                update {
                    if (status == CarEnterCameraStatus.LEAVE || start == null) {
                        it.copy(
                            speedLimitKmh = null,
                            cameraType = null,
                            intervalAverageSpeedKmh = null,
                            intervalRemainingMeters = null,
                            intervalRecommendedSpeedKmh = null,
                        )
                    } else {
                        it.copy(
                            speedLimitKmh = start.cameraSpeed.takeIf { speed -> speed > 0 },
                            cameraType = start.cameraType,
                            cameraDistanceMeters = null,
                            intervalAverageSpeedKmh = start.averageSpeed.takeIf { speed -> speed >= 0 },
                            intervalRemainingMeters = start.intervalRemainDistance.takeIf { distance -> distance >= 0 },
                            intervalRecommendedSpeedKmh = start.reasonableSpeedInRemainDist
                                .takeIf { speed -> speed > 0 },
                        )
                    }
                }
            }
            "onNaviRouteNotify" -> (arguments?.firstOrNull() as? AMapNaviRouteNotifyData)?.let { notice ->
                update { it.copy(routeNotice = notice.toRouteNotice()) }
            }
            "onArrivedWayPoint" -> {
                val waypointIndex = (arguments?.firstOrNull() as? Int)?.plus(1) ?: 1
                update {
                    it.copy(
                        routeNotice = NavigationRouteNotice(
                            id = ++routeNoticeGeneration,
                            title = "已到达途经点 $waypointIndex",
                            detail = "导航将继续前往下一站",
                        ),
                    )
                }
            }
            "onServiceAreaUpdate" -> {
                val serviceAreas = (arguments?.firstOrNull() as? Array<*>)
                    ?.filterIsInstance<AMapServiceAreaInfo>()
                    ?.asSequence()
                    ?.filter { it.remainDist >= 0 && it.name.isNotBlank() }
                    ?.sortedBy(AMapServiceAreaInfo::getRemainDist)
                    ?.map {
                        NavigationRouteFacility(
                            name = it.name,
                            distanceMeters = it.remainDist,
                            remainingTimeSeconds = it.remainTime.coerceAtLeast(0),
                            kind = NavigationFacilityKind.ServiceArea,
                        )
                    }
                    ?.toList()
                    .orEmpty()
                update {
                    it.copy(
                        routeFacilities = mergeRouteFacilities(it.routeFacilities, serviceAreas),
                    )
                }
            }
            "showCross" -> (arguments?.firstOrNull() as? AMapNaviCross)?.bitmap
                ?.takeUnless(Bitmap::isRecycled)
                ?.copy(Bitmap.Config.ARGB_8888, false)
                ?.let { bitmap ->
                    junctionViewGeneration++
                    update { it.copy(junctionViewBitmap = bitmap) }
                }
            "hideCross" -> hideJunctionView()
            "showModeCross" -> (arguments?.firstOrNull() as? AMapModelCross)?.picBuf1?.let { data ->
                val generation = ++junctionViewGeneration
                modeCrossOverlay.createModelCrossBitMap(data) { bitmap, _ ->
                    try {
                        if (generation == junctionViewGeneration && !destroyed && !bitmap.isRecycled) {
                            val junctionBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            update { it.copy(junctionViewBitmap = junctionBitmap) }
                        }
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
            "hideModeCross" -> hideJunctionView()
            "showLaneInfo" -> if (method.parameterCount == 1) {
                (arguments?.firstOrNull() as? AMapLaneInfo)?.let { laneInfo ->
                    update {
                        it.copy(
                            lanes = parseNavigationLanes(
                                backgroundLane = laneInfo.backgroundLane,
                                recommendedLane = laneInfo.frontLane,
                                laneCount = laneInfo.laneCount,
                            ),
                        )
                    }
                }
            }
            "hideLaneInfo" -> update { it.copy(lanes = emptyList()) }
            "onArriveDestination", "onEndEmulatorNavi" -> update {
                junctionViewGeneration++
                it.copy(
                    phase = NavigationPhase.Arrived,
                    instruction = "已到达目的地",
                    nextRoad = "",
                    maneuverIconType = 0,
                    maneuverIconBitmap = null,
                    maneuverDistanceMeters = 0,
                    remainingDistanceMeters = 0,
                    remainingTimeSeconds = 0,
                    currentSpeedKmh = 0,
                    speedLimitKmh = null,
                    cameraDistanceMeters = null,
                    cameraType = null,
                    intervalAverageSpeedKmh = null,
                    intervalRemainingMeters = null,
                    intervalRecommendedSpeedKmh = null,
                    routeNotice = null,
                    trafficAlert = null,
                    trafficIncident = null,
                    locationDiagnostic = null,
                    routeFacilities = emptyList(),
                    junctionViewBitmap = null,
                    lanes = emptyList(),
                )
            }
        }
        null
    } as AMapNaviListener

    init {
        var listenerRegistered = false
        try {
            navi.addAMapNaviListener(listener)
            listenerRegistered = true
            navi.setTrafficStatusUpdateEnabled(true)
            navi.setTrafficInfoUpdateEnabled(true)
            applyVoiceSettings()
            naviView.setOnMapTouchListener { event ->
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    notifyMapInteractionChanged(true)
                }
            }
        } catch (error: Throwable) {
            naviView.setOnMapTouchListener(null)
            if (listenerRegistered) navi.removeAMapNaviListener(listener)
            throw error
        }
    }

    fun addStateListener(callback: (NavigationUiState) -> Unit): Any {
        val token = Any()
        stateListeners[token] = callback
        callback(state)
        return token
    }

    fun removeStateListener(token: Any) {
        stateListeners.remove(token)
    }

    fun addNavigationStartedListener(callback: () -> Unit): Any {
        val token = Any()
        navigationStartedListeners[token] = callback
        if (navigationStarted) {
            mainHandler.post {
                if (!destroyed && navigationStartedListeners[token] === callback) callback()
            }
        }
        return token
    }

    fun removeNavigationStartedListener(token: Any) {
        navigationStartedListeners.remove(token)
    }

    fun addMapInteractionListener(callback: (Boolean) -> Unit): Any {
        val token = Any()
        mapInteractionListeners[token] = callback
        return token
    }

    fun removeMapInteractionListener(token: Any) {
        mapInteractionListeners.remove(token)
    }

    fun start(
        request: RouteRequest,
        simulated: Boolean = false,
        preferredPlan: RoutePlan? = null,
    ) {
        if (destroyed || started) return
        started = true
        navigationType = if (simulated) NaviType.EMULATOR else NaviType.GPS
        pendingRequest = request
        this.preferredPlan = preferredPlan
        val directDistanceMeters = FloatArray(1).also { result ->
            Location.distanceBetween(
                request.origin.latitude,
                request.origin.longitude,
                request.destination.latitude,
                request.destination.longitude,
                result,
            )
        }.first()
        multipleRouteEnabled = supportsMultipleRouteNavigation(request, simulated, directDistanceMeters)
        navi.setMultipleRouteNaviMode(multipleRouteEnabled)
        update { it.copy(phase = NavigationPhase.Calculating, instruction = "正在计算导航路线") }
        calculatePendingRoute(failIfRejected = navigationEngineInitialized)
    }

    fun overview() {
        if (destroyed) return
        naviView.displayOverview()
    }

    fun selectAlternativeRoute(pathId: Long) {
        if (destroyed) return
        navi.selectMainPathID(pathId)
        refreshRouteCoordinates()
        updateTrafficStatus()
        updateAlternativeRoutes()
        update {
            it.copy(
                routeNotice = NavigationRouteNotice(
                    id = ++routeNoticeGeneration,
                    title = "已切换导航路线",
                    detail = "预计到达时间和剩余距离已更新",
                ),
            )
        }
    }

    fun setVoiceSettings(settings: NavigationSettings) {
        if (destroyed) return
        voiceGuidanceLevel = settings.resolvedVoiceGuidanceLevel
        quietHoursEnabled = settings.quietHoursEnabled
        quietHoursStartMinutes = settings.quietHoursStartMinutes
        quietHoursEndMinutes = settings.quietHoursEndMinutes
        importantAlertsEnabled = settings.importantAlertsEnabled
        themeMode = settings.themeMode
        applyVoiceSettings()
    }

    fun setTrafficLayer(enabled: Boolean) {
        if (destroyed) return
        naviView.viewOptions = naviView.viewOptions.apply {
            isTrafficLayerEnabled = enabled
            isTrafficLine = enabled
        }
    }

    fun setRouteAlerts(enabled: Boolean) {
        if (destroyed) return
        routeAlerts = enabled
    }

    fun setTrafficBar(enabled: Boolean) {
        if (destroyed) return
        naviView.viewOptions = naviView.viewOptions.apply { isTrafficBarEnabled = enabled }
    }

    fun setEagleMap(enabled: Boolean) {
        if (destroyed) return
        naviView.viewOptions = naviView.viewOptions.apply { isEagleMapVisible = enabled }
    }

    fun setAutoZoom(enabled: Boolean) {
        if (destroyed) return
        naviView.viewOptions = naviView.viewOptions.apply { isAutoChangeZoom = enabled }
    }

    fun setPerspectiveMode(mode: NavigationPerspectiveMode) {
        if (destroyed) return
        naviView.viewOptions = naviView.viewOptions.apply { tilt = mode.tiltDegrees }
        naviView.lockTilt = mode.tiltDegrees
        naviView.recoverLockMode()
    }

    fun setNightMode(enabled: Boolean) {
        if (destroyed || appliedNightMode == enabled) return
        appliedNightMode = enabled
        naviView.viewOptions = naviView.viewOptions.apply {
            setAutoNaviViewNightMode(false)
            setNaviNight(enabled)
        }
        naviView.map.mapType = navigationMapType(enabled)
    }

    fun updateSatelliteStatus(status: NavigationSatelliteStatus) {
        update { it.copy(satelliteStatus = status) }
    }

    fun recoverFollowing() {
        if (destroyed) return
        naviView.recoverLockMode()
        notifyMapInteractionChanged(false)
    }

    fun stop() {
        if (destroyed) return
        hideJunctionView()
        update { it.copy(lanes = emptyList(), trafficIncident = null) }
        navi.stopNavi()
        trafficSegments = emptyList()
        routeCoordinates = emptyList()
        trafficIncidentAnchors = emptyList()
        started = false
        pendingRequest = null
        preferredPlan = null
        routeRequestAccepted = false
        routeRequestRetryPending = false
        routeRecalculationInProgress = false
        navigationStarted = false
    }

    fun resumeView() {
        if (!viewResumed && !viewDestroyed && !destroyed) {
            naviView.onResume()
            viewResumed = true
        }
    }

    fun pauseView() {
        if (viewResumed && !viewDestroyed) {
            naviView.onPause()
            viewResumed = false
        }
    }

    internal val isDestroyed: Boolean
        get() = destroyed

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pauseView()
        mainHandler.removeCallbacksAndMessages(null)
        stateListeners.clear()
        navigationStartedListeners.clear()
        mapInteractionListeners.clear()
        hideJunctionView()
        modeCrossOverlay.hideCrossOverlay()
        maneuverIconCache.clear()
        state = state.copy(maneuverIconBitmap = null, junctionViewBitmap = null)
        clearTrafficIncidentMarker()
        routeCoordinates = emptyList()
        trafficIncidentAnchors = emptyList()
        navi.removeAMapNaviListener(listener)
        navi.stopNavi()
        AMapNavi.destroy()
        if (!viewDestroyed) {
            naviView.onDestroy()
            viewDestroyed = true
        }
    }

    private fun calculatePendingRoute(failIfRejected: Boolean) {
        val request = pendingRequest ?: return
        val origin = NaviLatLng(request.origin.latitude, request.origin.longitude)
        val destination = NaviLatLng(request.destination.latitude, request.destination.longitude)
        val accepted = when (request.mode) {
            RouteMode.Drive -> navi.calculateDriveRoute(
                listOf(origin),
                listOf(destination),
                request.waypoints.map { NaviLatLng(it.latitude, it.longitude) },
                request.driveOptions.toAmapStrategy(multipleRouteEnabled),
            )
            RouteMode.Ride -> navi.calculateRideRoute(origin, destination)
            RouteMode.Walk -> navi.calculateWalkRoute(origin, destination)
            RouteMode.Transit -> false
        }
        routeRequestAccepted = accepted
        if (accepted || failIfRejected) routeRequestRetryPending = false
        if (!accepted && !failIfRejected && request.mode != RouteMode.Transit) {
            routeRequestRetryPending = true
            mainHandler.postDelayed(
                {
                    if (routeRequestRetryPending && !destroyed && started && !routeRequestAccepted) {
                        routeRequestRetryPending = false
                        calculatePendingRoute(failIfRejected = true)
                    }
                },
                ROUTE_REQUEST_RETRY_DELAY_MILLIS,
            )
        }
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
        applyVoiceSettings()
        val inTunnel = isTunnelRoad(info.currentRoadName.orEmpty())
        applyAutomaticNightMode(inTunnel)
        val maneuverIconBitmap = maneuverIconCache[info.iconType]
            ?: info.iconBitmap
                ?.takeUnless(Bitmap::isRecycled)
                ?.copy(Bitmap.Config.ARGB_8888, false)
                ?.also { maneuverIconCache[info.iconType] = it }
        update {
            val nowSeconds = System.currentTimeMillis() / 1_000L
            val currentArrivalSeconds = nowSeconds + info.pathRetainTime.coerceAtLeast(0)
            val etaChange = baselineArrivalSeconds?.let { baseline ->
                etaChangeMinutes(baseline, nowSeconds, info.pathRetainTime)
            }
            if (baselineArrivalSeconds == null || etaChange != null) {
                baselineArrivalSeconds = currentArrivalSeconds
            }
            val etaMessage = etaChange?.let { minutes ->
                if (minutes > 0) "预计晚到 $minutes 分钟" else "预计提前 ${-minutes} 分钟"
            }
            if (etaMessage != null && routeAlerts && canPlayCustomAlert(important = false)) {
                navi.playTTS(etaMessage, true)
            }
            val travelledDistance = (it.remainingDistanceMeters - info.pathRetainDistance)
                .coerceAtLeast(0)
            val routeTravelledDistance = ((navi.naviPath?.allLength ?: 0) - info.pathRetainDistance)
                .coerceAtLeast(0)
            val exitDirection = info.exitDirectionInfo
            val exitName = exitDirection?.exitNameInfo
                ?.filter(String::isNotBlank)
                ?.joinToString(" / ")
                .orEmpty()
            val direction = exitDirection?.directionInfo
                ?.filter(String::isNotBlank)
                ?.joinToString(" / ")
                .orEmpty()
            it.copy(
                phase = NavigationPhase.Navigating,
                currentRoad = info.currentRoadName.orEmpty(),
                inTunnel = inTunnel,
                nextRoad = info.nextRoadName.orEmpty(),
                highwayExit = listOf(exitName, direction)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" · "),
                maneuverIconType = info.iconType,
                maneuverIconBitmap = maneuverIconBitmap,
                maneuverDistanceMeters = info.curStepRetainDistance.coerceAtLeast(0),
                remainingDistanceMeters = info.pathRetainDistance.coerceAtLeast(0),
                remainingTimeSeconds = info.pathRetainTime.coerceAtLeast(0),
                currentSpeedKmh = info.currentSpeed.coerceAtLeast(0),
                routeFacilities = advanceRouteFacilities(it.routeFacilities, travelledDistance),
                trafficAlert = calculateUpcomingTraffic(trafficSegments, routeTravelledDistance),
                trafficIncident = findUpcomingTrafficIncident(routeTravelledDistance),
                remainingTrafficLights = info.routeRemainLightCount.coerceAtLeast(0),
                routeNotice = if (etaMessage != null && routeAlerts) {
                    NavigationRouteNotice(
                        id = ++routeNoticeGeneration,
                        title = etaMessage,
                        detail = "已根据最新路线和路况更新预计到达时间",
                    )
                } else {
                    it.routeNotice
                },
                message = null,
            )
        }
    }

    private fun fail(message: String) {
        started = false
        pendingRequest = null
        preferredPlan = null
        routeRequestAccepted = false
        routeRequestRetryPending = false
        routeRecalculationInProgress = false
        navigationStarted = false
        hideJunctionView()
        update {
            it.copy(
                phase = NavigationPhase.Failed,
                message = message,
                instruction = message,
                nextRoad = "",
                maneuverIconType = 0,
                maneuverIconBitmap = null,
                maneuverDistanceMeters = 0,
                currentSpeedKmh = 0,
                speedLimitKmh = null,
                cameraDistanceMeters = null,
                cameraType = null,
                intervalAverageSpeedKmh = null,
                intervalRemainingMeters = null,
                intervalRecommendedSpeedKmh = null,
                routeNotice = null,
                trafficAlert = null,
                trafficIncident = null,
                locationDiagnostic = null,
                routeFacilities = emptyList(),
            )
        }
    }

    private fun hideJunctionView() {
        junctionViewGeneration++
        update { current ->
            if (current.junctionViewBitmap == null) current else current.copy(junctionViewBitmap = null)
        }
    }

    private fun updateRouteFacilitiesFromGuide() {
        val tollGates = navi.naviGuideList.orEmpty()
            .asSequence()
            .mapNotNull(::guideToTollGate)
            .toList()
        update { current ->
            current.copy(routeFacilities = mergeRouteFacilities(current.routeFacilities, tollGates))
        }
    }

    private fun updateTrafficStatus(announceChange: Boolean = false) {
        refreshTrafficIncidentAnchors()
        trafficSegments = navi.naviPath?.trafficStatuses.orEmpty().map { traffic ->
            NavigationTrafficSegment(
                level = when (traffic.status) {
                    TrafficStatus.SMOOTH -> NavigationTrafficLevel.Smooth
                    TrafficStatus.SLOW -> NavigationTrafficLevel.Slow
                    TrafficStatus.JAM -> NavigationTrafficLevel.Congested
                    TrafficStatus.VERY_JAM -> NavigationTrafficLevel.SeverelyCongested
                    else -> NavigationTrafficLevel.Unknown
                },
                lengthMeters = traffic.length.coerceAtLeast(0),
            )
        }
        update { current ->
            val travelledDistance = ((navi.naviPath?.allLength ?: 0) - current.remainingDistanceMeters)
                .coerceAtLeast(0)
            val trafficAlert = calculateUpcomingTraffic(trafficSegments, travelledDistance)
            val changeMessage = if (announceChange && routeAlerts) {
                trafficChangeMessage(current.trafficAlert, trafficAlert)
            } else {
                null
            }
            val important = trafficAlert?.level == NavigationTrafficLevel.SeverelyCongested
            if (changeMessage != null && canPlayCustomAlert(important)) {
                navi.playTTS(changeMessage, true)
            }
            current.copy(
                trafficAlert = trafficAlert,
                trafficIncident = findUpcomingTrafficIncident(travelledDistance),
                routeNotice = changeMessage?.let { message ->
                    NavigationRouteNotice(
                        id = ++routeNoticeGeneration,
                        title = message,
                        detail = "已根据最新实时路况更新",
                        distanceMeters = trafficAlert?.distanceMeters,
                        important = important,
                    )
                } ?: current.routeNotice,
            )
        }
    }

    private fun updateAlternativeRoutes() {
        val selectedPathId = navi.naviPath?.pathid
        val routes = if (multipleRouteEnabled) {
            navi.naviPaths.orEmpty().values
                .distinctBy { it.pathid }
                .map { path ->
                    NavigationAlternativeRoute(
                        pathId = path.pathid,
                        label = path.labels.orEmpty().ifBlank { "备选路线" },
                        durationSeconds = path.allTime.coerceAtLeast(0),
                        distanceMeters = path.allLength.coerceAtLeast(0),
                        tollCostYuan = path.tollCost.coerceAtLeast(0),
                        selected = path.pathid == selectedPathId,
                    )
                }
                .sortedWith(
                    compareByDescending<NavigationAlternativeRoute> { it.selected }
                        .thenBy { it.durationSeconds },
                )
        } else {
            emptyList()
        }
        update { it.copy(alternativeRoutes = routes) }
    }

    private fun selectPreferredRoute() {
        val plan = preferredPlan ?: return
        preferredPlan = null
        val pathId = findMatchingNavigationPath(
            plan = plan,
            candidates = navi.naviPaths.orEmpty().values
                .distinctBy { it.pathid }
                .map { path ->
                    NavigationPathCandidate(
                        pathId = path.pathid,
                        durationSeconds = path.allTime.coerceAtLeast(0),
                        distanceMeters = path.allLength.coerceAtLeast(0),
                        polyline = path.coordList.orEmpty().map { point ->
                            RoutePoint(point.latitude, point.longitude)
                        },
                    )
                },
        ) ?: return
        navi.selectMainPathID(pathId)
    }

    private fun updateLocationDiagnostic(location: AMapNaviLocation) {
        consecutiveUnmatchedLocations = if (location.isMatchNaviPath) {
            0
        } else {
            consecutiveUnmatchedLocations + 1
        }
        update { current ->
            val coordinate = location.coord
            current.copy(
                locationDiagnostic = diagnoseLocation(
                    matchedToRoute = location.isMatchNaviPath,
                    accuracyMeters = location.accuracy,
                    consecutiveUnmatchedCount = consecutiveUnmatchedLocations,
                ),
                latitude = coordinate?.latitude,
                longitude = coordinate?.longitude,
            )
        }
    }

    private fun findUpcomingTrafficIncident(travelledDistanceMeters: Int): NavigationTrafficIncident? {
        return trafficIncidentAnchors
            .asSequence()
            .mapNotNull { incident ->
                (incident.routeDistanceMeters - travelledDistanceMeters.coerceAtLeast(0))
                    .takeIf { it >= 0 }
                    ?.let { distance ->
                    NavigationTrafficIncident(
                        title = incident.title,
                        typeLabel = incident.typeLabel,
                        distanceMeters = distance,
                        latitude = incident.latitude,
                        longitude = incident.longitude,
                    )
                }
            }
            .minByOrNull(NavigationTrafficIncident::distanceMeters)
    }

    private fun refreshRouteCoordinates() {
        routeCoordinates = navi.naviPath?.coordList.orEmpty().map { point ->
            NavigationCoordinate(point.latitude, point.longitude)
        }
        refreshTrafficIncidentAnchors()
    }

    private fun refreshTrafficIncidentAnchors() {
        val path = navi.naviPath
        if (path == null || routeCoordinates.isEmpty()) {
            trafficIncidentAnchors = emptyList()
            return
        }
        trafficIncidentAnchors = path.trafficIncidentInfo.orEmpty()
            .asSequence()
            .filter { incident -> isOngoingAmapRouteIncident(incident.type) }
            .mapNotNull { incident ->
                calculateIncidentRouteDistance(
                    route = routeCoordinates,
                    incident = NavigationCoordinate(
                        incident.latitude.toDouble(),
                        incident.longitude.toDouble(),
                    ),
                    routeLengthMeters = path.allLength,
                )?.let { routeDistanceMeters ->
                    TrafficIncidentAnchor(
                        title = incident.title.orEmpty().ifBlank { incident.type.incidentTypeLabel },
                        typeLabel = incident.type.incidentTypeLabel,
                        routeDistanceMeters = routeDistanceMeters,
                        latitude = incident.latitude.toDouble(),
                        longitude = incident.longitude.toDouble(),
                    )
                }
            }
            .toList()
    }

    private data class TrafficIncidentAnchor(
        val title: String,
        val typeLabel: String,
        val routeDistanceMeters: Int,
        val latitude: Double,
        val longitude: Double,
    )

    private val Int.incidentTypeLabel: String
        get() = when (this) {
            NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_START,
            NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_VIA,
            NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_END,
            NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_OTHER -> "道路封闭"
            NaviIncidentType.TYPE_ROUTE_UNCLOSED_EVENT -> "道路恢复"
            NaviIncidentType.TYPE_ROUTE_TARGET_DISPATCH -> "路线调度"
            else -> "交通事件"
        }

    private fun mergeRouteFacilities(
        current: List<NavigationRouteFacility>,
        incoming: List<NavigationRouteFacility>,
    ): List<NavigationRouteFacility> = (incoming + current)
        .distinctBy { it.kind to it.name }
        .sortedBy(NavigationRouteFacility::distanceMeters)

    private fun guideToTollGate(guide: Any): NavigationRouteFacility? = runCatching {
        val guideClass = guide.javaClass
        val iconType = guideClass.getMethod("getIconType").invoke(guide) as Int
        if (iconType != IconType.ARRIVED_TOLLGATE) return null
        val name = guideClass.getMethod("getName").invoke(guide) as? String
        if (name.isNullOrBlank()) return null
        val allLength = guideClass.getMethod("getAllLength").invoke(guide) as Int
        val length = guideClass.getMethod("getLength").invoke(guide) as Int
        val allTime = guideClass.getMethod("getAllTime").invoke(guide) as Int
        NavigationRouteFacility(
            name = name,
            distanceMeters = allLength.takeIf { it > 0 } ?: length.coerceAtLeast(0),
            remainingTimeSeconds = allTime.coerceAtLeast(0),
            kind = NavigationFacilityKind.TollGate,
            routeDistanceMeters = allLength.takeIf { it > 0 } ?: length.coerceAtLeast(0),
        )
    }.getOrNull()

    private fun AMapNaviRouteNotifyData.toRouteNotice(): NavigationRouteNotice {
        val title = when (notifyType) {
            AMapNaviRouteNotifyDataType.AVOID_RESTRICT_AREA -> "已避开限行区域"
            AMapNaviRouteNotifyDataType.FORBIDDEN_AREA -> "前方道路禁行"
            AMapNaviRouteNotifyDataType.ROAD_CLOSED_AREA -> "前方道路封闭"
            AMapNaviRouteNotifyDataType.AVOID_JAM_AREA -> "已避开拥堵路段"
            AMapNaviRouteNotifyDataType.CHANGE_MAIN_ROUTE -> "导航路线已更新"
            else -> subTitle.orEmpty().ifBlank { "路线提示" }
        }
        return NavigationRouteNotice(
            id = ++routeNoticeGeneration,
            title = title,
            detail = listOf(roadName, reason, subTitle)
                .filterNotNull()
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" · "),
            distanceMeters = distance.takeIf { it > 0 },
            important = notifyType == AMapNaviRouteNotifyDataType.FORBIDDEN_AREA ||
                notifyType == AMapNaviRouteNotifyDataType.ROAD_CLOSED_AREA,
        )
    }

    private fun update(transform: (NavigationUiState) -> NavigationUiState) {
        if (destroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val newState = transform(state)
            if (newState == state) return
            state = newState
            updateTrafficIncidentMarker(newState.trafficIncident)
            stateListeners.values.toList().forEach { it(newState) }
        } else {
            mainHandler.post {
                if (destroyed) return@post
                val newState = transform(state)
                if (newState == state) return@post
                state = newState
                updateTrafficIncidentMarker(newState.trafficIncident)
                stateListeners.values.toList().forEach { it(newState) }
            }
        }
    }

    private fun notifyMapInteractionChanged(interacting: Boolean) {
        if (destroyed) return
        mapInteractionListeners.values.toList().forEach { it(interacting) }
    }

    private fun updateTrafficIncidentMarker(incident: NavigationTrafficIncident?) {
        if (incident == displayedTrafficIncident) return
        if (incident != null && displayedTrafficIncident?.sameNodeAs(incident) == true) {
            displayedTrafficIncident = incident
            trafficIncidentMarker?.apply {
                position = LatLng(incident.latitude, incident.longitude)
                title = incident.typeLabel
                snippet = "${incident.title} · ${formatIncidentDistance(incident.distanceMeters)}"
                showInfoWindow()
            }
            return
        }
        clearTrafficIncidentMarker()
        displayedTrafficIncident = incident
        if (incident == null) return
        trafficIncidentMarker = naviView.map.addMarker(
            MarkerOptions()
                .position(LatLng(incident.latitude, incident.longitude))
                .title(incident.typeLabel)
                .snippet("${incident.title} · ${formatIncidentDistance(incident.distanceMeters)}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .anchor(0.5f, 1f)
                .zIndex(40f),
        ).apply { showInfoWindow() }
    }

    private fun clearTrafficIncidentMarker() {
        trafficIncidentMarker?.remove()
        trafficIncidentMarker = null
        displayedTrafficIncident = null
    }

    private fun applyVoiceSettings() {
        val quietHoursActive = isQuietHoursActive(
            enabled = quietHoursEnabled,
            startMinutes = quietHoursStartMinutes,
            endMinutes = quietHoursEndMinutes,
            minuteOfDay = LocalTime.now().hour * 60 + LocalTime.now().minute,
        )
        val regularGuidanceEnabled = voiceGuidanceLevel != VoiceGuidanceLevel.Muted && !quietHoursActive
        val broadcastMode = when (voiceGuidanceLevel) {
            VoiceGuidanceLevel.Detailed -> BroadcastMode.DETAIL
            VoiceGuidanceLevel.Concise -> BroadcastMode.CONCISE
            VoiceGuidanceLevel.Muted -> BroadcastMode.MUTE
        }
        if (appliedRegularGuidanceEnabled != regularGuidanceEnabled) {
            appliedRegularGuidanceEnabled = regularGuidanceEnabled
            navi.setUseInnerVoice(regularGuidanceEnabled, true)
        }
        if (appliedBroadcastMode != broadcastMode) {
            appliedBroadcastMode = broadcastMode
            navi.setBroadcastMode(broadcastMode)
        }
    }

    private fun canPlayCustomAlert(important: Boolean): Boolean {
        val now = LocalTime.now()
        val quietHoursActive = isQuietHoursActive(
            enabled = quietHoursEnabled,
            startMinutes = quietHoursStartMinutes,
            endMinutes = quietHoursEndMinutes,
            minuteOfDay = now.hour * 60 + now.minute,
        )
        return shouldPlayNavigationAlert(
            voiceLevel = voiceGuidanceLevel,
            quietHoursActive = quietHoursActive,
            important = important,
            importantAlertsEnabled = importantAlertsEnabled,
        )
    }

    private fun applyAutomaticNightMode(inTunnel: Boolean) {
        val now = LocalTime.now()
        val systemInDarkTheme = appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setNightMode(
            shouldUseNightTheme(
                mode = themeMode,
                systemInDarkTheme = systemInDarkTheme,
                minuteOfDay = now.hour * 60 + now.minute,
                inTunnel = inTunnel,
            ),
        )
    }

    private fun DriveRouteOptions.toAmapStrategy(multipleRoutes: Boolean) = navi.strategyConvert(
        avoidCongestion,
        avoidHighway,
        saveMoney,
        prioritizeHighway,
        multipleRoutes,
    )
}

@Composable
fun AmapNavigationView(
    onControllerReady: (AmapNavigationController) -> Unit,
    settings: NavigationSettings,
    trafficLayer: Boolean,
    routeAlerts: Boolean,
    trafficBar: Boolean,
    eagleMap: Boolean,
    autoZoom: Boolean,
    nightMode: Boolean,
    isLandscape: Boolean,
    overlaySafeAreaTopPx: Int = 0,
    overlaySafeAreaBottomPx: Int = 0,
    modifier: Modifier = Modifier,
    simulated: Boolean = false,
    sessionController: AmapNavigationController? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val naviView = remember(sessionController) {
        sessionController?.naviView ?: createAmapNavigationView(
            context = context,
            settings = NavigationSettings(
                voiceGuidance = settings.voiceGuidance,
                voiceGuidanceLevel = settings.voiceGuidanceLevel,
                quietHoursEnabled = settings.quietHoursEnabled,
                quietHoursStartMinutes = settings.quietHoursStartMinutes,
                quietHoursEndMinutes = settings.quietHoursEndMinutes,
                importantAlertsEnabled = settings.importantAlertsEnabled,
                trafficLayer = trafficLayer,
                routeAlerts = routeAlerts,
                trafficBar = trafficBar,
                eagleMap = eagleMap,
                autoZoom = autoZoom,
                nightMode = nightMode,
            ),
            isLandscape = isLandscape,
        )
    }
    val controller = remember(naviView, sessionController) {
        sessionController ?: AmapNavigationController(context, naviView, settings, routeAlerts)
    }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)

    LaunchedEffect(controller) { currentOnControllerReady(controller) }
    LaunchedEffect(
        controller,
        settings,
        trafficLayer,
        routeAlerts,
        trafficBar,
        eagleMap,
        autoZoom,
        nightMode,
        settings.perspectiveMode,
    ) {
        controller.setVoiceSettings(settings)
        controller.setTrafficLayer(trafficLayer)
        controller.setRouteAlerts(routeAlerts)
        controller.setTrafficBar(trafficBar)
        controller.setEagleMap(eagleMap)
        controller.setAutoZoom(autoZoom)
        controller.setPerspectiveMode(settings.perspectiveMode)
        controller.setNightMode(nightMode)
    }
    LaunchedEffect(
        naviView,
        isLandscape,
        overlaySafeAreaTopPx,
        overlaySafeAreaBottomPx,
        density.density,
    ) {
        naviView.post {
            if (controller.isDestroyed) return@post
            naviView.viewOptions = naviView.viewOptions.apply {
                setPointToCenter(
                    if (isLandscape) 0.64 else 0.5,
                    calculateNavigationPointToCenterY(
                        viewportHeight = naviView.height,
                        isLandscape = isLandscape,
                        overlaySafeAreaTopPx = overlaySafeAreaTopPx,
                        overlaySafeAreaBottomPx = overlaySafeAreaBottomPx,
                    ),
                )
            }
            val layout = calculateTmcRouteLayout(
                viewportWidth = naviView.width,
                viewportHeight = naviView.height,
                density = density.density,
                isLandscape = isLandscape,
                overlaySafeAreaTopPx = overlaySafeAreaTopPx,
                overlaySafeAreaBottomPx = overlaySafeAreaBottomPx,
            )
            naviView.setTMCRouteLayout(layout.x, layout.y, layout.width, layout.height)
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
            object : GnssStatusCompat.Callback() {
                override fun onStopped() {
                    controller.updateSatelliteStatus(NavigationSatelliteStatus())
                }

                override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
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
            }.also { callback ->
                LocationManagerCompat.registerGnssStatusCallback(
                    locationManager,
                    ContextCompat.getMainExecutor(context),
                    callback,
                )
            }
        } else {
            null
        }
        onDispose {
            callback?.let { LocationManagerCompat.unregisterGnssStatusCallback(locationManager, it) }
        }
    }

    DisposableEffect(naviView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.resumeView()
                Lifecycle.Event.ON_PAUSE -> controller.pauseView()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.resumeView()
        onDispose {
            lifecycle.removeObserver(observer)
            controller.pauseView()
            if (sessionController == null) {
                controller.destroy()
            }
        }
    }

    AndroidView(factory = { naviView }, modifier = modifier)
}

internal data class TmcRouteLayout(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun calculateTmcRouteLayout(
    viewportWidth: Int,
    viewportHeight: Int,
    density: Float,
    isLandscape: Boolean,
    overlaySafeAreaTopPx: Int = 0,
    overlaySafeAreaBottomPx: Int = 0,
): TmcRouteLayout {
    val resolvedDensity = density.takeIf { it > 0f } ?: 1f
    fun dp(value: Int) = (value * resolvedDensity).toInt()

    val resolvedWidth = viewportWidth.coerceAtLeast(1)
    val resolvedHeight = viewportHeight.coerceAtLeast(1)
    val horizontalMargin = dp(if (isLandscape) 12 else 10)
    val width = if (isLandscape) {
        (resolvedWidth * 0.032f).toInt().coerceIn(dp(18), dp(22))
    } else {
        (resolvedWidth * 0.045f).toInt().coerceIn(dp(16), dp(18))
    }.coerceAtMost(resolvedWidth)
    val x = (resolvedWidth - width - horizontalMargin).coerceAtLeast(0)

    val safeTop = overlaySafeAreaTopPx.coerceAtLeast(0)
    val safeBottom = overlaySafeAreaBottomPx.coerceAtLeast(0)
    val topMargin = dp(8)
    val bottomMargin = dp(8)
    val minHeight = dp(if (isLandscape) 96 else 132).coerceAtMost(resolvedHeight)
    val desiredY = if (safeTop > 0) {
        safeTop + topMargin
    } else if (isLandscape) {
        dp(52)
    } else {
        maxOf(dp(112), (resolvedHeight * 0.26f).toInt())
    }
    val bottomClearance = if (safeBottom > 0) {
        safeBottom + bottomMargin
    } else if (isLandscape) {
        dp(36)
    } else {
        dp(92)
    }.coerceAtMost((resolvedHeight - 1).coerceAtLeast(0))
    val maxY = (resolvedHeight - bottomClearance - minHeight).coerceAtLeast(0)
    val y = desiredY.coerceIn(0, maxY)
    val availableHeight = (resolvedHeight - y - bottomClearance).coerceAtLeast(1)
    val desiredHeight = if (isLandscape) {
        maxOf(dp(150), (availableHeight * 0.78f).toInt())
    } else {
        maxOf(dp(220), (availableHeight * 0.88f).toInt())
    }
    val height = desiredHeight.coerceIn(minOf(minHeight, availableHeight), availableHeight)
    return TmcRouteLayout(x = x, y = y, width = width, height = height)
}

internal fun calculateNavigationPointToCenterY(
    viewportHeight: Int,
    isLandscape: Boolean,
    overlaySafeAreaTopPx: Int = 0,
    overlaySafeAreaBottomPx: Int = 0,
): Double {
    val resolvedHeight = viewportHeight.coerceAtLeast(1)
    val baseCenterY = if (isLandscape) 0.58 else 0.66
    val safeTop = overlaySafeAreaTopPx.coerceAtLeast(0)
    val safeBottom = overlaySafeAreaBottomPx.coerceAtLeast(0)
    if (safeTop == 0 && safeBottom == 0) return baseCenterY

    val usableTop = safeTop.coerceAtMost(resolvedHeight - 1)
    val usableBottom = (resolvedHeight - safeBottom).coerceAtLeast(usableTop + 1)
    val usableHeight = (usableBottom - usableTop).coerceAtLeast(1)
    val anchoredCenterY = (usableTop + usableHeight * baseCenterY) / resolvedHeight.toDouble()
    return anchoredCenterY.coerceIn(0.2, 0.85)
}

internal fun createAmapNavigationView(
    context: Context,
    settings: NavigationSettings,
    isLandscape: Boolean,
): AMapNaviView {
    val options = AMapNaviViewOptions().apply {
        isLayoutVisible = false
        isAutoDrawRoute = true
        isTrafficLayerEnabled = settings.trafficLayer
        isTrafficLine = settings.trafficLayer
        isAutoLockCar = true
        isCompassEnabled = false
        isTrafficBarEnabled = settings.trafficBar
        isRouteListButtonShow = false
        isSettingMenuEnabled = false
        isAutoChangeZoom = settings.autoZoom
        setAutoDisplayOverview(true)
        setCameraBubbleShow(true)
        setShowCameraDistance(true)
        setWidgetOverSpeedPulseEffective(true)
        isNaviArrowVisible = true
        tilt = settings.perspectiveMode.tiltDegrees
        isEagleMapVisible = settings.eagleMap
        isRealCrossDisplayShow = false
        setModeCrossDisplayShow(false)
        setAutoNaviViewNightMode(false)
        setNaviNight(settings.nightMode)
                routeOverlayOptions = amapNavigationRouteOverlayOptions()
        setPointToCenter(if (isLandscape) 0.64 else 0.5, if (isLandscape) 0.58 else 0.66)
    }
    return AMapNaviView(context.applicationContext, options).apply {
        onCreate(null)
        map.mapType = navigationMapType(settings.nightMode)
        setTrafficLightsVisible(true)
        setShowTrafficLightView(true)
        setDriveGuideNaviAnimation(true)
        setShowDriveCongestion(true)
    }
}

private fun satelliteSystemLabel(constellationType: Int): String = when (constellationType) {
    GnssStatusCompat.CONSTELLATION_GPS -> "GPS（美国）"
    GnssStatusCompat.CONSTELLATION_GLONASS -> "GLONASS（俄罗斯）"
    GnssStatusCompat.CONSTELLATION_BEIDOU -> "北斗（中国）"
    GnssStatusCompat.CONSTELLATION_GALILEO -> "Galileo（欧盟）"
    GnssStatusCompat.CONSTELLATION_QZSS -> "QZSS（日本）"
    GnssStatusCompat.CONSTELLATION_IRNSS -> "NavIC（印度）"
    GnssStatusCompat.CONSTELLATION_SBAS -> "SBAS（增强系统）"
    else -> "其他卫星系统"
}

internal fun Int.toNavigationLaneDirection(): NavigationLaneDirection = when (this) {
    LaneAction.LANE_ACTION_AHEAD -> NavigationLaneDirection.Ahead
    LaneAction.LANE_ACTION_LEFT -> NavigationLaneDirection.Left
    LaneAction.LANE_ACTION_RIGHT -> NavigationLaneDirection.Right
    LaneAction.LANE_ACTION_AHEAD_LEFT -> NavigationLaneDirection.AheadLeft
    LaneAction.LANE_ACTION_AHEAD_RIGHT -> NavigationLaneDirection.AheadRight
    LaneAction.LANE_ACTION_LEFT_RIGHT -> NavigationLaneDirection.LeftRight
    LaneAction.LANE_ACTION_AHEAD_LEFT_RIGHT -> NavigationLaneDirection.AheadLeftRight
    LaneAction.LANE_ACTION_AHEAD_LU_TURN,
    LaneAction.LANE_ACTION_AHEAD_RU_TURN,
    -> NavigationLaneDirection.AheadUTurn
    LaneAction.LANE_ACTION_LU_TURN,
    LaneAction.LANE_ACTION_RU_TURN,
    LaneAction.LANE_ACTION_LEFT_LU_TURN,
    LaneAction.LANE_ACTION_RIGHT_RU_TURN,
    -> NavigationLaneDirection.UTurn
    else -> NavigationLaneDirection.Other
}

internal fun parseNavigationLanes(
    backgroundLane: IntArray?,
    recommendedLane: IntArray?,
    laneCount: Int,
): List<NavigationLane> {
    val availableDirections = backgroundLane ?: return emptyList()
    val count = minOf(laneCount.coerceAtLeast(0), availableDirections.size, 10)
    return List(count) { index ->
        NavigationLane(
            direction = availableDirections[index].toNavigationLaneDirection(),
            recommended = recommendedLane?.getOrNull(index)
                ?.let { it != LaneAction.LANE_ACTION_NULL } == true,
        )
    }
}
