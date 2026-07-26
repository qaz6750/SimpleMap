package com.simplemap.lifecycle

internal class AndroidViewLifecycle(
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onDestroy: () -> Unit,
) {
    private var state = State.Paused

    fun resume() {
        if (state != State.Paused) return
        if (runCatching(onResume).isSuccess) {
            state = State.Resumed
        }
    }

    fun pause() {
        if (state != State.Resumed) return
        if (runCatching(onPause).isSuccess) {
            state = State.Paused
        }
    }

    fun destroy() {
        if (state == State.Destroyed) return
        val shouldPause = state == State.Resumed
        state = State.Destroyed
        if (shouldPause) runCatching(onPause)
        runCatching(onDestroy)
    }

    private enum class State {
        Paused,
        Resumed,
        Destroyed,
    }
}
