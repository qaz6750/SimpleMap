package com.simplemap.navigation

import android.content.Context
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.settings.NavigationSettings
import com.simplemap.trips.SharedPreferencesTripHistoryStore
import com.simplemap.trips.TripRecord
import com.simplemap.trips.createTripRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    internal var recording: Boolean = false
}

object NavigationSessionCoordinator {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSession = MutableStateFlow<NavigationSession?>(null)
    private val mutableFailure = MutableStateFlow<String?>(null)
    private var pendingSpec: NavigationSessionSpec? = null
    private var finishing = false
    private var finishGeneration = 0L
    val session = mutableSession.asStateFlow()
    val failure = mutableFailure.asStateFlow()

    @Synchronized
    fun prepare(spec: NavigationSessionSpec): Boolean {
        if (mutableSession.value != null || pendingSpec != null || finishing) return false
        mutableFailure.value = null
        pendingSpec = spec
        return true
    }

    fun cancelPending() {
        pendingSpec = null
    }

    fun hasPendingSession(): Boolean = pendingSpec != null

    @Synchronized
    fun canStartNavigation(): Boolean = mutableSession.value == null && pendingSpec == null && !finishing

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
        var controller: AmapNavigationController? = null
        try {
            controller = AmapNavigationController(
                context = context.applicationContext,
                naviView = naviView,
                settings = spec.settings,
                routeAlerts = spec.settings.routeAlerts,
            ).apply {
                setVoiceSettings(spec.settings)
                setTrafficLayer(spec.settings.trafficLayer)
                setTrafficBar(spec.settings.trafficBar)
                setEagleMap(spec.settings.eagleMap)
                setAutoZoom(spec.settings.autoZoom)
                setPerspectiveMode(spec.settings.perspectiveMode)
                setNightMode(spec.settings.nightMode)
                start(spec.routeRequest, preferredPlan = spec.plan)
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
        } catch (error: Throwable) {
            controller?.destroy() ?: naviView.onDestroy()
            throw error
        }
    }

    @Synchronized
    fun finish(context: Context, phase: NavigationPhase? = null) {
        val current = mutableSession.value ?: return
        if (current.recorded) {
            NavigationSessionService.stop(context)
            return
        }
        if (current.recording) return
        current.recording = true
        finishing = true
        val generation = ++finishGeneration
        val applicationContext = context.applicationContext
        val record = createRecord(current, phase)
        persistenceScope.launch {
            val saved = persistRecord(applicationContext, record)
            val shouldStop = synchronized(NavigationSessionCoordinator) {
                current.recording = false
                if (saved) current.recorded = true
                finishing && finishGeneration == generation
            }
            if (shouldStop) NavigationSessionService.stop(applicationContext)
        }
    }

    @Synchronized
    fun onServiceDestroyed(context: Context) {
        finishing = false
        finishGeneration++
        mutableSession.value?.let { current ->
            if (!current.recorded && !current.recording) {
                val saved = persistRecord(context.applicationContext, createRecord(current, phase = null))
                if (saved) current.recorded = true
            }
        }
        stop()
    }

    fun stop() {
        pendingSpec = null
        finishing = false
        val current = mutableSession.value ?: return
        mutableSession.value = null
        current.controller.destroy()
    }

    private fun createRecord(session: NavigationSession, phase: NavigationPhase?): TripRecord {
        val state = session.latestState
        return createTripRecord(
            startedAtMillis = session.startedAtMillis,
            completedAtMillis = System.currentTimeMillis(),
            request = session.spec.routeRequest,
            plan = session.spec.plan,
            phase = phase ?: state.phase,
            remainingDistanceMeters = state.remainingDistanceMeters,
            simulated = false,
        )
    }

    private fun persistRecord(context: Context, record: TripRecord): Boolean =
        runCatching { SharedPreferencesTripHistoryStore(context).add(record) }.getOrDefault(false)
}