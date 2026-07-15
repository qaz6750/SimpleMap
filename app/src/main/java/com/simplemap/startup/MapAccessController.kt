package com.simplemap.startup

import com.simplemap.privacy.PrivacyConsentStore

sealed interface MapAccessState {
    data object Loading : MapAccessState
    data object ConsentRequired : MapAccessState
    data object MissingApiKey : MapAccessState
    data object Ready : MapAccessState
    data class Failed(val message: String) : MapAccessState
}

fun interface MapSdkRuntime {
    fun prepare()
}

class MapAccessController(
    private val consentStore: PrivacyConsentStore,
    private val apiKeyPresent: Boolean,
    private val runtime: MapSdkRuntime,
) {
    fun load(): MapAccessState {
        if (!consentStore.hasAccepted(CURRENT_POLICY_VERSION)) {
            return MapAccessState.ConsentRequired
        }
        return prepareIfConfigured()
    }

    fun accept(): MapAccessState {
        if (!consentStore.accept(CURRENT_POLICY_VERSION)) {
            return MapAccessState.Failed("隐私设置未能安全保存，请重试")
        }
        return prepareIfConfigured()
    }

    fun revoke(): Boolean = consentStore.revoke()

    private fun prepareIfConfigured(): MapAccessState {
        if (!apiKeyPresent) {
            return MapAccessState.MissingApiKey
        }
        return runCatching { runtime.prepare() }
            .fold(
                onSuccess = { MapAccessState.Ready },
                onFailure = { MapAccessState.Failed(it.message ?: "地图服务初始化失败") },
            )
    }

    companion object {
        const val CURRENT_POLICY_VERSION = 1
    }
}