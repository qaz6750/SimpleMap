package com.simplemap.navigation

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.amap.api.navi.enums.NaviIncidentType
import com.amap.api.navi.enums.IconType
import com.amap.api.navi.enums.AMapNaviRouteNotifyDataType
import com.amap.api.navi.enums.CarEnterCameraStatus
import com.amap.api.navi.enums.TrafficStatus
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.RouteOverlayOptions
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.view.AMapModeCrossOverlay
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.RouteMode
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.GnssStatusCompat
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
    private var pendingRequest: RouteRequest? = null
    private var started = false
    private var routeRequestAccepted = false
    private var navigationStarted = false
    private var navigationType = NaviType.GPS
    private var voiceGuidanceEnabled = voiceGuidance
    private var destroyed = false
    private var junctionViewGeneration = 0
    private var routeNoticeGeneration = 0L
    private var baselineArrivalSeconds: Long? = null
    private var trafficSegments: List<NavigationTrafficSegment> = emptyList()
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
            "onInitNaviSuccess" -> if (!routeRequestAccepted) calculatePendingRoute(failIfRejected = true)
            "onInitNaviFailure" -> fail("导航引擎初始化失败")
            "onCalculateRouteSuccess" -> {
                baselineArrivalSeconds = null
                updateRouteFacilitiesFromGuide()
                updateTrafficStatus()
                if (started && !navigationStarted) {
                    navigationStarted = true
                    update { it.copy(phase = NavigationPhase.Navigating, instruction = "路线已就绪") }
                    if (!navi.startNavi(navigationType)) {
                        navigationStarted = false
                        fail("无法启动 GPS 导航")
                    }
                }
            }
            "onCalculateRouteFailure" -> fail("路线计算失败，请检查网络后重试")
            "onStartNavi" -> {
                update { it.copy(phase = NavigationPhase.Navigating) }
                mainHandler.post { if (!destroyed) onNavigationStarted() }
            }
            "onTrafficStatusUpdate" -> updateTrafficStatus(announceChange = true)
            "onLocationChange" -> (arguments?.firstOrNull() as? AMapNaviLocation)?.let(::updateLocationDiagnostic)
            "onNaviInfoUpdate" -> (arguments?.firstOrNull() as? NaviInfo)?.let(::onNaviInfo)
            "onGetNavigationText" -> {
                val text = arguments?.lastOrNull() as? String
                if (!text.isNullOrBlank()) update { it.copy(instruction = text) }
            }
            "onReCalculateRouteForYaw" -> if (routeAlerts) {
                update { it.copy(phase = NavigationPhase.Calculating, message = "已偏离路线，正在重新规划") }
            }
            "onReCalculateRouteForTrafficJam" -> if (routeAlerts) {
                update { it.copy(phase = NavigationPhase.Calculating, message = "前方拥堵，正在寻找更优路线") }
            }
            "onGpsOpenStatus" -> update { it.copy(gpsAvailable = arguments?.firstOrNull() == true) }
            "onGpsSignalWeak" -> update { it.copy(gpsAvailable = arguments?.firstOrNull() != true) }
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
                )
            }
        }
        null
    } as AMapNaviListener

    init {
        navi.addAMapNaviListener(listener)
        navi.setTrafficStatusUpdateEnabled(true)
        navi.setTrafficInfoUpdateEnabled(true)
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
        request: RouteRequest,
        simulated: Boolean = false,
    ) {
        if (started) return
        started = true
        navigationType = if (simulated) NaviType.EMULATOR else NaviType.GPS
        pendingRequest = request
        update { it.copy(phase = NavigationPhase.Calculating, instruction = "正在计算导航路线") }
        calculatePendingRoute(failIfRejected = false)
    }

    fun overview() = naviView.displayOverview()

    fun setVoiceGuidance(enabled: Boolean) {
        voiceGuidanceEnabled = enabled
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

    fun setTrafficBar(enabled: Boolean) {
        naviView.viewOptions = naviView.viewOptions.apply { isTrafficBarEnabled = enabled }
    }

    fun setEagleMap(enabled: Boolean) {
        naviView.viewOptions = naviView.viewOptions.apply { isEagleMapVisible = enabled }
    }

    fun setAutoZoom(enabled: Boolean) {
        naviView.viewOptions = naviView.viewOptions.apply { isAutoChangeZoom = enabled }
    }

    fun setNightMode(enabled: Boolean) {
        naviView.viewOptions = naviView.viewOptions.apply {
            setAutoNaviViewNightMode(false)
            setNaviNight(enabled)
        }
    }

    fun updateSatelliteStatus(status: NavigationSatelliteStatus) {
        update { it.copy(satelliteStatus = status) }
    }

    fun recoverFollowing() {
        naviView.recoverLockMode()
        onMapInteractionChanged(false)
    }

    fun stop() {
        hideJunctionView()
        navi.stopNavi()
        trafficSegments = emptyList()
        started = false
        pendingRequest = null
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
        hideJunctionView()
        modeCrossOverlay.hideCrossOverlay()
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
                request.waypoints.map { NaviLatLng(it.latitude, it.longitude) },
                request.driveOptions.toAmapStrategy(),
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
            if (etaMessage != null && routeAlerts && voiceGuidanceEnabled) {
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
            if (changeMessage != null && voiceGuidanceEnabled) {
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
                        important = trafficAlert?.level == NavigationTrafficLevel.SeverelyCongested,
                    )
                } ?: current.routeNotice,
            )
        }
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
        val path = navi.naviPath ?: return null
        val route = path.coordList.orEmpty().map { point ->
            NavigationCoordinate(point.latitude, point.longitude)
        }
        return path.trafficIncidentInfo.orEmpty()
            .asSequence()
            .filter { incident ->
                incident.type >= NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_START &&
                    incident.type != NaviIncidentType.TYPE_OUT_ROUTE_CLOSED_EVENT
            }
            .mapNotNull { incident ->
                calculateIncidentDistance(
                    route = route,
                    incident = NavigationCoordinate(incident.latitude.toDouble(), incident.longitude.toDouble()),
                    travelledDistanceMeters = travelledDistanceMeters,
                    routeLengthMeters = path.allLength,
                )?.let { distance ->
                    NavigationTrafficIncident(
                        title = incident.title.orEmpty().ifBlank { incident.type.incidentTypeLabel },
                        typeLabel = incident.type.incidentTypeLabel,
                        distanceMeters = distance,
                    )
                }
            }
            .minByOrNull(NavigationTrafficIncident::distanceMeters)
    }

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
            state = newState
            onStateChanged(newState)
        } else {
            mainHandler.post {
                if (destroyed) return@post
                val newState = transform(state)
                state = newState
                onStateChanged(newState)
            }
        }
    }

    private fun DriveRouteOptions.toAmapStrategy() = navi.strategyConvert(
        avoidCongestion,
        avoidHighway,
        saveMoney,
        prioritizeHighway,
        true,
    )
}

@Composable
fun AmapNavigationView(
    onControllerReady: (AmapNavigationController) -> Unit,
    voiceGuidance: Boolean,
    trafficLayer: Boolean,
    routeAlerts: Boolean,
    trafficBar: Boolean,
    eagleMap: Boolean,
    autoZoom: Boolean,
    nightMode: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    simulated: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val naviView = remember {
        val options = AMapNaviViewOptions().apply {
            isLayoutVisible = false
            isAutoDrawRoute = true
            isTrafficLayerEnabled = trafficLayer
            isTrafficLine = trafficLayer
            isAutoLockCar = true
            isCompassEnabled = false
            isTrafficBarEnabled = trafficBar
            isRouteListButtonShow = false
            isSettingMenuEnabled = false
            isAutoChangeZoom = autoZoom
            isEagleMapVisible = eagleMap
            isShowCameraDistance = false
            isRealCrossDisplayShow = false
            setModeCrossDisplayShow(false)
            setAutoNaviViewNightMode(false)
            setNaviNight(nightMode)
            routeOverlayOptions = RouteOverlayOptions().apply {
                arrowColor = android.graphics.Color.rgb(23, 105, 224)
                arrowSideColor = android.graphics.Color.rgb(18, 62, 126)
                lineWidth = 28f
            }
            setPointToCenter(if (isLandscape) 0.64 else 0.5, if (isLandscape) 0.58 else 0.66)
        }
        AMapNaviView(context, options).apply { onCreate(null) }
    }
    val controller = remember(naviView) {
        AmapNavigationController(context, naviView, voiceGuidance, routeAlerts)
    }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)

    LaunchedEffect(controller) { currentOnControllerReady(controller) }
    LaunchedEffect(
        controller,
        voiceGuidance,
        trafficLayer,
        routeAlerts,
        trafficBar,
        eagleMap,
        autoZoom,
        nightMode,
    ) {
        controller.setVoiceGuidance(voiceGuidance)
        controller.setTrafficLayer(trafficLayer)
        controller.setRouteAlerts(routeAlerts)
        controller.setTrafficBar(trafficBar)
        controller.setEagleMap(eagleMap)
        controller.setAutoZoom(autoZoom)
        controller.setNightMode(nightMode)
    }
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
    GnssStatusCompat.CONSTELLATION_GPS -> "GPS（美国）"
    GnssStatusCompat.CONSTELLATION_GLONASS -> "GLONASS（俄罗斯）"
    GnssStatusCompat.CONSTELLATION_BEIDOU -> "北斗（中国）"
    GnssStatusCompat.CONSTELLATION_GALILEO -> "Galileo（欧盟）"
    GnssStatusCompat.CONSTELLATION_QZSS -> "QZSS（日本）"
    GnssStatusCompat.CONSTELLATION_IRNSS -> "NavIC（印度）"
    GnssStatusCompat.CONSTELLATION_SBAS -> "SBAS（增强系统）"
    else -> "其他卫星系统"
}