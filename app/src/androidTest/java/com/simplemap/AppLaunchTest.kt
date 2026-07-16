package com.simplemap

import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.simplemap.privacy.SharedPreferencesPrivacyConsentStore
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ClearPrivacyConsentRule())
        .around(composeRule)

    @Test
    fun privacyConsent_isRequiredOnFirstLaunch() {
        composeRule.onNodeWithText("欢迎使用 SimpleMap").assertIsDisplayed()
        composeRule.onNodeWithText("同意并继续").assertIsDisplayed()
    }
}

private class ClearPrivacyConsentRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            SharedPreferencesPrivacyConsentStore(ApplicationProvider.getApplicationContext()).revoke()
            base.evaluate()
        }
    }
}