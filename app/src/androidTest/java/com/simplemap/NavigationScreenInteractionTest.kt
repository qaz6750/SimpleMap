package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationServiceArea
import com.simplemap.navigation.NavigationUiState
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.search.Place
import com.simplemap.ui.NavigationScreen
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationScreenInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun navigationScreen_showsGuidanceAndCanExit() {
        var exited = false
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        instruction = "右转进入环城西路",
                        currentRoad = "体育场路",
                        nextRoad = "环城西路",
                        maneuverIconType = 2,
                        maneuverDistanceMeters = 280,
                        remainingDistanceMeters = 7_400,
                        remainingTimeSeconds = 1_080,
                        currentSpeedKmh = 36,
                        speedLimitKmh = 60,
                        cameraDistanceMeters = 620,
                        intervalAverageSpeedKmh = 52,
                        intervalRemainingMeters = 3_200,
                        serviceAreas = listOf(
                            NavigationServiceArea("临安服务区", 12_000, 900),
                            NavigationServiceArea("龙岗服务区", 31_000, 2_100),
                        ),
                        remainingTrafficLights = 8,
                    ),
                    onExit = { exited = true },
                )
            }
        }

        composeRule.onNodeWithText("环城西路").assertIsDisplayed()
        composeRule.onNodeWithText("280 米").assertIsDisplayed()
        composeRule.onNodeWithText("18 分钟").assertIsDisplayed()
        composeRule.onNodeWithText("剩余 7.4 公里").assertIsDisplayed()
        composeRule.onNodeWithText("60").assertIsDisplayed()
        composeRule.onNodeWithText("临安服务区 12.0 公里").assertIsDisplayed()
        composeRule.onNodeWithText("龙岗服务区 31.0 公里").assertIsDisplayed()
        composeRule.onNodeWithText("区间测速 3.2 公里 · 均速 52").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("总览 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("回正 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("结束 导航").performClick()
        composeRule.runOnIdle { assertTrue(exited) }
    }

    @Test
    fun navigationScreen_usesLandscapeVehicleLayout() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        currentRoad = "秋石高架路",
                        nextRoad = "机场高速",
                        maneuverDistanceMeters = 1_200,
                        remainingDistanceMeters = 34_000,
                        remainingTimeSeconds = 2_100,
                        currentSpeedKmh = 82,
                        speedLimitKmh = 100,
                        intervalAverageSpeedKmh = 78,
                        intervalRemainingMeters = 5_600,
                        serviceAreas = listOf(
                            NavigationServiceArea("下沙服务区", 18_000, 900),
                            NavigationServiceArea("萧山服务区", 42_000, 2_100),
                        ),
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 900.dp, height = 480.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("横屏车机导航布局").assertIsDisplayed()
        composeRule.onNodeWithText("下沙服务区 18.0 公里").assertIsDisplayed()
        composeRule.onNodeWithText("萧山服务区 42.0 公里").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("总览 导航").assertIsDisplayed()
    }
}

private fun routePlan() = RoutePlan(
    id = "drive-0",
    mode = RouteMode.Drive,
    durationSeconds = 1_080,
    distanceMeters = 7_400,
    costYuan = null,
    summary = "推荐路线",
    steps = emptyList(),
    polyline = listOf(RoutePoint(30.2920, 120.2120), RoutePoint(30.2511, 120.1269)),
)

private fun place(
    id: String,
    name: String,
    latitude: Double,
    longitude: Double,
) = Place(
    id = id,
    name = name,
    address = "测试地址",
    district = "杭州市",
    category = "",
    phone = "",
    latitude = latitude,
    longitude = longitude,
    distanceMeters = null,
)