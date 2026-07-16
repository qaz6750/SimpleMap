package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.simplemap.route.RouteRequest
import com.simplemap.route.DriveRouteOptions
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
                    onStartNavigation = { _, _, _ -> },
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
                    onStartNavigation = { _, _, _ -> },
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
    fun routePlanner_comparesUpToThreeRoutes() {
        val selectedPlanIds = mutableListOf<String>()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = MultipleRoutePlanRepository(),
                    initialOrigin = origin,
                    initialDestination = destination,
                    autoPlan = true,
                    onRouteSelected = { selectedPlanIds += it.id },
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("为你推荐 3 条路线"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("推荐路线").assertIsDisplayed()
        composeRule.onNodeWithText("更快路线").performClick()
        composeRule.onNodeWithText("备用路线").assertIsDisplayed()
        composeRule.onNodeWithText("第四路线").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(selectedPlanIds.last() == "route-1") }
    }

    @Test
    fun routePlanner_transitShowsDetailsWithoutNavigationActions() {
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = FakeRoutePlanRepository(),
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("公交").performClick()
        composeRule.onNodeWithText("规划公交路线").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("查看公交详情")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("模拟导航").assertDoesNotExist()
        composeRule.onNodeWithText("开始导航").assertDoesNotExist()
        composeRule.onNodeWithText("查看公交详情").performClick()
        composeRule.onNodeWithText("1. 向西步行 200 米").assertIsDisplayed()
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
                    onStartNavigation = { _, _, _ -> },
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
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        val updatedOrigin = currentOrigin.value.copy(latitude = 30.3001, longitude = 120.2201)
        composeRule.runOnIdle { currentOrigin.value = updatedOrigin }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("规划驾车路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        assertTrue(routeRepository.lastRequest?.origin == updatedOrigin)
    }

    @Test
    fun routePlanner_keepsUserSelectedOriginWhenCurrentLocationChanges() {
        val selectedOrigin = place("selected-origin", "武林广场", 30.2741, 120.1552)
        val currentOrigin = mutableStateOf(origin.copy(id = "current-location", name = "我的位置"))
        val routeRepository = FakeRoutePlanRepository()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination, selectedOrigin),
                    routePlanRepository = routeRepository,
                    initialOrigin = currentOrigin.value,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("起点 地点").performTextInput("武林")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 武林广场"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 武林广场").performClick()
        composeRule.runOnIdle {
            currentOrigin.value = currentOrigin.value.copy(latitude = 30.3001, longitude = 120.2201)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("规划驾车路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        assertTrue(routeRepository.lastRequest?.origin == selectedOrigin)
    }

    @Test
    fun routePlanner_usesRequestedInitialMode() {
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = FakeRoutePlanRepository(),
                    initialOrigin = origin,
                    initialDestination = destination,
                    initialMode = RouteMode.Walk,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("步行").assertIsSelected()
        composeRule.onNodeWithText("规划步行路线").assertIsDisplayed()
    }

    @Test
    fun routePlanner_autoReplansOnlyAfterMeaningfulLocationChange() {
        val routeRepository = FakeRoutePlanRepository()
        val currentOrigin = mutableStateOf(origin.copy(id = "current-location", name = "我的位置"))
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = currentOrigin.value,
                    initialDestination = destination,
                    autoPlan = true,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        composeRule.runOnIdle {
            currentOrigin.value = currentOrigin.value.copy(latitude = currentOrigin.value.latitude + 0.0001)
        }
        composeRule.waitForIdle()
        assertTrue(routeRepository.requestCount == 1)

        val movedOrigin = currentOrigin.value.copy(latitude = currentOrigin.value.latitude + 0.001)
        composeRule.runOnIdle { currentOrigin.value = movedOrigin }
        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 2 }
        assertTrue(routeRepository.lastRequest?.origin == movedOrigin)
    }

    @Test
    fun routePlanner_passesPreferenceAndWaypointToNavigation() {
        val waypoint = place("waypoint", "西湖文化广场", 30.2801, 120.1571)
        val routeRepository = FakeRoutePlanRepository()
        var navigationRequest: RouteRequest? = null
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination, waypoint),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { request, _, _ -> navigationRequest = request },
                )
            }
        }

        composeRule.onNodeWithContentDescription("展开规划偏好").performClick()
        composeRule.onNodeWithContentDescription("路线偏好 躲避拥堵").performClick()
        composeRule.onNodeWithContentDescription("添加途经点").performClick()
        composeRule.onNodeWithContentDescription("途经点 1 地点").performTextInput("文化广场")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 西湖文化广场"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 西湖文化广场").performClick()
        composeRule.onNodeWithText("规划驾车路线").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("开始导航")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("开始导航").performClick()

        composeRule.runOnIdle {
            assertTrue(routeRepository.lastRequest?.waypoints == listOf(waypoint))
            assertTrue(routeRepository.lastRequest?.driveOptions == DriveRouteOptions(avoidCongestion = true))
            assertTrue(navigationRequest == routeRepository.lastRequest)
        }
    }

    @Test
    fun routePlanner_reordersWaypointsBeforePlanning() {
        val firstWaypoint = place("waypoint-1", "西湖文化广场", 30.2801, 120.1571)
        val secondWaypoint = place("waypoint-2", "武林广场", 30.2741, 120.1552)
        val routeRepository = FakeRoutePlanRepository()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(
                        origin,
                        destination,
                        firstWaypoint,
                        secondWaypoint,
                    ),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加途经点").performClick()
        composeRule.onNodeWithContentDescription("途经点 1 地点").performTextInput("文化广场")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 西湖文化广场"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 西湖文化广场").performClick()
        composeRule.onNodeWithContentDescription("添加途经点").performClick()
        composeRule.onNodeWithContentDescription("途经点 2 地点").performTextInput("武林广场")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 武林广场"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 武林广场").performClick()
        composeRule.onNodeWithContentDescription("上移途经点 2").performClick()
        composeRule.onNodeWithText("规划驾车路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        composeRule.runOnIdle {
            assertTrue(routeRepository.lastRequest?.waypoints == listOf(secondWaypoint, firstWaypoint))
        }
    }

    @Test
    fun routePlanner_combinesPreferencesAndResolvesConflicts() {
        val routeRepository = FakeRoutePlanRepository()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("展开规划偏好").performClick()
        composeRule.onNodeWithContentDescription("路线偏好 躲避拥堵").performClick()
        composeRule.onNodeWithContentDescription("路线偏好 不走高速").performClick()
        composeRule.onNodeWithText("规划驾车路线").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        composeRule.runOnIdle {
            assertTrue(
                routeRepository.lastRequest?.driveOptions == DriveRouteOptions(
                    avoidCongestion = true,
                    avoidHighway = true,
                ),
            )
        }

        composeRule.onNodeWithContentDescription("路线偏好 高速优先").performClick()
        composeRule.onNodeWithText("规划驾车路线").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 2 }
        composeRule.runOnIdle {
            assertTrue(
                routeRepository.lastRequest?.driveOptions == DriveRouteOptions(
                    avoidCongestion = true,
                    prioritizeHighway = true,
                ),
            )
        }
    }

    @Test
    fun routePlanner_usesAndUpdatesPersistedDrivePreferences() {
        val routeRepository = FakeRoutePlanRepository()
        var savedOptions: DriveRouteOptions? = null
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    initialDriveOptions = DriveRouteOptions(avoidCongestion = true),
                    onDriveOptionsChanged = { savedOptions = it },
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("展开规划偏好").performClick()
        composeRule.onNodeWithContentDescription("路线偏好 躲避拥堵").assertIsSelected()
        composeRule.onNodeWithContentDescription("路线偏好 不走高速").performClick()
        composeRule.onNodeWithText("规划驾车路线").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { routeRepository.requestCount == 1 }
        composeRule.runOnIdle {
            val expected = DriveRouteOptions(avoidCongestion = true, avoidHighway = true)
            assertTrue(savedOptions == expected)
            assertTrue(routeRepository.lastRequest?.driveOptions == expected)
        }
    }

    @Test
    fun routePlanner_placesWaypointsBetweenEndpointsAndCollapsesPreferences() {
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = FakeRoutePlanRepository(),
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("展开规划偏好").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("路线偏好 躲避拥堵").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("添加途经点").performClick()

        val originBounds = composeRule.onNodeWithContentDescription("起点 地点")
            .fetchSemanticsNode().boundsInRoot
        val waypointBounds = composeRule.onNodeWithContentDescription("途经点 1 地点")
            .fetchSemanticsNode().boundsInRoot
        val destinationBounds = composeRule.onNodeWithContentDescription("终点 地点")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(originBounds.bottom <= waypointBounds.top)
        assertTrue(waypointBounds.bottom <= destinationBounds.top)
    }

    @Test
    fun routePlanner_requiresSelectingTypedWaypoint() {
        val routeRepository = FakeRoutePlanRepository()
        composeRule.setContent {
            SimpleMapTheme {
                RoutePlannerPanel(
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = routeRepository,
                    initialOrigin = origin,
                    initialDestination = destination,
                    onRouteSelected = {},
                    onRouteCleared = {},
                    onStartNavigation = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加途经点").performClick()
        composeRule.onNodeWithContentDescription("途经点 1 地点").performTextInput("尚未确认")

        composeRule.onNodeWithText("请从搜索结果中选择途经点").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("步行").performClick()
        composeRule.onNodeWithContentDescription("驾车").performClick()
        composeRule.onNodeWithText("规划驾车路线").assertIsNotEnabled()
        composeRule.runOnIdle { assertTrue(routeRepository.requestCount == 0) }
    }
}

private class FakeRoutePlaceRepository(
    private val origin: Place,
    private val destination: Place,
    private val waypoint: Place? = null,
    private val secondWaypoint: Place? = null,
) : PlaceRepository {
    override fun search(query: String, city: String): Result<List<Place>> = Result.success(
        when {
            query.contains("东站") -> listOf(origin)
            query.contains("文化广场") && waypoint != null -> listOf(waypoint)
            query.contains("武林广场") && secondWaypoint != null -> listOf(secondWaypoint)
            else -> listOf(destination)
        },
    )
}

private class FakeRoutePlanRepository : RoutePlanRepository {
    var requestCount = 0
    var lastRequest: RouteRequest? = null

    override fun plan(request: RouteRequest): Result<List<RoutePlan>> {
        requestCount += 1
        lastRequest = request
        return Result.success(
        listOf(
            RoutePlan(
                id = "walk-0",
                mode = request.mode,
                durationSeconds = 2_520,
                distanceMeters = 4_200,
                costYuan = null,
                summary = "推荐步行路线",
                steps = listOf("向西步行 200 米", "沿湖滨路继续前行"),
                polyline = listOf(
                    RoutePoint(request.origin.latitude, request.origin.longitude),
                    RoutePoint(request.destination.latitude, request.destination.longitude),
                ),
            ),
        ),
    )
    }
}

private class MultipleRoutePlanRepository : RoutePlanRepository {
    override fun plan(request: RouteRequest): Result<List<RoutePlan>> = Result.success(
        listOf("推荐路线", "更快路线", "备用路线", "第四路线").mapIndexed { index, summary ->
            RoutePlan(
                id = "route-$index",
                mode = request.mode,
                durationSeconds = 1_200L + index * 120L,
                distanceMeters = 8_000 + index * 500,
                costYuan = null,
                summary = summary,
                steps = listOf("沿测试道路前行"),
                polyline = listOf(
                    RoutePoint(request.origin.latitude, request.origin.longitude),
                    RoutePoint(request.destination.latitude, request.destination.longitude),
                ),
            )
        },
    )
}

private class DelayedRoutePlanRepository : RoutePlanRepository {
    private val requestStarted = CountDownLatch(1)
    private val releaseRequest = CountDownLatch(1)

    override fun plan(request: RouteRequest): Result<List<RoutePlan>> {
        requestStarted.countDown()
        releaseRequest.await(5, TimeUnit.SECONDS)
        return Result.success(
            listOf(
                RoutePlan(
                    id = "delayed-route",
                    mode = request.mode,
                    durationSeconds = 600,
                    distanceMeters = 1_000,
                    costYuan = null,
                    summary = "延迟路线",
                    steps = emptyList(),
                    polyline = listOf(
                        RoutePoint(request.origin.latitude, request.origin.longitude),
                        RoutePoint(request.destination.latitude, request.destination.longitude),
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