package com.simplemap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest

internal data class NavigationRequest(
    val routeRequest: RouteRequest,
    val plan: RoutePlan,
    val simulated: Boolean,
)

internal data class ActiveTripSession(
    val startedAtMillis: Long? = null,
    val recorded: Boolean = false,
)

internal data class NavigationFlowState(
    val pendingRequest: NavigationRequest? = null,
    val activeRequest: NavigationRequest? = null,
    val tripSession: ActiveTripSession? = null,
)

internal class NavigationFlowStateHolder {
    var state by mutableStateOf(NavigationFlowState())
        private set

    fun startSimulated(request: NavigationRequest) {
        state = NavigationFlowState(
            activeRequest = request,
            tripSession = ActiveTripSession(),
        )
    }

    fun beginLiveStart() {
        state = NavigationFlowState(tripSession = ActiveTripSession())
    }

    fun awaitLocationPermission(request: NavigationRequest) {
        state = NavigationFlowState(
            pendingRequest = request,
            tripSession = ActiveTripSession(),
        )
    }

    fun activate(request: NavigationRequest) {
        state = state.copy(
            pendingRequest = null,
            activeRequest = request,
        )
    }

    fun failStart() {
        state = NavigationFlowState()
    }

    fun rejectPendingPermission() {
        state = NavigationFlowState()
    }

    fun restoreLive(request: NavigationRequest, startedAtMillis: Long) {
        state = NavigationFlowState(
            activeRequest = request,
            tripSession = ActiveTripSession(startedAtMillis = startedAtMillis),
        )
    }

    fun clearActive() {
        state = NavigationFlowState()
    }

    fun clearLiveActive(): Boolean {
        if (state.activeRequest?.simulated != false) return false
        clearActive()
        return true
    }

    fun markStarted(startedAtMillis: Long) {
        val session = state.tripSession ?: return
        if (session.startedAtMillis != null) return
        state = state.copy(tripSession = session.copy(startedAtMillis = startedAtMillis))
    }

    fun markRecorded() {
        val session = state.tripSession ?: return
        if (session.recorded) return
        state = state.copy(tripSession = session.copy(recorded = true))
    }
}
