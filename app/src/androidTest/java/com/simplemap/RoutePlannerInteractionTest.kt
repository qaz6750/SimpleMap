package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoutePlannerInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val origin = place("origin", "杭州东站", 30.2920, 120.2120)
    private val destination = place("destination", "西湖风景名胜区", 30.2511, 120.1269)

    @Before
    fun setContent() {
        composeRule.setContent {
            SimpleMapTheme {
                SimpleMapApp(
                    showLiveMap = false,
                    placeRepository = FakeRoutePlaceRepository(origin, destination),
                    routePlanRepository = FakeRoutePlanRepository(),
                )
            }
        }
        composeRule.onNodeWithContentDescription("路线").performClick()
    }

    @Test
    fun routePlanner_selectsEndpointsAndComparesWalkRoute() {
        assertTrue(composeRule.onAllNodes(hasContentDescription("地图")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasContentDescription("行程")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasContentDescription("我的")).fetchSemanticsNodes().isEmpty())

        val originField = composeRule.onNodeWithContentDescription("起点 地点")
        originField.performTextInput("杭州东站")
        originField.performImeAction()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("选择地点 杭州东站"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择地点 杭州东站").performClick()

        val destinationField = composeRule.onNodeWithContentDescription("终点 地点")
        destinationField.performTextInput("西湖")
        destinationField.performImeAction()
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
        composeRule.onNodeWithText("开始导航").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("驾车").performClick().assertIsSelected()
        composeRule.onNodeWithText("规划驾车路线").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("开始导航")).fetchSemanticsNodes().isEmpty())
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
    override fun plan(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        city: String,
    ): Result<List<RoutePlan>> = Result.success(
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