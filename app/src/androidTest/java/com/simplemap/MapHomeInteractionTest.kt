package com.simplemap

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapHomeInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val landscapeViewport = mutableStateOf(false)

    @Before
    fun setContent() {
        composeRule.setContent {
            SimpleMapTheme {
                val landscape = landscapeViewport.value
                val density = LocalDensity.current
                val configuration = Configuration(LocalConfiguration.current).apply {
                    if (landscape) {
                        screenWidthDp = 640
                        screenHeightDp = 320
                        orientation = Configuration.ORIENTATION_LANDSCAPE
                    }
                }
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                    LocalDensity provides if (landscape) Density(1f, density.fontScale) else density,
                ) {
                    SimpleMapApp(
                        showLiveMap = false,
                        modifier = if (landscape) {
                            Modifier.requiredSize(width = 640.dp, height = 320.dp)
                        } else {
                            Modifier
                        },
                    )
                }
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
        composeRule.onNodeWithContentDescription("地图比例尺 100 米").assertIsDisplayed()
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
    fun mapPerspectiveCanSwitchAndResetNorth() {
        composeRule.onNodeWithContentDescription("地图视角 2D").assertIsSelected()
        composeRule.onNodeWithContentDescription("地图视角 3D").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("地图正北").assertIsDisplayed().performClick()
    }

    @Test
    fun floatingNavigation_switchesDestination() {
        composeRule.onNodeWithContentDescription("沉浸式底部导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("地图").assertIsSelected()
        composeRule.onNodeWithContentDescription("行程").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("地图").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("地图").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("路线").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("起点 地点").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithContentDescription("起点 地点").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("地图").assertIsSelected()
    }

    @Test
    fun mapHome_shortLandscapeKeepsToolsClearOfNavigation() {
        composeRule.runOnIdle { landscapeViewport.value = true }

        val zoom = composeRule.onNodeWithContentDescription("地图比例尺 100 米")
            .fetchSemanticsNode().boundsInRoot
        val location = composeRule.onNodeWithContentDescription("定位到当前位置")
            .fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithContentDescription("沉浸式底部导航")
            .fetchSemanticsNode().boundsInRoot
        val perspective = composeRule.onNodeWithContentDescription("地图视角 2D")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(zoom.right <= navigation.left)
        assertTrue(navigation.right <= location.left)
        assertTrue(location.bottom <= 320f)
        assertTrue(perspective.bottom <= location.top)
    }
}