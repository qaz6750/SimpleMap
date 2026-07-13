package com.simplemap.navigation

import android.content.Context
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
import com.amap.api.navi.model.NaviLatLng
import com.simplemap.route.RouteMode
import com.simplemap.search.Place
import java.lang.reflect.Proxy

class AmapNavigationController internal constructor(
    context: Context,
    private val naviView: AMapNaviView,
    voiceGuidance: Boolean,
) {
    private val navi = AMapNavi.getInstance(context.applicationContext)
    private var state = NavigationUiState()
    private var onStateChanged: (NavigationUiState) -> Unit = {}
    private var pendingRequest: NavigationRequest? = null
    private var started = false
    private val listener = Proxy.newProxyInstance(
        AMapNaviListener::class.java.classLoader,
        arrayOf(AMapNaviListener::class.java),
    ) { proxy, method, arguments ->
        when (method.name) {
            "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
            "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
            "toString" -> return@newProxyInstance "SimpleMapAmapNaviListener"
            "onInitNaviSuccess" -> calculatePendingRoute()
            "onInitNaviFailure" -> fail("导航引擎初始化失败")
            "onCalculateRouteSuccess" -> {
                update(state.copy(phase = NavigationPhase.Navigating, instruction = "路线已就绪"))
                if (!navi.startNavi(NaviType.GPS)) fail("无法启动 GPS 导航")
            }
            "onCalculateRouteFailure" -> fail("路线计算失败，请检查网络后重试")
            "onStartNavi" -> update(state.copy(phase = NavigationPhase.Navigating))
            "onNaviInfoUpdate" -> (arguments?.firstOrNull() as? NaviInfo)?.let(::onNaviInfo)
            "onGetNavigationText" -> {
                val text = arguments?.lastOrNull() as? String
                if (!text.isNullOrBlank()) update(state.copy(instruction = text))
            }
            "onReCalculateRouteForYaw" -> update(
                state.copy(phase = NavigationPhase.Calculating, message = "已偏离路线，正在重新规划"),
            )
            "onReCalculateRouteForTrafficJam" -> update(
                state.copy(phase = NavigationPhase.Calculating, message = "前方拥堵，正在寻找更优路线"),
            )
            "onGpsOpenStatus" -> update(state.copy(gpsAvailable = arguments?.firstOrNull() == true))
            "onGpsSignalWeak" -> update(state.copy(gpsAvailable = arguments?.firstOrNull() != true))
            "onArriveDestination", "onEndEmulatorNavi" -> update(
                state.copy(
                    phase = NavigationPhase.Arrived,
                    instruction = "已到达目的地",
                    remainingDistanceMeters = 0,
                    remainingTimeSeconds = 0,
                ),
            )
        }
        null
    } as AMapNaviListener

    init {
        navi.addAMapNaviListener(listener)
        navi.setUseInnerVoice(voiceGuidance, true)
    }

    fun setOnStateChanged(callback: (NavigationUiState) -> Unit) {
        onStateChanged = callback
        callback(state)
    }

    fun start(origin: Place, destination: Place, mode: RouteMode) {
        if (started) return
        started = true
        pendingRequest = NavigationRequest(origin, destination, mode)
        update(state.copy(phase = NavigationPhase.Calculating, instruction = "正在计算导航路线"))
        calculatePendingRoute()
    }

    fun overview() = naviView.displayOverview()

    fun recoverFollowing() = naviView.recoverLockMode()

    fun stop() {
        navi.stopNavi()
        started = false
    }

    fun destroy() {
        navi.removeAMapNaviListener(listener)
        navi.stopNavi()
    }

    private fun calculatePendingRoute() {
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
        if (!accepted) {
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
        update(
            state.copy(
                phase = NavigationPhase.Navigating,
                currentRoad = info.currentRoadName.orEmpty(),
                nextRoad = info.nextRoadName.orEmpty(),
                maneuverIconType = info.iconType,
                maneuverDistanceMeters = info.curStepRetainDistance,
                remainingDistanceMeters = info.pathRetainDistance,
                remainingTimeSeconds = info.pathRetainTime,
                currentSpeedKmh = info.currentSpeed,
                remainingTrafficLights = info.routeRemainLightCount,
                message = null,
            ),
        )
    }

    private fun fail(message: String) {
        update(state.copy(phase = NavigationPhase.Failed, message = message, instruction = message))
    }

    private fun update(newState: NavigationUiState) {
        state = newState
        onStateChanged(newState)
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
    modifier: Modifier = Modifier,
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
            setPointToCenter(0.5, 0.66)
        }
        AMapNaviView(context, options).apply { onCreate(null) }
    }
    val controller = remember(naviView, voiceGuidance) {
        AmapNavigationController(context, naviView, voiceGuidance)
    }
    val currentOnControllerReady by rememberUpdatedState(onControllerReady)

    LaunchedEffect(controller) { currentOnControllerReady(controller) }

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