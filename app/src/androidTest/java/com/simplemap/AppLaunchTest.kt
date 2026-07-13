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
    fun mapWorkspace_isDisplayed() {
        composeRule.onNodeWithText("搜索地点、公交或路线").assertIsDisplayed()
        composeRule.onNodeWithText("地图").assertIsDisplayed()
    }
}