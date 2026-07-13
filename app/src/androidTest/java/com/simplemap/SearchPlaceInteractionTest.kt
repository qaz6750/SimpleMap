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
                )
            }
        }
    }

    @Test
    fun searchResult_canBeFavoritedAndUsedAsRouteDestination() {
        composeRule.onNodeWithContentDescription("搜索地点、公交或路线").performClick()
        composeRule.onNodeWithText("输入地点、公交或路线").performTextInput("西湖")
        composeRule.onNodeWithContentDescription("搜索").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("西湖风景名胜区")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("查看地点 西湖风景名胜区").performClick()
        composeRule.onNodeWithText("风景名胜").assertIsDisplayed()
        composeRule.onNodeWithText("2.4 公里").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { "west-lake" in favoriteStore.savedIds }
        composeRule.onNodeWithText("去这里").performClick()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
    }
}

private class FakePlaceRepository(private val place: Place) : PlaceRepository {
    override fun search(query: String, city: String): Result<List<Place>> =
        Result.success(listOf(place))
}

private class FakeFavoritePlaceStore : FavoritePlaceStore {
    val savedIds = mutableSetOf<String>()

    override fun load(): List<Place> = emptyList()

    override fun save(place: Place): Boolean {
        savedIds += place.id
        return true
    }

    override fun remove(placeId: String): Boolean {
        savedIds -= placeId
        return true
    }
}