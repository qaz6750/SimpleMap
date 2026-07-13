package com.simplemap.privacy

import android.content.Context

interface PrivacyConsentStore {
    fun hasAccepted(policyVersion: Int): Boolean

    fun accept(policyVersion: Int): Boolean
}

class SharedPreferencesPrivacyConsentStore(context: Context) : PrivacyConsentStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun hasAccepted(policyVersion: Int): Boolean =
        preferences.getInt(KEY_POLICY_VERSION, NO_POLICY) == policyVersion

    override fun accept(policyVersion: Int): Boolean =
        preferences.edit().putInt(KEY_POLICY_VERSION, policyVersion).commit()

    private companion object {
        const val FILE_NAME = "privacy_preferences"
        const val KEY_POLICY_VERSION = "accepted_policy_version"
        const val NO_POLICY = -1
    }
}