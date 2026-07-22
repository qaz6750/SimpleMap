package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.offline.OfflineCity
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchPlaceInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val place = Place(
        id = "west-lake",
        name = "西湖风景名胜区",
        address = "龙井路1号",
        district = "杭州市 · 西湖区",
        category = "风景名胜",
        phone = "0571-12345678",
        latitude = 30.2511,
        longitude = 120.1269,
        distanceMeters = 2_400,
    )
    private val favoriteStore = FakeFavoritePlaceStore()

    @Before
    fun setContent() {
        composeRule.setContent {
            SimpleMapTheme {
                SimpleMapApp(
                    showLiveMap = false,
                    placeRepository = FakePlaceRepository(place),
                    favoritePlaceStore = favoriteStore,
                    offlineMapRepository = FakeOfflineMapRepository,
                )
            }
        }
    }

    @Test
    fun searchResult_canBeFavoritedAndUsedAsRouteDestination() {
        composeRule.onNodeWithContentDescription("搜索地点或路线").performClick()
        composeRule.onNodeWithText("输入地点或路线").performTextInput("西湖")
        composeRule.onNodeWithContentDescription("搜索").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("西湖风景名胜区")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("查看地点 西湖风景名胜区").performClick()
        composeRule.onNodeWithText("风景名胜").assertIsDisplayed()
        composeRule.onNodeWithText("2.4 公里").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("展开地图工具").assertDoesNotExist()
        composeRule.onNodeWithText("收藏").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" in favoriteStore.savedIds }
        composeRule.onNodeWithText("去这里").performClick()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
    }

    @Test
    fun searchDoesNotShowBusLinesOrStations() {
        composeRule.onNodeWithContentDescription("搜索地点或路线").performClick()
        composeRule.onNodeWithText("输入地点或路线").performTextInput("西湖")
        composeRule.onNodeWithContentDescription("搜索").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("西湖风景名胜区")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("公交线路").assertDoesNotExist()
        composeRule.onNodeWithText("少年宫").assertDoesNotExist()
    }

    @Test
    fun systemBackClosesSearchAndPlaceDetailsBeforeLeavingMap() {
        composeRule.onNodeWithContentDescription("搜索地点或路线").performClick()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithContentDescription("搜索地点或路线").assertIsDisplayed()

        openPlaceDetails()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("去这里").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("搜索地点或路线").assertIsDisplayed()
    }

    @Test
    fun removingFavoriteFromProfileUpdatesMapDetails() {
        openPlaceDetails()
        composeRule.onNodeWithText("收藏").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" in favoriteStore.savedIds }

        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithText("移除").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" !in favoriteStore.savedIds }

        composeRule.onNodeWithContentDescription("地图").performClick()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onNodeWithText("已收藏").assertDoesNotExist()
    }

    @Test
    fun removedFavoriteCanBeRestoredFromSnackbar() {
        openPlaceDetails()
        composeRule.onNodeWithText("收藏").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" in favoriteStore.savedIds }

        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithText("移除").performClick()
        composeRule.onNodeWithText("撤销").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" in favoriteStore.savedIds }
        composeRule.onNodeWithContentDescription("规划到 西湖风景名胜区").assertIsDisplayed()
    }

    private fun openPlaceDetails() {
        composeRule.onNodeWithContentDescription("搜索地点或路线").performClick()
        composeRule.onNodeWithText("输入地点或路线").performTextInput("西湖")
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("西湖风景名胜区")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("查看地点 西湖风景名胜区").performClick()
    }
}

private object FakeOfflineMapRepository : OfflineMapRepository {
    override fun loadCities(): Result<List<OfflineCity>> = Result.success(emptyList())
    override fun download(cityName: String): Result<Unit> = Result.success(Unit)
    override fun pause(cityName: String) = Unit
    override fun remove(cityName: String) = Unit
    override fun setOnChanged(listener: (OfflineCity) -> Unit) = Unit
    override fun destroy() = Unit
}

private class FakePlaceRepository(private val place: Place) : PlaceRepository {
    override fun search(query: String, city: String): Result<List<Place>> =
        Result.success(listOf(place))

}

private class FakeFavoritePlaceStore : FavoritePlaceStore {
    private val places = linkedMapOf<String, Place>()
    val savedIds: Set<String> get() = places.keys

    override fun load(): List<Place> = places.values.toList()

    override fun save(place: Place): Boolean {
        places[place.id] = place
        return true
    }

    override fun remove(placeId: String): Boolean {
        places.remove(placeId)
        return true
    }

    override fun clear(): Boolean {
        places.clear()
        return true
    }
}