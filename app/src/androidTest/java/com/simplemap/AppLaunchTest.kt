package com.simplemap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun privacyConsent_isRequiredOnFirstLaunch() {
        composeRule.onNodeWithText("欢迎使用 SimpleMap").assertIsDisplayed()
        composeRule.onNodeWithText("同意并继续").assertIsDisplayed()
    }
}