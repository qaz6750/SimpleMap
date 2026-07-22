package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MapHomeInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setContent() {
        composeRule.setContent {
            SimpleMapTheme {
                SimpleMapApp(showLiveMap = false)
            }
        }
    }

    @Test
    fun search_canOpenAndClose() {
        composeRule.onNodeWithContentDescription("搜索地点或路线").performClick()
        composeRule.onNodeWithText("输入地点或路线").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("地图").assertDoesNotExist()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("搜索地点或路线").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("放大地图").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("缩小地图").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("地图").assertIsDisplayed()
    }

    @Test
    fun layerSelectionCollapsesLayerMenu() {
        composeRule.onNodeWithContentDescription("展开图层").performClick()
        composeRule.onNodeWithContentDescription("路况").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("展开图层").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("卫星").assertDoesNotExist()
    }

    @Test
    fun floatingNavigation_switchesDestination() {
        composeRule.onNodeWithContentDescription("地图").assertIsSelected()
        composeRule.onNodeWithContentDescription("路线").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("起点 地点").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithContentDescription("起点 地点").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("地图").assertIsSelected()
    }
}