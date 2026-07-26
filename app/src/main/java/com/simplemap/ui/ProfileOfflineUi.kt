package com.simplemap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
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
import com.simplemap.ui.theme.trafficClear
import com.simplemap.ui.theme.trafficJam
import com.simplemap.ui.theme.trafficSlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OfflineMapsSection(
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
internal fun OfflineCityItem(
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
internal fun offlineCityContainerColor(state: OfflineDownloadState) = when (state) {
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
internal fun offlineStateTone(state: OfflineDownloadState) = when (state) {
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

internal fun offlineStateLabel(state: OfflineDownloadState) = when (state) {
    OfflineDownloadState.NotDownloaded -> "未下载"
    OfflineDownloadState.Waiting -> "等待中"
    OfflineDownloadState.Downloading -> "下载中"
    OfflineDownloadState.Paused -> "已暂停"
    OfflineDownloadState.Installed -> "可离线使用"
    OfflineDownloadState.UpdateAvailable -> "有更新"
    OfflineDownloadState.Failed -> "下载失败"
}
