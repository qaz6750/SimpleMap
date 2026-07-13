package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.offline.OfflineCity
import com.simplemap.offline.OfflineDownloadState
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.offline.rememberNetworkAvailable
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(ProfileSection.Favorites) }
    var favorites by remember(favoriteStore) { mutableStateOf(favoriteStore.load()) }
    var settings by remember(settingsStore) { mutableStateOf(settingsStore.load()) }

    DisposableEffect(offlineRepository) {
        onDispose {
            if (destroyOfflineRepositoryOnDispose) offlineRepository?.destroy()
        }
    }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 94.dp)
            .fillMaxWidth(),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 12.dp,
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
                        ) { Text(item.label) }
                    } else {
                        OutlinedButton(
                            onClick = { section = item },
                            modifier = Modifier.weight(1f),
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
                        if (favoriteStore.remove(place.id)) favorites = favorites.filterNot { it.id == place.id }
                    },
                )
                ProfileSection.Offline -> if (offlineRepository != null) {
                    OfflineMapsSection(offlineRepository)
                } else {
                    Text(
                        text = offlineUnavailableMessage ?: "离线地图服务暂不可用",
                        modifier = Modifier.padding(vertical = 30.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ProfileSection.Settings -> SettingsSection(
                    settings = settings,
                    onChanged = {
                        if (settingsStore.save(it)) settings = it
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoritesSection(
    favorites: List<Place>,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
) {
    if (favorites.isEmpty()) {
        Text("暂无收藏地点", modifier = Modifier.padding(vertical = 30.dp), color = Color(0xFF66726F))
        return
    }
    LazyColumn(modifier = Modifier.heightIn(max = 590.dp)) {
        items(favorites, key = Place::id) { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onNavigateTo(place) }
                    .semantics { contentDescription = "规划到 ${place.name}" }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF17211F))
                    Text(place.address.ifBlank { place.district }, color = Color(0xFF68736F), fontSize = 13.sp)
                }
                TextButton(onClick = { onRemove(place) }) { Text("移除") }
            }
            HorizontalDivider(color = Color(0xFFF0F3F1))
        }
    }
}

@Composable
private fun OfflineMapsSection(repository: OfflineMapRepository) {
    val coroutineScope = rememberCoroutineScope()
    val networkAvailable = rememberNetworkAvailable()
    var query by remember { mutableStateOf("") }
    var cities by remember(repository) {
        mutableStateOf(repository.loadCities().getOrDefault(emptyList()))
    }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(repository) {
        repository.setOnChanged { changed ->
            cities = cities.map { if (it.name == changed.name) changed else it }
            if (cities.none { it.name == changed.name }) cities = cities + changed
        }
        onDispose { repository.setOnChanged {} }
    }

    Surface(
        color = if (networkAvailable) Color(0xFFEAF5EF) else Color(0xFFF9EAE7),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (networkAvailable) "网络可用，可下载或更新城市包" else "当前离线，可继续使用已下载城市包",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = if (networkAvailable) Color(0xFF126B56) else Color(0xFFB3473F),
            fontSize = 13.sp,
        )
    }
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
    LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
        items(filteredCities, key = OfflineCity::code) { city ->
            OfflineCityItem(
                city = city,
                networkAvailable = networkAvailable,
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

@Composable
private fun OfflineCityItem(
    city: OfflineCity,
    networkAvailable: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF17211F))
                Text(
                    "%.1f MB · ${offlineStateLabel(city.state)}".format(city.sizeBytes / 1024f / 1024f),
                    color = Color(0xFF68736F),
                    fontSize = 12.sp,
                )
            }
            when (city.state) {
                OfflineDownloadState.Downloading,
                OfflineDownloadState.Waiting,
                -> TextButton(onClick = onPause) { Text("暂停") }
                OfflineDownloadState.Installed -> TextButton(onClick = onRemove) { Text("删除") }
                else -> TextButton(onClick = onDownload, enabled = networkAvailable) { Text("下载") }
            }
        }
        if (city.state == OfflineDownloadState.Downloading || city.state == OfflineDownloadState.Waiting) {
            LinearProgressIndicator(
                progress = { city.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF126B56),
            )
        }
    }
    HorizontalDivider(color = Color(0xFFF0F3F1))
}

@Composable
private fun SettingsSection(
    settings: NavigationSettings,
    onChanged: (NavigationSettings) -> Unit,
) {
    SettingToggle("语音导航", "播报转向、路况与到达提醒", settings.voiceGuidance) {
        onChanged(settings.copy(voiceGuidance = it))
    }
    SettingToggle("实时路况", "在地图和导航路线中显示拥堵", settings.trafficLayer) {
        onChanged(settings.copy(trafficLayer = it))
    }
    SettingToggle("路线状态提醒", "偏航或拥堵重规划时显示提示", settings.routeAlerts) {
        onChanged(settings.copy(routeAlerts = it))
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text("隐私与权限", fontWeight = FontWeight.SemiBold, color = Color(0xFF17211F))
    Text(
        "地图服务仅在你同意隐私说明后初始化；定位权限由系统设置管理。",
        color = Color(0xFF68736F),
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
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
            Text(title, color = Color(0xFF17211F), fontWeight = FontWeight.Medium)
            Text(description, color = Color(0xFF68736F), fontSize = 12.sp)
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