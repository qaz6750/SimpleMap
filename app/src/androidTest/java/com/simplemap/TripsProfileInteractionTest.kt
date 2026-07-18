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
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.FavoritePlace
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.trips.TripHistoryStore
import com.simplemap.trips.TripRecord
import com.simplemap.trips.TripStatus
import com.simplemap.ui.SimpleMapApp
import com.simplemap.ui.theme.SimpleMapTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("已到达").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("查看行程 西湖风景名胜区").performClick()
        composeRule.onNodeWithText("行程复盘").assertIsDisplayed()
        composeRule.onNodeWithText("24 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("仅保存在本机，记录行程摘要，不保存轨迹点。").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("再次规划到 西湖风景名胜区").performClick()
        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
    }

    @Test
    fun tripHistoryShowsFailedSimulationStatus() {
        composeRule.setAppContent(
            tripStore = FakeTripStore(
                origin = origin,
                destination = destination,
                status = TripStatus.Failed,
                simulated = true,
            ),
        )
        composeRule.onNodeWithContentDescription("行程").performClick()

        composeRule.onNodeWithText("模拟 · 失败").assertIsDisplayed()
    }

    @Test
    fun tripHistoryRequiresConfirmationBeforeClearing() {
        val tripStore = FakeTripStore(origin, destination)
        composeRule.setAppContent(tripStore = tripStore)
        composeRule.onNodeWithContentDescription("行程").performClick()

        composeRule.onNodeWithText("清空").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertFalse(tripStore.cleared) }

        composeRule.onNodeWithText("清空").performClick()
        composeRule.onNodeWithText("确认清空").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { tripStore.cleared }
        composeRule.onNodeWithText("开始一次导航后，路线会出现在这里").assertIsDisplayed()
    }

    @Test
    fun profile_managesFavoriteSettingsAndOfflineMap() {
        val settingsStore = FakeSettingsStore()
        val offlineRepository = FakeOfflineRepository()
        composeRule.setAppContent(settingsStore, offlineRepository)
        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithContentDescription("规划到 西湖风景名胜区").assertIsDisplayed()

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithContentDescription("按时间自动").performClick()
        composeRule.onNodeWithContentDescription("简洁播报").performClick()
        composeRule.onNodeWithContentDescription("静音时段").performClick()
        composeRule.onNodeWithContentDescription("重要提示语音").performClick()
        composeRule.onNodeWithContentDescription("语音导航").performClick()
        composeRule.runOnIdle {
            assertTrue(settingsStore.settings.themeMode == NavigationThemeMode.Automatic)
            assertTrue(settingsStore.settings.voiceGuidanceLevel == VoiceGuidanceLevel.Muted)
            assertTrue(settingsStore.settings.quietHoursEnabled)
            assertFalse(settingsStore.settings.importantAlertsEnabled)
            assertFalse(settingsStore.settings.voiceGuidance)
        }

        composeRule.onNodeWithText("离线地图").performClick()
        composeRule.onNodeWithText("杭州市").assertIsDisplayed()
        composeRule.onNodeWithText("已下载 0.0 MB / 全部 128.0 MB").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("仅 Wi-Fi 下载").performClick()
        composeRule.runOnIdle { assertFalse(settingsStore.settings.wifiOnlyOfflineDownloads) }
        composeRule.onNodeWithText("下载").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { offlineRepository.downloadedCity == "杭州市" }
    }

    @Test
    fun profile_groupsFavoriteAndPlansWithOneTap() {
        val favoriteStore = FakeFavoriteStore(destination)
        composeRule.setAppContent(favoriteStore = favoriteStore)
        composeRule.onNodeWithContentDescription("我的").performClick()

        composeRule.onNodeWithText("收藏夹").assertIsDisplayed()
        composeRule.onNodeWithText("公司").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { favoriteStore.group == FavoriteGroup.Work }
        composeRule.onNodeWithContentDescription("规划到 西湖风景名胜区").performClick()

        composeRule.onNodeWithContentDescription("终点 地点").assertIsDisplayed()
        composeRule.onNodeWithText("西湖风景名胜区").assertIsDisplayed()
    }

    @Test
    fun installedOfflineMapRequiresDeleteConfirmation() {
        val offlineRepository = FakeOfflineRepository(installed = true)
        composeRule.setAppContent(offlineRepository = offlineRepository)
        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithText("离线地图").performClick()
        composeRule.onNodeWithText("已下载 128.0 MB / 全部 128.0 MB").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertFalse(offlineRepository.removed) }

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.runOnIdle { assertTrue(offlineRepository.removed) }
    }

    @Test
    fun profile_confirmsAndClearsLocalData() {
        val favoriteStore = FakeFavoriteStore(destination)
        val tripStore = FakeTripStore(origin, destination)
        val settingsStore = FakeSettingsStore(NavigationSettings(voiceGuidance = false))
        composeRule.setAppContent(
            settingsStore = settingsStore,
            favoriteStore = favoriteStore,
            tripStore = tripStore,
        )
        composeRule.onNodeWithContentDescription("我的").performClick()
        composeRule.onNodeWithText("设置").performClick()

        composeRule.onNodeWithContentDescription("清除本地数据").performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertFalse(favoriteStore.cleared)
            assertFalse(tripStore.cleared)
        }

        composeRule.onNodeWithContentDescription("清除本地数据").performClick()
        composeRule.onNodeWithText("确认清除").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            favoriteStore.cleared && tripStore.cleared
        }
        composeRule.runOnIdle { assertTrue(settingsStore.settings == NavigationSettings()) }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setAppContent(
        settingsStore: FakeSettingsStore = FakeSettingsStore(),
        offlineRepository: FakeOfflineRepository = FakeOfflineRepository(),
        favoriteStore: FakeFavoriteStore = FakeFavoriteStore(destination),
        tripStore: FakeTripStore = FakeTripStore(origin, destination),
    ) {
        setContent {
            SimpleMapTheme {
                SimpleMapApp(
                    showLiveMap = false,
                    favoritePlaceStore = favoriteStore,
                    tripHistoryStore = tripStore,
                    navigationSettingsStore = settingsStore,
                    offlineMapRepository = offlineRepository,
                )
            }
        }
    }
}

private class FakeFavoriteStore(private val favorite: Place) : FavoritePlaceStore {
    var cleared = false
    var group = FavoriteGroup.Saved
    override fun load() = if (cleared) emptyList() else listOf(favorite)
    override fun loadFavorites() = if (cleared) emptyList() else listOf(FavoritePlace(favorite, group))
    override fun save(place: Place) = true
    override fun save(place: Place, group: FavoriteGroup): Boolean {
        this.group = group
        return true
    }
    override fun setGroup(placeId: String, group: FavoriteGroup): Boolean {
        this.group = group
        return true
    }
    override fun remove(placeId: String) = true
    override fun clear(): Boolean {
        cleared = true
        return true
    }
}

private class FakeTripStore(
    origin: Place,
    destination: Place,
    status: TripStatus = TripStatus.Arrived,
    simulated: Boolean = false,
) : TripHistoryStore {
    private val trips = listOf(
        TripRecord(
            id = "trip-1",
            startedAtMillis = 1_700_000_000_000,
            completedAtMillis = 1_700_000_001_800,
            origin = origin,
            destination = destination,
            mode = RouteMode.Drive,
            durationSeconds = 1_800,
            distanceMeters = 12_000,
            status = status,
            simulated = simulated,
        ),
    )
    var cleared = false

    override fun load() = if (cleared) emptyList() else trips
    override fun add(record: TripRecord) = true
    override fun clear(): Boolean {
        cleared = true
        return true
    }
}

private class FakeSettingsStore(initialSettings: NavigationSettings = NavigationSettings()) : NavigationSettingsStore {
    var settings = initialSettings

    override fun load() = settings

    override fun save(settings: NavigationSettings): Boolean {
        this.settings = settings
        return true
    }
}

private class FakeOfflineRepository(private val installed: Boolean = false) : OfflineMapRepository {
    @Volatile
    var downloadedCity: String? = null
    var removed = false

    override fun loadCities() = Result.success(
        listOf(
            OfflineCity(
                code = "0571",
                name = "杭州市",
                sizeBytes = 128L * 1024 * 1024,
                progress = 0,
                state = if (installed) OfflineDownloadState.Installed else OfflineDownloadState.NotDownloaded,
            ),
        ),
    )

    override fun download(cityName: String): Result<Unit> {
        downloadedCity = cityName
        return Result.success(Unit)
    }

    override fun pause(cityName: String) = Unit
    override fun remove(cityName: String) {
        removed = true
    }
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