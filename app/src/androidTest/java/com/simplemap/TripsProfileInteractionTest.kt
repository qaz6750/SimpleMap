package com.simplemap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplemap.offline.OfflineCity
import com.simplemap.offline.OfflineDownloadState
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.trips.TripHistoryStore
import com.simplemap.trips.TripRecord
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TripsProfileInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val origin = place("origin", "杭州东站")
    private val destination = place("destination", "西湖风景名胜区")

    @Test
    fun trip_canBePlannedAgain() {
        composeRule.setAppContent()
        composeRule.onNodeWithContentDescription("行程").performClick()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("再次规划到 西湖风景名胜区").performClick()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
    }

    @Test
    fun profile_managesFavoriteSettingsAndOfflineMap() {
        val settingsStore = FakeSettingsStore()
        val offlineRepository = FakeOfflineRepository()
        composeRule.setAppContent(settingsStore, offlineRepository)
        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithContentDescription("规划到 西湖风景名胜区").assertIsDisplayed()

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithContentDescription("语音导航").performClick()
        composeRule.runOnIdle { assertFalse(settingsStore.settings.voiceGuidance) }

        composeRule.onNodeWithText("离线地图").performClick()
        composeRule.onNodeWithText("杭州市").assertIsDisplayed()
        composeRule.onNodeWithText("下载").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { offlineRepository.downloadedCity == "杭州市" }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setAppContent(
        settingsStore: FakeSettingsStore = FakeSettingsStore(),
        offlineRepository: FakeOfflineRepository = FakeOfflineRepository(),
    ) {
        setContent {
            SimpleMapTheme {
                SimpleMapApp(
                    showLiveMap = false,
                    favoritePlaceStore = FakeFavoriteStore(destination),
                    tripHistoryStore = FakeTripStore(origin, destination),
                    navigationSettingsStore = settingsStore,
                    offlineMapRepository = offlineRepository,
                )
            }
        }
    }
}

private class FakeFavoriteStore(private val favorite: Place) : FavoritePlaceStore {
    override fun load() = listOf(favorite)
    override fun save(place: Place) = true
    override fun remove(placeId: String) = true
}

private class FakeTripStore(origin: Place, destination: Place) : TripHistoryStore {
    private val trips = listOf(
        TripRecord(
            id = "trip-1",
            startedAtMillis = 1_700_000_000_000,
            origin = origin,
            destination = destination,
            mode = RouteMode.Drive,
            durationSeconds = 1_800,
            distanceMeters = 12_000,
        ),
    )

    override fun load() = trips
    override fun add(origin: Place, destination: Place, plan: RoutePlan) = true
    override fun clear() = true
}

private class FakeSettingsStore : NavigationSettingsStore {
    var settings = NavigationSettings()

    override fun load() = settings

    override fun save(settings: NavigationSettings): Boolean {
        this.settings = settings
        return true
    }
}

private class FakeOfflineRepository : OfflineMapRepository {
    @Volatile
    var downloadedCity: String? = null

    override fun loadCities() = Result.success(
        listOf(
            OfflineCity(
                code = "0571",
                name = "杭州市",
                sizeBytes = 128L * 1024 * 1024,
                progress = 0,
                state = OfflineDownloadState.NotDownloaded,
            ),
        ),
    )

    override fun download(cityName: String): Result<Unit> {
        downloadedCity = cityName
        return Result.success(Unit)
    }

    override fun pause(cityName: String) = Unit
    override fun remove(cityName: String) = Unit
    override fun setOnChanged(listener: (OfflineCity) -> Unit) = Unit
    override fun destroy() = Unit
}

private fun place(id: String, name: String) = Place(
    id = id,
    name = name,
    address = "测试地址",
    district = "杭州市",
    category = "",
    phone = "",
    latitude = 30.25,
    longitude = 120.16,
    distanceMeters = null,
)