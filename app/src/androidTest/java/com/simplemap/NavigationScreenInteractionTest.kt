package com.simplemap

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationFacilityKind
import com.simplemap.navigation.NavigationLocationDiagnostic
import com.simplemap.navigation.NavigationLocationIssue
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationLaneDirection
import com.simplemap.navigation.NavigationRouteFacility
import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.navigation.NavigationSatelliteStatus
import com.simplemap.navigation.NavigationTrafficAlert
import com.simplemap.navigation.NavigationTrafficIncident
import com.simplemap.navigation.NavigationTrafficLevel
import com.simplemap.navigation.NavigationUiState
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.search.Place
import com.simplemap.ui.NavigationScreen
import com.simplemap.ui.theme.SimpleMapTheme
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationScreenInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun navigationScreen_showsGuidanceAndCanExit() {
        var exited = false
        var finishedPhase: NavigationPhase? = null
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
                        highwayExit = "西湖景区 · 靠右",
                        maneuverIconType = 2,
                        maneuverDistanceMeters = 280,
                        remainingDistanceMeters = 7_400,
                        remainingTimeSeconds = 1_080,
                        currentSpeedKmh = 36,
                        speedLimitKmh = 60,
                        cameraDistanceMeters = 620,
                        intervalAverageSpeedKmh = 52,
                        intervalRemainingMeters = 3_200,
                        intervalRecommendedSpeedKmh = 48,
                        routeNotice = NavigationRouteNotice(
                            id = 1L,
                            title = "前方道路封闭",
                            detail = "环城西路 · 临时施工",
                            distanceMeters = 1_800,
                            important = true,
                        ),
                        trafficAlert = NavigationTrafficAlert(
                            level = NavigationTrafficLevel.SeverelyCongested,
                            distanceMeters = 900,
                            affectedLengthMeters = 2_400,
                        ),
                        trafficIncident = NavigationTrafficIncident(
                            title = "环城西路施工封闭",
                            typeLabel = "道路封闭",
                            distanceMeters = 1_100,
                        ),
                        junctionViewBitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888),
                        routeFacilities = listOf(
                            NavigationRouteFacility("临安服务区", 12_000, 900),
                            NavigationRouteFacility("杭州西收费站", 22_000, 1_500, NavigationFacilityKind.TollGate),
                            NavigationRouteFacility("龙岗服务区", 31_000, 2_100),
                            NavigationRouteFacility("绍兴服务区", 43_000, 2_800),
                            NavigationRouteFacility("绍兴北收费站", 56_000, 3_400, NavigationFacilityKind.TollGate),
                            NavigationRouteFacility("嵊州服务区", 74_000, 4_500),
                            NavigationRouteFacility("新昌收费站", 91_000, 5_600, NavigationFacilityKind.TollGate),
                        ),
                        satelliteStatus = NavigationSatelliteStatus(
                            visibleCount = 18,
                            usedInFixCount = 11,
                            averageCn0DbHz = 31.5f,
                            systems = mapOf("北斗（中国）" to 8, "GPS（美国）" to 10),
                        ),
                        remainingTrafficLights = 8,
                    ),
                    onExit = { exited = true },
                    onNavigationFinished = { phase, _ -> finishedPhase = phase },
                    previewMapInteracting = true,
                )
            }
        }

        composeRule.onNodeWithText("环城西路").assertIsDisplayed()
        composeRule.onNodeWithText("右转进入环城西路").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("高速出口 西湖景区 · 靠右").assertIsDisplayed()
        composeRule.onNodeWithText("280 米").assertIsDisplayed()
        composeRule.onNodeWithText("18 分钟").assertIsDisplayed()
        composeRule.onNodeWithText("剩余 7.4 公里").assertIsDisplayed()
        composeRule.onNodeWithText("60").assertIsDisplayed()
        composeRule.onNodeWithText("服务区 · 临安服务区").assertIsDisplayed()
        val statusCardBounds = composeRule.onNodeWithContentDescription("竖屏导航状态卡")
            .fetchSemanticsNode().boundsInRoot
        val currentRoadBounds = composeRule.onNodeWithText("体育场路").fetchSemanticsNode().boundsInRoot
        val facilitiesBounds = composeRule.onNodeWithText("服务区 · 临安服务区")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(currentRoadBounds.bottom <= statusCardBounds.top)
        assertTrue(facilitiesBounds.bottom <= statusCardBounds.top)
        composeRule.onNodeWithContentDescription("查看全部沿途设施").performClick()
        composeRule.onNodeWithContentDescription("全路线沿途设施").assertIsDisplayed()
        composeRule.onNodeWithText("杭州西收费站").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("沿途设施列表")
            .performScrollToNode(hasText("新昌收费站"))
        composeRule.onNodeWithText("新昌收费站").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithContentDescription(
            "区间测速 平均 52 公里每小时 剩余 3.2 公里 建议 48 公里每小时",
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("路线提示 前方道路封闭").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("前方 900 米 严重拥堵 影响 2.4 公里").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("道路封闭 环城西路施工封闭 距离 1.1 公里").assertDoesNotExist()
        composeRule.onNodeWithText("体育场路").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("展开路口放大图").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("收起路口放大图").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("路口放大图").assertIsDisplayed()
        val portraitCardBounds = composeRule.onNodeWithContentDescription("竖屏导航信息卡")
            .fetchSemanticsNode().boundsInRoot
        val portraitJunctionBounds = composeRule.onNodeWithContentDescription("路口放大图")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(portraitJunctionBounds.left >= portraitCardBounds.left)
        assertTrue(portraitJunctionBounds.top >= portraitCardBounds.top)
        assertTrue(portraitJunctionBounds.right <= portraitCardBounds.right)
        assertTrue(portraitJunctionBounds.bottom <= portraitCardBounds.bottom)
        composeRule.onNodeWithContentDescription("跟随 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("设置 导航").performClick()
        composeRule.onNodeWithContentDescription("路况柱 导航设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("鹰眼总览 导航设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("自动缩放 导航设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("按时间自动").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换横屏 导航设置").assertIsDisplayed()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithContentDescription("结束 导航").performClick()
        composeRule.runOnIdle {
            assertTrue(exited)
            assertTrue(finishedPhase == NavigationPhase.Navigating)
        }
    }

    @Test
    fun navigationScreen_gpsPanelUsesResponsivePositionAndAutoCloses() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        satelliteStatus = NavigationSatelliteStatus(
                            visibleCount = 18,
                            usedInFixCount = 11,
                            averageCn0DbHz = 31.5f,
                            systems = mapOf("北斗（中国）" to 8, "GPS（美国）" to 10),
                        ),
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                )
            }
        }

        composeRule.onNodeWithText("GPS 11").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("GPS 卫星状态").performClick()
        composeRule.onNodeWithText("5 秒后自动关闭").assertIsDisplayed()
        composeRule.onNodeWithText("18 颗").assertIsDisplayed()
        composeRule.onNodeWithText("北斗（中国）").assertIsDisplayed()
        val panelBounds = composeRule.onNodeWithContentDescription("GPS 定位详情面板")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(panelBounds.left < 640f / 2f)
        composeRule.waitUntil(timeoutMillis = 6_500) {
            composeRule.onAllNodesWithContentDescription("GPS 定位详情面板")
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithContentDescription("GPS 卫星状态").assertIsDisplayed()
    }

    @Test
    fun navigationScreen_persistsNightModeFromSettings() {
        var updatedSettings: NavigationSettings? = null
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    settings = NavigationSettings(themeMode = NavigationThemeMode.Day),
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                    onSettingsChanged = { updatedSettings = it },
                    previewMapInteracting = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置 导航").performClick()
        composeRule.onNodeWithText("导航设置").performClick()
        composeRule.onNodeWithText("导航设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("始终夜间").performClick()
        composeRule.onNodeWithContentDescription("简洁播报").performClick()
        composeRule.onNodeWithContentDescription("静音时段 22:00-07:00 导航设置").performClick()
        composeRule.onNodeWithContentDescription("自动缩放 导航设置").performClick()
        composeRule.runOnIdle {
            assertTrue(updatedSettings?.nightMode == true)
            assertTrue(updatedSettings?.themeMode == NavigationThemeMode.Night)
            assertTrue(updatedSettings?.voiceGuidanceLevel == VoiceGuidanceLevel.Concise)
            assertTrue(updatedSettings?.quietHoursEnabled == true)
            assertTrue(updatedSettings?.autoZoom == false)
        }
    }

    @Test
    fun navigationScreen_hidesCameraDistanceWithoutIntervalSpeed() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        cameraDistanceMeters = 620,
                    ),
                    onExit = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("前方电子眼 距离 620 米").assertDoesNotExist()
        composeRule.onNodeWithText("620 米").assertDoesNotExist()
    }

    @Test
    fun navigationScreen_placesPortraitGpsPanelInLowerHalf() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 360.dp, height = 640.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("GPS 卫星状态").performClick()
        val panelBounds = composeRule.onNodeWithContentDescription("GPS 定位详情面板")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(panelBounds.top >= 640f * 0.4f)
        assertTrue(panelBounds.bottom <= 640f)
    }

    @Test
    fun navigationScreen_explainsGpsDriftWithoutReportingOffRoute() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        locationDiagnostic = NavigationLocationDiagnostic(
                            issue = NavigationLocationIssue.LowAccuracy,
                            accuracyMeters = 65,
                        ),
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 360.dp, height = 640.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("GPS 卫星状态")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "GPS 漂移",
                ),
            )
            .performClick()
        composeRule.onNodeWithText("GPS 信号漂移").assertIsDisplayed()
        composeRule.onNodeWithText("定位精度较低，暂不判断为真实偏航").assertIsDisplayed()
        composeRule.onNodeWithText("约 65 米").assertIsDisplayed()
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
                        junctionViewBitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888),
                        routeFacilities = listOf(
                            NavigationRouteFacility("下沙服务区", 18_000, 900),
                            NavigationRouteFacility("萧山服务区", 42_000, 2_100),
                        ),
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                    previewMapInteracting = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("横屏车机导航布局").assertIsDisplayed()
        composeRule.onNodeWithText("秋石高架路").assertIsDisplayed()
        composeRule.onNodeWithText("时间").assertIsDisplayed()
        composeRule.onNodeWithText("剩余").assertIsDisplayed()
        composeRule.onNodeWithText("红绿灯").assertIsDisplayed()
        composeRule.onNodeWithText("预计").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("查看全部沿途设施").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "区间测速 平均 78 公里每小时 剩余 5.6 公里",
        ).assertDoesNotExist()
        val guidanceBounds = composeRule.onNodeWithText("机场高速").fetchSemanticsNode().boundsInRoot
        val junctionBounds = composeRule.onNodeWithContentDescription("路口放大图")
            .fetchSemanticsNode().boundsInRoot
        val informationBounds = composeRule.onNodeWithContentDescription("横屏导航信息卡")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("82").assertIsDisplayed()
        assertTrue(guidanceBounds.right <= informationBounds.right)
        composeRule.onNodeWithContentDescription("高速出口 西湖景区 · 靠右").assertDoesNotExist()
        assertTrue(junctionBounds.left >= informationBounds.left)
        assertTrue(junctionBounds.top >= informationBounds.top)
        assertTrue(junctionBounds.right <= informationBounds.right)
        assertTrue(junctionBounds.bottom <= informationBounds.bottom)
        composeRule.onNodeWithContentDescription("跟随 导航").assertIsDisplayed()
    }

    @Test
    fun navigationScreen_placesLandscapeSettingsOnRight() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置 导航").performClick()
        val panelBounds = composeRule.onNodeWithContentDescription("导航设置面板")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(panelBounds.center.x > 640f / 2f)
        assertTrue(panelBounds.right <= 640f)
    }

    @Test
    fun navigationScreen_placesLandscapeFacilitiesOnLeftWithoutJunctionView() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Navigating,
                        nextRoad = "机场高速",
                        highwayExit = "萧山机场 · 靠右",
                        maneuverDistanceMeters = 1_200,
                        routeFacilities = listOf(
                            NavigationRouteFacility("下沙服务区", 18_000, 900),
                            NavigationRouteFacility("萧山收费站", 28_000, 1_500, NavigationFacilityKind.TollGate),
                        ),
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                )
            }
        }

        val facilityBounds = composeRule.onNodeWithContentDescription("查看全部沿途设施")
            .fetchSemanticsNode().boundsInRoot
        val exitBounds = composeRule.onNodeWithContentDescription("高速出口 萧山机场 · 靠右")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(facilityBounds.left < 640f / 2f)
        assertTrue(exitBounds.left < 640f / 2f)
        composeRule.onNodeWithContentDescription("查看全部沿途设施").performClick()
        val panelBounds = composeRule.onNodeWithContentDescription("全路线沿途设施")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(panelBounds.left < 640f / 2f)
        composeRule.onNodeWithText("萧山收费站").assertIsDisplayed()
    }

    @Test
    fun navigationScreen_showsActionsOnlyDuringMapInteraction() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("跟随 导航").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("设置 导航").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("结束 导航").assertDoesNotExist()

        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                    previewMapInteracting = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("跟随 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("设置 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("结束 导航").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("跟随 导航").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("跟随 导航").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("设置 导航").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("结束 导航").assertDoesNotExist()
    }

    @Test
    fun navigationScreen_recoversFollowingAfterTenSeconds() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(phase = NavigationPhase.Navigating),
                    onExit = {},
                    previewMapInteracting = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("继续导航 导航").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(9_000L)
        composeRule.onNodeWithContentDescription("继续导航 导航").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("继续导航 导航").assertDoesNotExist()
    }

    @Test
    fun navigationScreen_arrivalHidesStaleManeuver() {
        var finishedCount = 0
        var finishedPhase: NavigationPhase? = null
        var parkingSearchRequested = false
        var savedParkingLocation: Pair<Double, Double>? = null
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = NavigationUiState(
                        phase = NavigationPhase.Arrived,
                        instruction = "已到达目的地",
                        currentRoad = "北山街",
                        latitude = 30.2511,
                        longitude = 120.1269,
                    ),
                    onExit = {},
                    onFindParking = { parkingSearchRequested = true },
                    onSaveParkingLocation = { latitude, longitude ->
                        savedParkingLocation = latitude to longitude
                    },
                    onNavigationFinished = { phase, _ ->
                        finishedCount += 1
                        finishedPhase = phase
                    },
                )
            }
        }

        composeRule.onNodeWithText("已到达目的地附近").assertIsDisplayed()
        composeRule.onNodeWithText("完成行程").assertIsDisplayed()
        composeRule.onNodeWithText("附近停车场").performClick()
        composeRule.onNodeWithText("保存停车位置").performClick()
        composeRule.onNodeWithText("机场高速").assertDoesNotExist()
        composeRule.onNodeWithText("280 米").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(finishedCount == 1)
            assertTrue(finishedPhase == NavigationPhase.Arrived)
            assertTrue(parkingSearchRequested)
            assertTrue(savedParkingLocation == 30.2511 to 120.1269)
        }
    }

    @Test
    fun navigationScreen_keepsCompactPortraitGuidanceAboveStatusCard() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = compactGuidanceState(),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 320.dp, height = 480.dp),
                )
            }
        }

        val guidance = composeRule.onNodeWithContentDescription("竖屏导航信息卡").fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithContentDescription("竖屏导航状态卡").fetchSemanticsNode().boundsInRoot
        val junction = composeRule.onNodeWithContentDescription("路口放大图").fetchSemanticsNode().boundsInRoot
        assertTrue(guidance.bottom <= status.top)
        assertTrue(junction.top >= guidance.top && junction.bottom <= guidance.bottom)
        assertTrue(status.bottom <= 480f)
        composeRule.onNodeWithContentDescription("路线提示 前方道路封闭").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("前方 900 米 严重拥堵 影响 2.4 公里").assertDoesNotExist()
    }

    @Test
    fun navigationScreen_compactsGuidanceWithoutJunctionViewOnShortScreen() {
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
                        nextRoad = "环城西路",
                        maneuverDistanceMeters = 280,
                        remainingDistanceMeters = 7_400,
                        remainingTimeSeconds = 1_080,
                    ),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 320.dp, height = 480.dp),
                )
            }
        }

        composeRule.onNodeWithText("280 米 后 环城西路").assertIsDisplayed()
        val guidance = composeRule.onNodeWithContentDescription("竖屏导航信息卡")
            .fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithContentDescription("竖屏导航状态卡")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(guidance.bottom <= status.top)
    }

    @Test
    fun navigationScreen_keepsCompactLandscapeInformationInsideViewport() {
        composeRule.setContent {
            SimpleMapTheme {
                NavigationScreen(
                    origin = place("origin", "杭州东站", 30.2920, 120.2120),
                    destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                    plan = routePlan(),
                    showLiveNavigation = false,
                    previewState = compactGuidanceState(),
                    onExit = {},
                    modifier = Modifier.requiredSize(width = 640.dp, height = 320.dp),
                )
            }
        }

        val information = composeRule.onNodeWithContentDescription("横屏导航信息卡")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(information.left >= 0f && information.top >= 0f)
        assertTrue(information.right <= 640f && information.bottom <= 320f)
        composeRule.onNodeWithContentDescription("路线提示 前方道路封闭").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("道路封闭 环城西路施工封闭 距离 1.1 公里").assertDoesNotExist()
    }

    @Test
    fun navigationScreen_supportsLargeFontWithoutOverlap() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                SimpleMapTheme {
                    NavigationScreen(
                        origin = place("origin", "杭州东站", 30.2920, 120.2120),
                        destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269),
                        plan = routePlan(),
                        showLiveNavigation = false,
                        previewState = compactGuidanceState(),
                        onExit = {},
                        modifier = Modifier.requiredSize(width = 360.dp, height = 640.dp),
                    )
                }
            }
        }

        val guidance = composeRule.onNodeWithContentDescription("竖屏导航信息卡").fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithContentDescription("竖屏导航状态卡").fetchSemanticsNode().boundsInRoot
        assertTrue(guidance.bottom <= status.top)
        assertTrue(guidance.left >= 0f && guidance.right <= 360f)
        assertTrue(status.bottom <= 640f)
        composeRule.onNodeWithText("机场高速").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("直行, 推荐右转").assertIsDisplayed()
    }
}

private fun compactGuidanceState() = NavigationUiState(
    phase = NavigationPhase.Navigating,
    instruction = "靠右行驶",
    currentRoad = "秋石高架路",
    nextRoad = "机场高速",
    maneuverDistanceMeters = 1_200,
    remainingDistanceMeters = 34_000,
    remainingTimeSeconds = 2_100,
    junctionViewBitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888),
    lanes = listOf(
        NavigationLane(NavigationLaneDirection.Ahead, recommended = false),
        NavigationLane(NavigationLaneDirection.Right, recommended = true),
    ),
    routeNotice = NavigationRouteNotice(
        id = 99L,
        title = "前方道路封闭",
        detail = "环城西路 · 临时施工",
        distanceMeters = 1_800,
        important = true,
    ),
    trafficAlert = NavigationTrafficAlert(
        level = NavigationTrafficLevel.SeverelyCongested,
        distanceMeters = 900,
        affectedLengthMeters = 2_400,
    ),
    trafficIncident = NavigationTrafficIncident(
        title = "环城西路施工封闭",
        typeLabel = "道路封闭",
        distanceMeters = 1_100,
    ),
)

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