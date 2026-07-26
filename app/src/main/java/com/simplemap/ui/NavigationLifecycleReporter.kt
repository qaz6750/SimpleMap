package com.simplemap.ui

import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationUiState

internal class NavigationLifecycleReporter {
    private var state = ReportingState.Pending

    fun reportStarted(onNavigationStarted: () -> Unit) {
        if (state != ReportingState.Pending) return
        state = ReportingState.Started
        onNavigationStarted()
    }

    fun reportFinished(
        phase: NavigationPhase,
        finalState: NavigationUiState,
        onNavigationFinished: (NavigationPhase, NavigationUiState) -> Unit,
    ) {
        if (state == ReportingState.Finished) return
        state = ReportingState.Finished
        onNavigationFinished(phase, finalState)
    }

    private enum class ReportingState {
        Pending,
        Started,
        Finished,
    }
}
