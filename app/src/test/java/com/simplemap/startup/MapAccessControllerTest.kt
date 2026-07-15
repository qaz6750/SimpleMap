package com.simplemap.startup

import com.simplemap.privacy.PrivacyConsentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAccessControllerTest {
    @Test
    fun load_withoutConsent_doesNotPrepareSdk() {
        val runtime = RecordingRuntime()
        val controller = controller(store = FakeConsentStore(), runtime = runtime)

        assertEquals(MapAccessState.ConsentRequired, controller.load())
        assertEquals(0, runtime.prepareCalls)
    }

    @Test
    fun load_withOutdatedPolicy_requiresConsentAgain() {
        val runtime = RecordingRuntime()
        val controller = controller(
            store = FakeConsentStore(acceptedVersion = MapAccessController.CURRENT_POLICY_VERSION - 1),
            runtime = runtime,
        )

        assertEquals(MapAccessState.ConsentRequired, controller.load())
        assertEquals(0, runtime.prepareCalls)
    }

    @Test
    fun accept_whenPersistenceFails_doesNotPrepareSdk() {
        val runtime = RecordingRuntime()
        val controller = controller(
            store = FakeConsentStore(acceptSucceeds = false),
            runtime = runtime,
        )

        assertTrue(controller.accept() is MapAccessState.Failed)
        assertEquals(0, runtime.prepareCalls)
    }

    @Test
    fun accepted_withoutApiKey_doesNotPrepareSdk() {
        val runtime = RecordingRuntime()
        val controller = controller(
            store = FakeConsentStore(acceptedVersion = MapAccessController.CURRENT_POLICY_VERSION),
            apiKeyPresent = false,
            runtime = runtime,
        )

        assertEquals(MapAccessState.MissingApiKey, controller.load())
        assertEquals(0, runtime.prepareCalls)
    }

    @Test
    fun accepted_withApiKey_preparesSdkAndBecomesReady() {
        val runtime = RecordingRuntime()
        val controller = controller(
            store = FakeConsentStore(acceptedVersion = MapAccessController.CURRENT_POLICY_VERSION),
            runtime = runtime,
        )

        assertEquals(MapAccessState.Ready, controller.load())
        assertEquals(1, runtime.prepareCalls)
    }

    @Test
    fun sdkFailure_isReported() {
        val runtime = RecordingRuntime(failure = IllegalStateException("SDK unavailable"))
        val controller = controller(
            store = FakeConsentStore(acceptedVersion = MapAccessController.CURRENT_POLICY_VERSION),
            runtime = runtime,
        )

        val state = controller.load()

        assertTrue(state is MapAccessState.Failed)
        assertEquals("SDK unavailable", (state as MapAccessState.Failed).message)
        assertEquals(1, runtime.prepareCalls)
    }

    @Test
    fun revoke_clearsPersistedConsent() {
        val store = FakeConsentStore(acceptedVersion = MapAccessController.CURRENT_POLICY_VERSION)
        val controller = controller(store = store, runtime = RecordingRuntime())

        assertTrue(controller.revoke())
        assertEquals(MapAccessState.ConsentRequired, controller.load())
    }

    private fun controller(
        store: PrivacyConsentStore,
        apiKeyPresent: Boolean = true,
        runtime: RecordingRuntime,
    ) = MapAccessController(store, apiKeyPresent, runtime)

    private class FakeConsentStore(
        private var acceptedVersion: Int? = null,
        private val acceptSucceeds: Boolean = true,
    ) : PrivacyConsentStore {
        override fun hasAccepted(policyVersion: Int): Boolean = acceptedVersion == policyVersion

        override fun accept(policyVersion: Int): Boolean {
            if (acceptSucceeds) acceptedVersion = policyVersion
            return acceptSucceeds
        }

        override fun revoke(): Boolean {
            acceptedVersion = null
            return true
        }
    }

    private class RecordingRuntime(
        private val failure: Throwable? = null,
    ) : MapSdkRuntime {
        var prepareCalls = 0
            private set

        override fun prepare() {
            prepareCalls += 1
            failure?.let { throw it }
        }
    }
}