package com.simplemap.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.simplemap.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.offline.OfflineCity
import com.simplemap.offline.OfflineDownloadState
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.offline.canDownloadOfflineMap
import com.simplemap.offline.downloadedOfflineBytes
import com.simplemap.offline.rememberNetworkStatus
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.FavoritePlace
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.ui.theme.panelBorder
import com.simplemap.ui.theme.sectionSurface
import com.simplemap.ui.theme.sectionSurfaceEmphasis
import com.simplemap.ui.theme.trafficClear
import com.simplemap.ui.theme.trafficJam
import com.simplemap.ui.theme.trafficSlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class ProfileSection(val label: String, val summary: String) {
    Favorites("收藏", "快捷管理常用地点与分组。"),
    Offline("离线地图", "查看城市包、下载条件与可用容量。"),
    Settings("设置", "管理主题、导航提醒、隐私与本地数据。"),
}

@Composable
internal fun ProfilePanel(
    favoriteStore: FavoritePlaceStore,
    settingsStore: NavigationSettingsStore,
    offlineRepository: OfflineMapRepository?,
    offlineUnavailableMessage: String?,
    destroyOfflineRepositoryOnDispose: Boolean,
    onNavigateTo: (Place) -> Unit,
    onFavoritesChanged: (List<Place>) -> Unit,
    onClearLocalData: suspend () -> Boolean,
    onLocalDataCleared: () -> Unit,
    onRevokePrivacyConsent: suspend () -> Boolean,
    onPrivacyRevoked: () -> Unit,
    onSettingsChanged: (NavigationSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var section by remember { mutableStateOf<ProfileSection?>(null) }
    var favorites by remember(favoriteStore) { mutableStateOf<List<FavoritePlace>>(emptyList()) }
    var settings by remember(settingsStore) { mutableStateOf(NavigationSettings()) }
    val settingsSaveMutex = remember(settingsStore) { Mutex() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(favoriteStore, settingsStore) {
        favorites = withContext(Dispatchers.IO) { favoriteStore.loadFavorites() }
        onFavoritesChanged(favorites.map(FavoritePlace::place))
        settings = withContext(Dispatchers.IO) { settingsStore.load() }
    }

    DisposableEffect(offlineRepository) {
        onDispose {
            if (destroyOfflineRepositoryOnDispose) offlineRepository?.destroy()
        }
    }

    BackHandler(enabled = section != null) { section = null }

    Box(modifier = modifier.fillMaxSize()) {
        val activeSection = section
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shape = RectangleShape,
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = FloatingNavigationClearance),
            ) {
                if (activeSection == null) {
                    Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "收藏地点、离线地图与应用设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    ProfileSectionList(
                        favoriteCount = favorites.size,
                        onSectionSelected = { section = it },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { section = null },
                            modifier = Modifier.semantics { contentDescription = "返回我的列表" },
                        ) {
                            Text("‹ 返回")
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(activeSection.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                activeSection.summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                when (activeSection) {
                    ProfileSection.Favorites -> FavoritesSection(
                        favorites = favorites,
                        onNavigateTo = onNavigateTo,
                        onRemove = { place ->
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { favoriteStore.remove(place.id) }) {
                                    val removed = favorites.firstOrNull { it.place.id == place.id }
                                    favorites = favorites.filterNot { it.place.id == place.id }
                                    onFavoritesChanged(favorites.map(FavoritePlace::place))
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已移除 ${place.name}",
                                        actionLabel = "撤销",
                                    )
                                    if (result == SnackbarResult.ActionPerformed &&
                                        removed != null && withContext(Dispatchers.IO) {
                                            favoriteStore.save(removed.place, removed.group)
                                        }
                                    ) {
                                        favorites = favorites + removed
                                        onFavoritesChanged(favorites.map(FavoritePlace::place))
                                    }
                                }
                            }
                        },
                        onGroupChanged = { favorite, group ->
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { favoriteStore.setGroup(favorite.place.id, group) }) {
                                    favorites = withContext(Dispatchers.IO) { favoriteStore.loadFavorites() }
                                    onFavoritesChanged(favorites.map(FavoritePlace::place))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )

                    ProfileSection.Offline -> if (offlineRepository != null) {
                        OfflineMapsSection(
                            repository = offlineRepository,
                            wifiOnly = settings.wifiOnlyOfflineDownloads,
                            onWifiOnlyChanged = { wifiOnly ->
                                val updated = settings.copy(wifiOnlyOfflineDownloads = wifiOnly)
                                settings = updated
                                onSettingsChanged(updated)
                                coroutineScope.launch {
                                    val saved = settingsSaveMutex.withLock {
                                        withContext(Dispatchers.IO) { settingsStore.save(updated) }
                                    }
                                    if (!saved) {
                                        snackbarHostState.showSnackbar("设置保存失败，请重试")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    text = offlineUnavailableMessage ?: "离线地图服务暂不可用",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    ProfileSection.Settings -> SettingsSection(
                        settings = settings,
                        onChanged = { updated ->
                            settings = updated
                            onSettingsChanged(updated)
                            coroutineScope.launch {
                                val saved = settingsSaveMutex.withLock {
                                    withContext(Dispatchers.IO) { settingsStore.save(updated) }
                                }
                                if (!saved) {
                                    snackbarHostState.showSnackbar("设置保存失败，请重试")
                                }
                            }
                        },
                        onClearLocalData = {
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { onClearLocalData() }) {
                                    favorites = emptyList()
                                    settings = NavigationSettings()
                                    onFavoritesChanged(emptyList())
                                    onLocalDataCleared()
                                }
                            }
                        },
                        onRevokePrivacyConsent = {
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { onRevokePrivacyConsent() }) {
                                    onPrivacyRevoked()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    null -> Unit
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = FloatingNavigationClearance - 8.dp),
        )
    }
}

@Composable
private fun ProfileSectionList(
    favoriteCount: Int,
    onSectionSelected: (ProfileSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(ProfileSection.entries, key = ProfileSection::name) { item ->
            val subtitle = when (item) {
                ProfileSection.Favorites -> "$favoriteCount 个已收藏地点"
                else -> item.summary.removeSuffix("。")
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSectionSelected(item) }
                    .semantics { contentDescription = "打开${item.label}" },
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFDCE7F5)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 34.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(item.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun FavoriteListGroup(
    favorites: List<FavoritePlace>,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE7F5)),
    ) {
        Column {
            favorites.forEachIndexed { index, favorite ->
                if (index > 0) HorizontalDivider(color = Color(0xFFE4ECF6))
                FavoriteRow(
                    favorite = favorite,
                    onNavigateTo = onNavigateTo,
                    onRemove = onRemove,
                    onGroupChanged = onGroupChanged,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.sectionSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun FavoritesSection(
    favorites: List<FavoritePlace>,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            SectionCard {
                Text("暂无收藏地点", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FavoriteGroup.entries.forEach { group ->
            val groupedFavorites = favorites.filter { it.group == group }
            if (groupedFavorites.isNotEmpty()) {
                item(key = "header-${group.name}") {
                    Text(
                        text = group.label,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "group-${group.name}") {
                    FavoriteListGroup(
                        favorites = groupedFavorites,
                        onNavigateTo = onNavigateTo,
                        onRemove = onRemove,
                        onGroupChanged = onGroupChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoritePlace,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
) {
    val place = favorite.place
    Column(
        modifier = Modifier
            .clickable(role = Role.Button) { onNavigateTo(place) }
            .semantics { contentDescription = "规划到 ${place.name}" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val detail = place.address.ifBlank { place.district }
                if (detail.isNotBlank()) {
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { onRemove(place) },
            ) { Text("移除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FavoriteGroup.entries.forEach { targetGroup ->
                CompactChoiceChip(
                    text = targetGroup.label,
                    selected = favorite.group == targetGroup,
                    onClick = { onGroupChanged(favorite, targetGroup) },
                    enabled = favorite.group != targetGroup,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContentColor = MaterialTheme.colorScheme.secondary,
                    unselectedContainerColor = MaterialTheme.colorScheme.sectionSurfaceEmphasis,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfflineMapsSection(
    repository: OfflineMapRepository,
    wifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val networkStatus = rememberNetworkStatus()
    val downloadAllowed = canDownloadOfflineMap(networkStatus, wifiOnly)
    var query by remember { mutableStateOf("") }
    var cities by remember(repository) { mutableStateOf<List<OfflineCity>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        val result = withContext(Dispatchers.IO) { repository.loadCities() }
        cities = result.getOrDefault(emptyList())
        message = result.exceptionOrNull()?.localizedMessage
    }

    DisposableEffect(repository) {
        repository.setOnChanged { changed ->
            cities = cities.map { if (it.name == changed.name) changed else it }
            if (cities.none { it.name == changed.name }) cities = cities + changed
        }
        onDispose { repository.setOnChanged {} }
    }

    val installedBytes = downloadedOfflineBytes(cities)
    val totalBytes = cities.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val filteredCities = remember(cities, query) {
        if (query.isBlank()) cities.take(30) else cities.filter { it.name.contains(query.trim(), true) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("离线包容量", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "已下载 ${formatOfflineSize(installedBytes)} / 全部 ${formatOfflineSize(totalBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Text("仅 Wi‑Fi 下载", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged,
                    modifier = Modifier.semantics { contentDescription = "仅 Wi-Fi 下载" },
                )
            }
            Surface(
                color = if (downloadAllowed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.dp,
                    if (downloadAllowed) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                    },
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = when {
                        !networkStatus.available -> "当前离线，可继续使用已下载城市包"
                        wifiOnly && !networkStatus.connectedViaWifi -> "已开启仅 Wi‑Fi 下载，连接 Wi‑Fi 后可继续"
                        else -> "当前网络可下载或更新城市包"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = if (downloadAllowed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                )
            }
            Text(
                "城市包仅提供离线底图；地点搜索、路线规划和实时导航可能仍需网络。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "搜索离线城市" },
            placeholder = { Text("搜索城市") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
        )

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (filteredCities.isEmpty()) {
            SectionCard {
                Text(
                    text = if (query.isBlank()) "暂无可显示的城市包" else "没有匹配的城市",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredCities, key = OfflineCity::code) { city ->
                    OfflineCityItem(
                        city = city,
                        downloadAllowed = downloadAllowed,
                        onDownload = {
                            coroutineScope.launch {
                                val result = withContext(Dispatchers.IO) { repository.download(city.name) }
                                message = result.exceptionOrNull()?.localizedMessage
                            }
                        },
                        onPause = { repository.pause(city.name) },
                        onRemove = { repository.remove(city.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineCityItem(
    city: OfflineCity,
    downloadAllowed: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit,
) {
    var removalConfirmationVisible by remember { mutableStateOf(false) }

    SectionCard(containerColor = offlineCityContainerColor(city.state)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "%.1f MB · ${offlineStateLabel(city.state)}".format(city.sizeBytes / 1024f / 1024f),
                    color = offlineStateTone(city.state),
                    fontSize = 12.sp,
                )
            }
            when (city.state) {
                OfflineDownloadState.Downloading,
                OfflineDownloadState.Waiting,
                -> TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = onPause,
                ) { Text("暂停") }

                OfflineDownloadState.Installed -> TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { removalConfirmationVisible = true },
                ) { Text("删除") }

                else -> TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = onDownload,
                    enabled = downloadAllowed,
                ) { Text("下载") }
            }
        }
        if (city.state == OfflineDownloadState.Downloading || city.state == OfflineDownloadState.Waiting) {
            LinearProgressIndicator(
                progress = { city.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }

    if (removalConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { removalConfirmationVisible = false },
            title = { Text("删除 ${city.name} 离线包？") },
            text = { Text("删除后需要联网重新下载才能离线查看该城市底图。") },
            confirmButton = {
                TextButton(onClick = {
                    removalConfirmationVisible = false
                    onRemove()
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { removalConfirmationVisible = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun offlineCityContainerColor(state: OfflineDownloadState) = when (state) {
    OfflineDownloadState.Downloading,
    OfflineDownloadState.Waiting,
    -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    OfflineDownloadState.Installed -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    else -> MaterialTheme.colorScheme.surface
}

internal fun formatOfflineSize(sizeBytes: Long): String {
    val safeBytes = sizeBytes.coerceAtLeast(0L)
    val gibibytes = safeBytes / 1024f / 1024f / 1024f
    return if (gibibytes >= 1f) {
        "%.1f GB".format(gibibytes)
    } else {
        "%.1f MB".format(safeBytes / 1024f / 1024f)
    }
}

@Composable
private fun SettingsSection(
    settings: NavigationSettings,
    onChanged: (NavigationSettings) -> Unit,
    onClearLocalData: () -> Unit,
    onRevokePrivacyConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingCommand by remember { mutableStateOf<SettingsCommand?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Text("显示", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("主题模式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            SettingChoiceRow(
                options = NavigationThemeMode.entries,
                selected = settings.themeMode,
                label = NavigationThemeMode::label,
                onSelect = { mode -> onChanged(settings.copy(themeMode = mode, nightMode = mode == NavigationThemeMode.Night)) },
            )
            Text(
                "按时间自动在 19:00 至次日 06:00 使用夜间主题；导航进入隧道时会临时切换。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        SectionCard {
            Text("导航语音与提醒", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            SettingToggle("语音导航", "播报转向、路况与到达提醒", settings.resolvedVoiceGuidanceLevel != VoiceGuidanceLevel.Muted) {
                val level = if (it) VoiceGuidanceLevel.Detailed else VoiceGuidanceLevel.Muted
                onChanged(settings.copy(voiceGuidance = it, voiceGuidanceLevel = level))
            }
            SettingChoiceRow(
                options = VoiceGuidanceLevel.entries,
                selected = settings.resolvedVoiceGuidanceLevel,
                label = VoiceGuidanceLevel::label,
                onSelect = { level ->
                    onChanged(settings.copy(voiceGuidance = level != VoiceGuidanceLevel.Muted, voiceGuidanceLevel = level))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingToggle("静音时段", "在设定时间内暂停常规导航播报", settings.quietHoursEnabled) {
                onChanged(settings.copy(quietHoursEnabled = it))
            }
            if (settings.quietHoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCommandButton(
                        title = "开始 ${formatMinutesOfDay(settings.quietHoursStartMinutes)}",
                        description = "选择开始时间",
                        modifier = Modifier.weight(1f),
                    ) {
                        showTimePicker(context, settings.quietHoursStartMinutes) { minutes ->
                            onChanged(settings.copy(quietHoursStartMinutes = minutes))
                        }
                    }
                    SettingsCommandButton(
                        title = "结束 ${formatMinutesOfDay(settings.quietHoursEndMinutes)}",
                        description = "选择结束时间",
                        modifier = Modifier.weight(1f),
                    ) {
                        showTimePicker(context, settings.quietHoursEndMinutes) { minutes ->
                            onChanged(settings.copy(quietHoursEndMinutes = minutes))
                        }
                    }
                }
            }
            SettingToggle("重要提示语音", "单独控制严重拥堵等提醒；开启后静音时段仍播报", settings.importantAlertsEnabled) {
                onChanged(settings.copy(importantAlertsEnabled = it))
            }
            SettingToggle("实时路况", "在地图和导航路线中显示拥堵", settings.trafficLayer) {
                onChanged(settings.copy(trafficLayer = it))
            }
            Text("导航视角", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavigationPerspectiveMode.entries.forEach { mode ->
                    CompactChoiceChip(
                        text = mode.label,
                        selected = settings.perspectiveMode == mode,
                        onClick = { onChanged(settings.copy(perspectiveMode = mode)) },
                        modifier = Modifier.weight(1f),
                        role = Role.RadioButton,
                    )
                }
            }
            SettingToggle("路线状态提醒", "偏航或拥堵重规划时显示提示", settings.routeAlerts) {
                onChanged(settings.copy(routeAlerts = it))
            }
        }

        SectionCard {
            Text("关于", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("SimpleMap", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("当前版本", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("构建 ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }

        SectionCard {
            Text("隐私与权限", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "地图服务仅在你同意隐私说明后初始化；定位权限由系统设置管理。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            SettingsCommandButton("系统应用权限", "管理定位、通知等系统权限") {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
            SettingsCommandButton("清除本地数据", "删除收藏、行程、停车位置与导航设置") {
                pendingCommand = SettingsCommand.ClearData
            }
            SettingsCommandButton(
                title = "撤回隐私同意",
                description = "下次启动时重新显示隐私说明",
                destructive = true,
            ) {
                pendingCommand = SettingsCommand.RevokeConsent
            }
        }
    }

    pendingCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(if (command == SettingsCommand.ClearData) "清除本地数据？" else "撤回隐私同意？") },
            text = {
                Text(
                    if (command == SettingsCommand.ClearData) {
                        "收藏、行程、停车位置和导航设置将被删除，此操作无法撤销。"
                    } else {
                        "应用将关闭。下次启动前不会再次初始化地图服务。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCommand = null
                        if (command == SettingsCommand.ClearData) onClearLocalData() else onRevokePrivacyConsent()
                    },
                ) { Text(if (command == SettingsCommand.ClearData) "确认清除" else "确认撤回") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("取消") }
            },
        )
    }
}

private enum class SettingsCommand { ClearData, RevokeConsent }

@Composable
private fun SettingsCommandButton(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title },
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.sectionSurfaceEmphasis,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                description,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun <T> SettingChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            CompactChoiceChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .semantics {
                contentDescription = text
                this.role = role
                this.selected = selected
            },
        color = if (selected) selectedContainerColor else unselectedContainerColor,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (selected) selectedContentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text,
                color = if (selected) selectedContentColor else unselectedContentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}

private fun showTimePicker(context: android.content.Context, initialMinutes: Int, onSelected: (Int) -> Unit) {
    val safeMinutes = initialMinutes.coerceIn(0, 24 * 60 - 1)
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(hour * 60 + minute) },
        safeMinutes / 60,
        safeMinutes % 60,
        true,
    ).show()
}

internal fun formatMinutesOfDay(minutes: Int): String {
    val safeMinutes = minutes.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(safeMinutes / 60, safeMinutes % 60)
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun offlineStateTone(state: OfflineDownloadState) = when (state) {
    OfflineDownloadState.NotDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant
    OfflineDownloadState.Waiting,
    OfflineDownloadState.Downloading,
    -> MaterialTheme.colorScheme.primary

    OfflineDownloadState.Paused,
    OfflineDownloadState.UpdateAvailable,
    -> MaterialTheme.colorScheme.trafficSlow

    OfflineDownloadState.Installed -> MaterialTheme.colorScheme.trafficClear
    OfflineDownloadState.Failed -> MaterialTheme.colorScheme.trafficJam
}

private fun offlineStateLabel(state: OfflineDownloadState) = when (state) {
    OfflineDownloadState.NotDownloaded -> "未下载"
    OfflineDownloadState.Waiting -> "等待中"
    OfflineDownloadState.Downloading -> "下载中"
    OfflineDownloadState.Paused -> "已暂停"
    OfflineDownloadState.Installed -> "可离线使用"
    OfflineDownloadState.UpdateAvailable -> "有更新"
    OfflineDownloadState.Failed -> "下载失败"
}
