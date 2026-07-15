package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.route.RoutePoint
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.ui.RoutePlannerPanel
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RoutePlannerInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val origin = place("origin", "杭州东站", 30.2920, 120.2120)
    private val destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269)

    @Test
    fun routePlanner_selectsEndpointsAndComparesWalkRoute() {
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = FakeRoutePlanRepository(),
                    initialOrigin = origin.copy(name = "我的位置"),
                    initialDestination = null,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("起点 地点").assertTextContains("我的位置")

        val destinationField = composeRule.onNodeWithContentDescription("终点 地点")
        destinationField.performTextInput("西湖")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 西湖风景名胜区"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 西湖风景名胜区").performClick()
        composeRule.onNodeWithContentDescription("步行").performClick().assertIsSelected()
        composeRule.onNodeWithText("规划步行路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("42 分钟")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("4.2 公里").assertIsDisplayed()
        composeRule.onNodeWithText("路线详情").performClick()
        composeRule.onNodeWithText("1. 向西步行 200 米").assertIsDisplayed()
        composeRule.onNodeWithText("模拟导航").assertIsDisplayed()
        composeRule.onNodeWithText("开始导航").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("驾车").performClick().assertIsSelected()
        composeRule.onNodeWithText("规划驾车路线").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("开始导航")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun routePlanner_autoPlansExternalDestination() {
        val routeRepository = FakeRoutePlanRepository()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin.copy(name = "我的位置"),
                    initialDestination = destination,
                    autoPlan = true,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _, _ -> },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            routeRepository.requestCount == 1 &&
                composeRule.onAllNodes(hasText("42 分钟")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("开始导航").assertIsDisplayed()
    }

    @Test
    fun routePlanner_searchDoesNotCancelRoutePlanning() {
        val routeRepository = DelayedRoutePlanRepository()
        val routeSelected = AtomicBoolean(false)
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    autoPlan = true,
                    onRouteSelected = { routeSelected.set(true) },
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _, _ -> },
                )
            }
        }

        assertTrue(routeRepository.awaitRequest())
        composeRule.onNodeWithContentDescription("终点 地点").performImeAction()
        routeRepository.releaseRequest()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeSelected.get() }
    }

    @Test
    fun routePlanner_usesUpdatedCurrentLocationCoordinates() {
        val routeRepository = FakeRoutePlanRepository()
        val currentOrigin = mutableStateOf(origin.copy(id = "current-location", name = "我的位置"))
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = currentOrigin.value,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _, _ -> },
                )
            }
        }

        val updatedOrigin = currentOrigin.value.copy(latitude = 30.3001, longitude = 120.2201)
        composeRule.runOnIdle { currentOrigin.value = updatedOrigin }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("规划驾车路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        assertTrue(routeRepository.lastOrigin == updatedOrigin)
    }
}

private class FakeRoutePlaceRepository(
    private val origin: Place,
    private val destination: Place,
) : PlaceRepository {
    override fun search(query: String, city: String): Result<List<Place>> = Result.success(
        if (query.contains("东站")) listOf(origin) else listOf(destination),
    )
}

private class FakeRoutePlanRepository : RoutePlanRepository {
    var requestCount = 0
    var lastOrigin: Place? = null

    override fun plan(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        city: String,
    ): Result<List<RoutePlan>> {
        requestCount += 1
        lastOrigin = origin
        return Result.success(
        listOf(
            RoutePlan(
                id = "walk-0",
                mode = mode,
                durationSeconds = 2_520,
                distanceMeters = 4_200,
                costYuan = null,
                summary = "推荐步行路线",
                steps = listOf("向西步行 200 米", "沿湖滨路继续前行"),
                polyline = listOf(
                    RoutePoint(origin.latitude, origin.longitude),
                    RoutePoint(destination.latitude, destination.longitude),
                ),
            ),
        ),
    )
    }
}

private class DelayedRoutePlanRepository : RoutePlanRepository {
    private val requestStarted = CountDownLatch(1)
    private val releaseRequest = CountDownLatch(1)

    override fun plan(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        city: String,
    ): Result<List<RoutePlan>> {
        requestStarted.countDown()
        releaseRequest.await(5, TimeUnit.SECONDS)
        return Result.success(
            listOf(
                RoutePlan(
                    id = "delayed-route",
                    mode = mode,
                    durationSeconds = 600,
                    distanceMeters = 1_000,
                    costYuan = null,
                    summary = "延迟路线",
                    steps = emptyList(),
                    polyline = listOf(
                        RoutePoint(origin.latitude, origin.longitude),
                        RoutePoint(destination.latitude, destination.longitude),
                    ),
                ),
            ),
        )
    }

    fun awaitRequest(): Boolean = requestStarted.await(5, TimeUnit.SECONDS)

    fun releaseRequest() {
        releaseRequest.countDown()
    }
}

private fun place(
    id: String,
    name: String,
    latitude: Double,
    longitude: Double,
) = Place(
    id = id,
    name = name,
    address = "测试地址",
    district = "杭州市 · 西湖区",
    category = "",
    phone = "",
    latitude = latitude,
    longitude = longitude,
    distanceMeters = null,
)