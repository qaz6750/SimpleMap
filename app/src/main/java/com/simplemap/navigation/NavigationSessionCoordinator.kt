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
    @Volatile
    internal var latestState: NavigationUiState = NavigationUiState()
    internal var recorded: Boolean = false
    internal var recording: Boolean = false
    internal var pendingRecord: TripRecord? = null
}

object NavigationSessionCoordinator {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSession = MutableStateFlow<NavigationSession?>(null)
    private val mutableFailure = MutableStateFlow<String?>(null)
    private var pendingSpec: NavigationSessionSpec? = null
    private var activating = false
    private var finishing = false
    private var finishGeneration = 0L
    val session = mutableSession.asStateFlow()
    val failure = mutableFailure.asStateFlow()

    @Synchronized
    fun prepare(spec: NavigationSessionSpec): Boolean {
        if (mutableSession.value != null || pendingSpec != null || activating || finishing) return false
        mutableFailure.value = null
        pendingSpec = spec
        return true
    }

    @Synchronized
    fun cancelPending() {
        pendingSpec = null
    }

    @Synchronized
    fun hasPendingSession(): Boolean = pendingSpec != null || activating

    @Synchronized
    fun canStartNavigation(): Boolean =
        mutableSession.value == null && pendingSpec == null && !activating && !finishing

    @Synchronized
    fun reportActivationFailure(message: String) {
        pendingSpec = null
        activating = false
        mutableFailure.value = message
    }

    fun clearFailure() {
        mutableFailure.value = null
    }

    fun activate(context: Context): NavigationSession {
        val spec = synchronized(this) {
            check(mutableSession.value == null && !activating && !finishing) {
                "A navigation session is already active"
            }
            checkNotNull(pendingSpec) { "No navigation session has been prepared" }.also {
                pendingSpec = null
                activating = true
            }
        }
        var naviView: com.amap.api.navi.AMapNaviView? = null
        var controller: AmapNavigationController? = null
        try {
            naviView = createAmapNavigationView(context, spec.settings, isLandscape = false)
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
            synchronized(this) {
                check(activating && mutableSession.value == null && !finishing) {
                    "Navigation service stopped during activation"
                }
                mutableSession.value = session
                activating = false
            }
            controller.addStateListener { state ->
                session.latestState = state
                if (state.phase == NavigationPhase.Arrived || state.phase == NavigationPhase.Failed) {
                    finish(context, state.phase)
                }
            }
            return session
        } catch (error: Throwable) {
            synchronized(this) { activating = false }
            runCatching { controller?.destroy() ?: naviView?.onDestroy() }
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
        val record = current.pendingRecord ?: createRecord(current, phase).also {
            current.pendingRecord = it
        }
        persistenceScope.launch {
            val saved = persistRecord(applicationContext, record)
            val shouldStop = synchronized(NavigationSessionCoordinator) {
                current.recording = false
                if (saved) {
                    current.recorded = true
                    current.pendingRecord = null
                }
                finishing && finishGeneration == generation
            }
            if (shouldStop) NavigationSessionService.stop(applicationContext)
        }
    }

    fun onServiceDestroyed(context: Context) {
        val (current, fallbackRecord) = synchronized(this) {
            activating = false
            finishing = false
            finishGeneration++
            val session = detachSessionLocked()
            // Reuse the in-flight record so a destroy fallback remains an idempotent upsert.
            val record = session
                ?.takeUnless { it.recorded }
                ?.let { it.pendingRecord ?: createRecord(it, phase = null) }
            session to record
        }
        if (current == null) return
        runCatching { current.controller.destroy() }
        if (fallbackRecord != null) {
            persistenceScope.launch {
                persistRecord(context.applicationContext, fallbackRecord)
            }
        }
    }

    fun stop() {
        val current = synchronized(this) { detachSessionLocked() }
        runCatching { current?.controller?.destroy() }
    }

    private fun detachSessionLocked(): NavigationSession? {
        pendingSpec = null
        activating = false
        finishing = false
        val current = mutableSession.value ?: return null
        mutableSession.value = null
        return current
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
