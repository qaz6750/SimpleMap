package com.simplemap.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSettingsTest {
    @Test
    fun navigationDefaultsToThreeDimensionalPerspective() {
        assertTrue(NavigationSettings().perspectiveMode == NavigationPerspectiveMode.ThreeDimensional)
        assertTrue(NavigationSettings().perspectiveMode.tiltDegrees == 45)
    }

    @Test
    fun legacyDisabledVoiceResolvesToMuted() {
        assertTrue(
            NavigationSettings(voiceGuidance = false).resolvedVoiceGuidanceLevel ==
                VoiceGuidanceLevel.Muted,
        )
    }

    @Test
    fun selectingVoiceLevelUpdatesEnabledStateAtomically() {
        val muted = NavigationSettings().withVoiceGuidanceLevel(VoiceGuidanceLevel.Muted)
        val detailed = muted.withVoiceGuidanceLevel(VoiceGuidanceLevel.Detailed)
        val concise = muted.withVoiceGuidanceLevel(VoiceGuidanceLevel.Concise)

        assertFalse(muted.voiceGuidance)
        assertTrue(detailed.voiceGuidance)
        assertTrue(concise.voiceGuidance)
        assertTrue(detailed.resolvedVoiceGuidanceLevel == VoiceGuidanceLevel.Detailed)
        assertTrue(concise.resolvedVoiceGuidanceLevel == VoiceGuidanceLevel.Concise)
    }

    @Test
    fun automaticThemeUsesTimeAndTunnel() {
        assertFalse(shouldUseNightTheme(NavigationThemeMode.Automatic, false, 12 * 60, false))
        assertTrue(shouldUseNightTheme(NavigationThemeMode.Automatic, false, 21 * 60, false))
        assertTrue(shouldUseNightTheme(NavigationThemeMode.Automatic, false, 12 * 60, true))
    }

    @Test
    fun systemThemeStillUsesTunnelOverride() {
        assertTrue(shouldUseNightTheme(NavigationThemeMode.FollowSystem, true, 12 * 60, false))
        assertTrue(shouldUseNightTheme(NavigationThemeMode.FollowSystem, false, 12 * 60, true))
        assertFalse(shouldUseNightTheme(NavigationThemeMode.FollowSystem, false, 12 * 60, false))
    }

    @Test
    fun manualThemeOverridesAutomaticSignals() {
        assertFalse(shouldUseNightTheme(NavigationThemeMode.Day, true, 23 * 60, true))
        assertTrue(shouldUseNightTheme(NavigationThemeMode.Night, false, 12 * 60, false))
    }

    @Test
    fun quietHoursSupportsOvernightWindow() {
        assertTrue(isQuietHoursActive(true, 22 * 60, 7 * 60, 23 * 60))
        assertTrue(isQuietHoursActive(true, 22 * 60, 7 * 60, 6 * 60))
        assertFalse(isQuietHoursActive(true, 22 * 60, 7 * 60, 12 * 60))
        assertFalse(isQuietHoursActive(false, 22 * 60, 7 * 60, 23 * 60))
    }

    @Test
    fun importantAlertCanBypassQuietHoursOnlyWhenEnabled() {
        assertTrue(shouldPlayNavigationAlert(VoiceGuidanceLevel.Concise, true, true, true))
        assertFalse(shouldPlayNavigationAlert(VoiceGuidanceLevel.Concise, true, true, false))
        assertFalse(shouldPlayNavigationAlert(VoiceGuidanceLevel.Concise, true, false, true))
        assertFalse(shouldPlayNavigationAlert(VoiceGuidanceLevel.Muted, false, true, true))
    }
}