package com.simplemap.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.FavoritePlace
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class ProfileSection(val label: String) {
    Favorites("收藏"),
    Offline("离线地图"),
    Settings("设置"),
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
    var section by remember { mutableStateOf(ProfileSection.Favorites) }
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

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = FloatingNavigationClearance),
            color = MaterialTheme.colorScheme.background,
            shape = RectangleShape,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
            Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileSection.entries.forEach { item ->
                    val selected = item == section
                    if (selected) {
                        Button(
                            onClick = { section = item },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    role = Role.Tab
                                    this.selected = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769E0)),
                        ) { Text(item.label) }
                    } else {
                        OutlinedButton(
                            onClick = { section = item },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    role = Role.Tab
                                    this.selected = false
                                },
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(item.label) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (section) {
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
                    Text(
                        text = offlineUnavailableMessage ?: "离线地图服务暂不可用",
                        modifier = Modifier.padding(vertical = 30.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ProfileSection.Settings -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SettingsSection(
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
                    )
                }
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = FloatingNavigationClearance),
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
        Text("暂无收藏地点", modifier = modifier.padding(vertical = 30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        FavoriteGroup.entries.forEach { group ->
            val groupedFavorites = favorites.filter { it.group == group }
            if (groupedFavorites.isNotEmpty()) {
                item(key = "header-${group.name}") {
                    Text(
                        text = group.label,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(groupedFavorites, key = { it.place.id }) { favorite ->
                val place = favorite.place
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onNavigateTo(place) }
                    .semantics { contentDescription = "规划到 ${place.name}" }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        place.address.ifBlank { place.district },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        FavoriteGroup.entries.forEach { targetGroup ->
                            TextButton(
                                onClick = { onGroupChanged(favorite, targetGroup) },
                                enabled = favorite.group != targetGroup,
                            ) { Text(targetGroup.label, fontSize = 12.sp) }
                        }
                    }
                }
                TextButton(onClick = { onRemove(place) }) { Text("移除") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

    Column(modifier = modifier) {
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
        Text("仅 Wi-Fi 下载", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Switch(
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChanged,
            modifier = Modifier.semantics { contentDescription = "仅 Wi-Fi 下载" },
        )
    }
    Spacer(Modifier.height(8.dp))

    Surface(
        color = if (downloadAllowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = when {
                !networkStatus.available -> "当前离线，可继续使用已下载城市包"
                wifiOnly && !networkStatus.connectedViaWifi -> "已开启仅 Wi-Fi 下载，连接 Wi-Fi 后可继续"
                else -> "当前网络可下载或更新城市包"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = if (downloadAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "城市包仅提供离线底图；地点搜索、路线规划和实时导航可能仍需网络。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "搜索离线城市" },
        placeholder = { Text("搜索城市") },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
    )
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    val filteredCities = remember(cities, query) {
        if (query.isBlank()) cities.take(30) else cities.filter { it.name.contains(query.trim(), true) }
    }
    LazyColumn(modifier = Modifier.weight(1f)) {
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

@Composable
private fun OfflineCityItem(
    city: OfflineCity,
    downloadAllowed: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit,
) {
    var removalConfirmationVisible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "%.1f MB · ${offlineStateLabel(city.state)}".format(city.sizeBytes / 1024f / 1024f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            when (city.state) {
                OfflineDownloadState.Downloading,
                OfflineDownloadState.Waiting,
                -> TextButton(onClick = onPause) { Text("暂停") }
                OfflineDownloadState.Installed -> TextButton(onClick = { removalConfirmationVisible = true }) { Text("删除") }
                else -> TextButton(onClick = onDownload, enabled = downloadAllowed) { Text("下载") }
            }
        }
        if (city.state == OfflineDownloadState.Downloading || city.state == OfflineDownloadState.Waiting) {
            LinearProgressIndicator(
                progress = { city.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1769E0),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
) {
    val context = LocalContext.current
    var pendingCommand by remember { mutableStateOf<SettingsCommand?>(null) }
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
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text("导航语音", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
    SettingToggle("路线状态提醒", "偏航或拥堵重规划时显示提示", settings.routeAlerts) {
        onChanged(settings.copy(routeAlerts = it))
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text("隐私与权限", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Text(
        "地图服务仅在你同意隐私说明后初始化；定位权限由系统设置管理。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(8.dp))
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
    SettingsCommandButton("撤回隐私同意", "下次启动时重新显示隐私说明", destructive = true) {
        pendingCommand = SettingsCommand.RevokeConsent
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
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title },
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> SettingChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .semantics { contentDescription = label(option) },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    label(option),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
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
            .padding(vertical = 10.dp),
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

private fun offlineStateLabel(state: OfflineDownloadState) = when (state) {
    OfflineDownloadState.NotDownloaded -> "未下载"
    OfflineDownloadState.Waiting -> "等待中"
    OfflineDownloadState.Downloading -> "下载中"
    OfflineDownloadState.Paused -> "已暂停"
    OfflineDownloadState.Installed -> "可离线使用"
    OfflineDownloadState.UpdateAvailable -> "有更新"
    OfflineDownloadState.Failed -> "下载失败"
}