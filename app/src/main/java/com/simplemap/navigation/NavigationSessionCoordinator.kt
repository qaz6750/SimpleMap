package com.simplemap.navigation

import android.content.Context
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.settings.NavigationSettings
import com.simplemap.trips.SharedPreferencesTripHistoryStore
import com.simplemap.trips.createTripRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavigationSessionSpec(
    val routeRequest: RouteRequest,
    val plan: RoutePlan,
    val settings: NavigationSettings,
)

class NavigationSession internal constructor(
    val spec: NavigationSessionSpec,
    val controller: AmapNavigationController,
) {
    val startedAtMillis: Long = System.currentTimeMillis()
    internal var latestState: NavigationUiState = NavigationUiState()
    internal var recorded: Boolean = false
}

object NavigationSessionCoordinator {
    private val mutableSession = MutableStateFlow<NavigationSession?>(null)
    private val mutableFailure = MutableStateFlow<String?>(null)
    private var pendingSpec: NavigationSessionSpec? = null
    val session = mutableSession.asStateFlow()
    val failure = mutableFailure.asStateFlow()

    fun prepare(spec: NavigationSessionSpec) {
        check(mutableSession.value == null) { "A navigation session is already active" }
        mutableFailure.value = null
        pendingSpec = spec
    }

    fun cancelPending() {
        pendingSpec = null
    }

    fun reportActivationFailure(message: String) {
        pendingSpec = null
        mutableFailure.value = message
    }

    fun clearFailure() {
        mutableFailure.value = null
    }

    fun activate(context: Context): NavigationSession {
        val spec = checkNotNull(pendingSpec) { "No navigation session has been prepared" }
        pendingSpec = null
        val naviView = createAmapNavigationView(context, spec.settings, isLandscape = false)
        val controller = AmapNavigationController(
            context = context.applicationContext,
            naviView = naviView,
            voiceGuidance = spec.settings.voiceGuidance,
            routeAlerts = spec.settings.routeAlerts,
        ).apply {
            setTrafficLayer(spec.settings.trafficLayer)
            setTrafficBar(spec.settings.trafficBar)
            setEagleMap(spec.settings.eagleMap)
            setAutoZoom(spec.settings.autoZoom)
            setNightMode(spec.settings.nightMode)
            start(spec.routeRequest)
        }
        val session = NavigationSession(spec, controller)
        mutableSession.value = session
        controller.addStateListener { state ->
            session.latestState = state
            if (state.phase == NavigationPhase.Arrived || state.phase == NavigationPhase.Failed) {
                finish(context, state.phase)
            }
        }
        return session
    }

    @Synchronized
    fun finish(context: Context, phase: NavigationPhase? = null) {
        val current = mutableSession.value ?: return
        record(context, current, phase)
        NavigationSessionService.stop(context)
    }

    @Synchronized
    fun onServiceDestroyed(context: Context) {
        mutableSession.value?.let { record(context, it, phase = null) }
        stop()
    }

    fun stop() {
        pendingSpec = null
        val current = mutableSession.value ?: return
        mutableSession.value = null
        current.controller.destroy()
        current.controller.naviView.onDestroy()
    }

    private fun record(context: Context, session: NavigationSession, phase: NavigationPhase?) {
        if (session.recorded) return
        session.recorded = true
        val state = session.latestState
        SharedPreferencesTripHistoryStore(context.applicationContext).add(
            createTripRecord(
                startedAtMillis = session.startedAtMillis,
                completedAtMillis = System.currentTimeMillis(),
                request = session.spec.routeRequest,
                plan = session.spec.plan,
                phase = phase ?: state.phase,
                remainingDistanceMeters = state.remainingDistanceMeters,
                simulated = false,
            ),
        )
    }
}